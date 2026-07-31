package com.paypulse.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS — locked to known frontend origins only, never wildcard (*).
 * NFR-14 / MEM-022. Owner: M3.
 */
@Configuration
public class CorsConfig {

    @Value("${paypulse.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Idempotency-Key", "Authorization"));
        config.setExposedHeaders(List.of(
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset",
                "Retry-After", "Location"
        ));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}

