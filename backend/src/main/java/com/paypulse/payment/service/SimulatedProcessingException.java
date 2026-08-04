package com.paypulse.payment.service;

/** * Thrown by StatusTransitionEngine's simulated external steps (send/complete) * to represent a transient, retryable simulated failure. * Referenced explicitly in application.yml resilience4j.retry.instances.*.retry-exceptions * so Resilience4j's Retry actually triggers for these simulated failures. */
public class SimulatedProcessingException extends RuntimeException {
    public SimulatedProcessingException(String message) {
        super(message);
    }
}