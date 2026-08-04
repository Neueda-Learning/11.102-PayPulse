package com.paypulse.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilienceConfigTest {

    @Test
    void execute_appliesRetryAndOpensCircuitBreakerAfterFailures() {
        CircuitBreakerConfig breakerConfig = CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(2)
                .slidingWindowSize(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();

        CircuitBreakerRegistry breakerRegistry = CircuitBreakerRegistry.of(breakerConfig);
        breakerRegistry.circuitBreaker("paymentSend");

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retryRegistry.retry("paymentSend");

        ResilienceConfig resilienceConfig = new ResilienceConfig(breakerRegistry, retryRegistry);

        AtomicInteger attempts = new AtomicInteger(0);
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> resilienceConfig.execute("paymentSend", () -> {
                attempts.incrementAndGet();
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class);
        }

        assertThat(attempts.get()).isEqualTo(4); // 2 calls x 2 retry attempts

        CircuitBreaker breaker = breakerRegistry.circuitBreaker("paymentSend");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void execute_allowsRecovery_andClosesCircuitBreakerAfterSuccessfulHalfOpenCalls() throws InterruptedException {
        CircuitBreakerConfig breakerConfig = CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(2)
                .slidingWindowSize(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        CircuitBreakerRegistry breakerRegistry = CircuitBreakerRegistry.of(breakerConfig);
        breakerRegistry.circuitBreaker("paymentComplete");

        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(1)
                .waitDuration(Duration.ZERO)
                .build());
        retryRegistry.retry("paymentComplete");

        ResilienceConfig resilienceConfig = new ResilienceConfig(breakerRegistry, retryRegistry);

        // Open breaker via two failed protected calls.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> resilienceConfig.execute("paymentComplete", () -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class);
        }

        CircuitBreaker breaker = breakerRegistry.circuitBreaker("paymentComplete");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(40L);

        assertThat(resilienceConfig.execute("paymentComplete", () -> "ok-1")).isEqualTo("ok-1");
        assertThat(resilienceConfig.execute("paymentComplete", () -> "ok-2")).isEqualTo("ok-2");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}

