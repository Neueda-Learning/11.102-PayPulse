package com.paypulse.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

@Component
public class RateLimitFilter extends HttpFilter {

    private final ProxyManager<byte[]> proxyManager;
    private final long globalLimitPerMinute;
    private final long perClientLimitPerMinute;

    private static final byte[] GLOBAL_BUCKET_KEY = "rate-limit:global".getBytes(StandardCharsets.UTF_8);

    public RateLimitFilter(
            ProxyManager<byte[]> proxyManager,
            @Value("${paypulse.rate-limit.global-requests-per-minute}") long globalLimitPerMinute,
            @Value("${paypulse.rate-limit.per-client-requests-per-minute}") long perClientLimitPerMinute) {
        this.proxyManager = proxyManager;
        this.globalLimitPerMinute = globalLimitPerMinute;
        this.perClientLimitPerMinute = perClientLimitPerMinute;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        BucketProxy globalBucket = resolveBucket(GLOBAL_BUCKET_KEY, globalLimitPerMinute);
        BucketProxy clientBucket = resolveBucket(clientKey(request), perClientLimitPerMinute);

        var globalProbe = globalBucket.tryConsumeAndReturnRemaining(1);
        if (!globalProbe.isConsumed()) {
            reject(request, response, globalProbe.getNanosToWaitForRefill(), globalLimitPerMinute, 0);
            return;
        }

        var clientProbe = clientBucket.tryConsumeAndReturnRemaining(1);
        if (!clientProbe.isConsumed()) {
            reject(request, response, clientProbe.getNanosToWaitForRefill(), perClientLimitPerMinute, 0);
            return;
        }

        long resetSeconds = 60; // fixed 1-minute window
        response.setHeader("X-RateLimit-Limit", String.valueOf(perClientLimitPerMinute));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(clientProbe.getRemainingTokens()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));

        chain.doFilter(request, response);
    }

    private BucketProxy resolveBucket(byte[] key, long limitPerMinute) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limitPerMinute)
                        .refillGreedy(limitPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();

        RemoteBucketBuilder<byte[]> builder = proxyManager.builder();
        return builder.build(key, configSupplier);
    }

    private byte[] clientKey(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ("rate-limit:client:" + ip).getBytes(StandardCharsets.UTF_8);
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        long nanosToWait,
                        long limit,
                        long remaining) throws IOException {
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(nanosToWait).toSeconds());
        long resetSeconds = retryAfterSeconds;

        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));

        String path = request.getRequestURI();
        String body = "{\"errorCode\":\"RATE_LIMIT_EXCEEDED\","
                + "\"message\":\"Too many requests. Please retry after " + retryAfterSeconds + "s.\","
                + "\"timestamp\":\"" + java.time.Instant.now() + "\","
                + "\"path\":\"" + path + "\"}";
        response.getWriter().write(body);
    }
}