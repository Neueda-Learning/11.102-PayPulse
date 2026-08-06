package com.paypulse;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Global test configuration to provide mock beans for external infrastructure
 * (like Redis) so the Spring Boot ApplicationContext can start successfully
 * without requiring Docker containers running locally.
 */
@Configuration
public class TestMockConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public ProxyManager<byte[]> mockProxyManager() {
        // Provides a dummy ProxyManager to satisfy the RateLimitFilter dependency
        return Mockito.mock(ProxyManager.class);
    }
}