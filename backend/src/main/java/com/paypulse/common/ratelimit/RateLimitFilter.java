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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Distributed token-bucket rate limiter (FR-11, NFR-11).
 * One global system-wide bucket + one per-client-IP bucket, both backed by
 * Redis via Bucket4j's ProxyManager — state is shared across all app instances.
 * Owner: M1.
 */
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
            reject(response, globalProbe.getNanosToWaitForRefill(), globalLimitPerMinute, 0);
            return;
        }

        var clientProbe = clientBucket.tryConsumeAndReturnRemaining(1);
        if (!clientProbe.isConsumed()) {
            reject(response, clientProbe.getNanosToWaitForRefill(), perClientLimitPerMinute, 0);
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(perClientLimitPerMinute));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(clientProbe.getRemainingTokens()));
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
        }
        return ("rate-limit:client:" + ip).getBytes(StandardCharsets.UTF_8);
    }

    private void reject(HttpServletResponse response, long nanosToWait, long limit, long remaining) throws IOException {
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(nanosToWait).toSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"errorCode\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Please retry after "
                        + retryAfterSeconds + "s.\",\"timestamp\":\"" + java.time.Instant.now() + "\"}"
        );
    }
}

