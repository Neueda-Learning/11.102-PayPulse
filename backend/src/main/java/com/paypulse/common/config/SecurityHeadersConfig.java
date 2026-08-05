package com.paypulse.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security headers to every API response.
 * NFR-14 / docs/07-TESTING-STRATEGY.md §2.8. Owner: M3.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersConfig extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Prevent MIME-type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Disable legacy XSS filter (recommended for modern browsers)
        response.setHeader("X-XSS-Protection", "0");

        // Force HTTPS (backend behind nginx in Docker)
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // No caching for API responses
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        // API-only CSP — no HTML served by backend
//        response.setHeader("Content-Security-Policy", "default-src 'none'");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;");

        filterChain.doFilter(request, response);
    }
}

