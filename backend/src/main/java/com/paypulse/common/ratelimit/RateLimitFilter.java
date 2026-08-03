package com.paypulse.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypulse.common.error.ApiError;
import com.paypulse.common.error.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiter (Bucket4j) — runs as the first servlet filter
 * before any controller/service/DB layer is touched (cheapest rejection point).
 *
 * Two tiers:
 *  1. Global bucket  — system-wide ceiling (~40,000 req/min, NFR-10/11)
 *  2. Per-IP bucket  — fair-share per client (~2,000 req/min default)
 *
 * Breach → 429 + RATE_LIMIT_EXCEEDED + Retry-After + X-RateLimit-* headers.
 * Owner: M2 (see docs/13-WORK-DISTRIBUTION.md §2).
 */
@Component
@Slf4j
public class RateLimitFilter implements Filter {

    private final Bucket globalBucket;
    private final Map<String, Bucket> perClientBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Value("${paypulse.rate-limit.global-requests-per-minute:40000}")
    private long globalRequestsPerMinute;

    @Value("${paypulse.rate-limit.per-client-requests-per-minute:2000}")
    private long perClientRequestsPerMinute;

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${paypulse.rate-limit.global-requests-per-minute:40000}") long globalRpm,
                           @Value("${paypulse.rate-limit.per-client-requests-per-minute:2000}") long perClientRpm) {
        this.objectMapper = objectMapper;
        this.globalBucket = buildBucket(globalRpm);
        this.globalRequestsPerMinute = globalRpm;
        this.perClientRequestsPerMinute = perClientRpm;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq  = (HttpServletRequest)  request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // Only rate-limit /api/** paths
        String path = httpReq.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Global bucket check
        if (!globalBucket.tryConsume(1)) {
            sendRateLimitResponse(httpReq, httpResp, globalRequestsPerMinute);
            return;
        }

        // 2. Per-client bucket check
        String clientIp = getClientIp(httpReq);
        Bucket clientBucket = perClientBuckets.computeIfAbsent(
                clientIp, ip -> buildBucket(perClientRequestsPerMinute));

        if (!clientBucket.tryConsume(1)) {
            sendRateLimitResponse(httpReq, httpResp, perClientRequestsPerMinute);
            return;
        }

        // Add informational headers to every passing response
        long remaining = Math.min(
                globalBucket.getAvailableTokens(),
                clientBucket.getAvailableTokens());
        httpResp.setHeader("X-RateLimit-Limit",     String.valueOf(perClientRequestsPerMinute));
        httpResp.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        httpResp.setHeader("X-RateLimit-Reset",     "60");

        chain.doFilter(request, response);
    }

    private void sendRateLimitResponse(HttpServletRequest req,
                                        HttpServletResponse resp,
                                        long limitRpm) throws IOException {
        log.warn("Rate limit exceeded for IP {} on {}", getClientIp(req), req.getRequestURI());
        resp.setStatus(429);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setHeader("Retry-After", "60");
        resp.setHeader("X-RateLimit-Limit",     String.valueOf(limitRpm));
        resp.setHeader("X-RateLimit-Remaining", "0");
        resp.setHeader("X-RateLimit-Reset",     "60");
        ApiError error = ApiError.of(ErrorCode.RATE_LIMIT_EXCEEDED,
                "Too many requests. Please slow down and retry after 60 seconds.",
                req.getRequestURI());
        resp.getWriter().write(objectMapper.writeValueAsString(error));
    }

    private Bucket buildBucket(long requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

