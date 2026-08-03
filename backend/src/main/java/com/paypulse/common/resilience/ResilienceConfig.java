package com.paypulse.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Central helper for applying retry + circuit breaker policies by instance name.
 */
@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public <T> T execute(String instanceName, Supplier<T> supplier) {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);

        Supplier<T> retried = Retry.decorateSupplier(retry, supplier);
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(breaker, retried);
        return guarded.get();
    }

    @Bean
    public Random simulationRandom() {
        return new Random();
    }
}

