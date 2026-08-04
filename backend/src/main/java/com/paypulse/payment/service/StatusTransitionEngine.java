package com.paypulse.payment.service;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.common.resilience.ResilienceConfig;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.PaymentStatusHistory;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.repository.PaymentStatusHistoryRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import com.paypulse.payment.service.states.PaymentState;
import com.paypulse.payment.service.states.PaymentStateFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatusTransitionEngine {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final PaymentStateFactory stateFactory;
    private final ResilienceConfig resilienceConfig;
    private final Random simulationRandom;

    private final ApplicationEventPublisher eventPublisher;
    @Value("${paypulse.simulation.failure-account-number:FAILTEST01}")
    private String deterministicFailureAccount;

    @Value("${paypulse.simulation.random-failure-rate:0.05}")
    private double randomFailureRate;

    @Transactional(readOnly = true)
    public List<PaymentStatusHistory> getHistory(String paymentId) {
        return historyRepository.findByPaymentIdOrderByOccurredAtAsc(paymentId);
    }

    @Transactional
    public Payment validatePayment(Payment payment, TriggeredBy triggeredBy) {
        return transition(payment, Action.VALIDATE, triggeredBy, null, null);
    }

    @Transactional
    public Payment sendPayment(Payment payment, TriggeredBy triggeredBy) {
        return transitionWithResilience(payment, Action.SEND, "paymentSend", triggeredBy);
    }

    @Transactional
    public Payment completePayment(Payment payment, TriggeredBy triggeredBy) {
        return transitionWithResilience(payment, Action.COMPLETE, "paymentComplete", triggeredBy);
    }

    @Transactional
    public Payment markFailed(Payment payment, TriggeredBy triggeredBy, String errorCode, String errorMessage) {
        PaymentStatus previous = payment.getStatus();
        return persistTransition(payment, previous, PaymentStatus.FAILED, triggeredBy, errorCode, errorMessage);
    }
    /**
     * Writes the initial null -> CREATED audit row. Payment is assumed to
     * already be persisted with status CREATED by the caller (PaymentService) —
     * this only records the audit trail entry, no state transition happens here.
     */
    @Transactional
    public void recordCreation(Payment payment, TriggeredBy triggeredBy) {
        Instant occurredAt = Instant.now();
        saveHistory(payment.getId(), null, PaymentStatus.CREATED, null, null, triggeredBy, occurredAt);
        eventPublisher.publishEvent(new PaymentStatusChangedEvent(
                payment.getId(), null, PaymentStatus.CREATED, null, null, triggeredBy, occurredAt));
    }

    /**
     * Feature #4 — Automatic Payment Lifecycle Processing (FR-2.4).
     * Runs validate -> send -> complete with TriggeredBy.SYSTEM, short-circuiting
     * as soon as any step leaves the payment in a non-progressing state (FAILED).
     * Returns the final Payment reflecting wherever the lifecycle stopped.
     */
    @Transactional
    public Payment runAutomaticLifecycle(Payment payment) {
        Payment current = validatePayment(payment, TriggeredBy.SYSTEM);
        if (current.getStatus() != PaymentStatus.VALIDATED) {
            return current;
        }

        current = sendPayment(current, TriggeredBy.SYSTEM);
        if (current.getStatus() != PaymentStatus.SENT) {
            return current;
        }

        return completePayment(current, TriggeredBy.SYSTEM);
    }
    private Payment transitionWithResilience(
            Payment payment,
            Action action,
            String resilienceInstance,
            TriggeredBy triggeredBy
    ) {
        PaymentStatus previous = payment.getStatus();
        PaymentState state = stateFactory.from(previous);

        PaymentStatus next = switch (action) {
            case SEND -> state.send();
            case COMPLETE -> state.complete();
            default -> throw new IllegalArgumentException("Unsupported resilience transition action: " + action);
        };

        try {
            resilienceConfig.execute(resilienceInstance, () -> {
                simulateExternalStep(payment);
                return Boolean.TRUE;
            });
            return persistTransition(payment, previous, next, triggeredBy, null, null);
        } catch (CallNotPermittedException ex) {
            return persistTransition(
                    payment,
                    previous,
                    PaymentStatus.FAILED,
                    triggeredBy,
                    ErrorCode.PROCESSING_ERROR.name(),
                    "Circuit breaker is open for transition " + action
            );
        } catch (RuntimeException ex) {
            return persistTransition(
                    payment,
                    previous,
                    PaymentStatus.FAILED,
                    triggeredBy,
                    ErrorCode.NETWORK_ERROR.name(),
                    ex.getMessage()
            );
        }
    }

    private Payment transition(
            Payment payment,
            Action action,
            TriggeredBy triggeredBy,
            String errorCode,
            String errorMessage
    ) {
        PaymentStatus previous = payment.getStatus();

        PaymentState state = stateFactory.from(previous);
        PaymentStatus next = switch (action) {
            case VALIDATE -> state.validate();
            case SEND -> state.send();
            case COMPLETE -> state.complete();
        };

        return persistTransition(payment, previous, next, triggeredBy, errorCode, errorMessage);
    }

    private Payment persistTransition(
            Payment payment,
            PaymentStatus previous,
            PaymentStatus next,
            TriggeredBy triggeredBy,
            String errorCode,
            String errorMessage
    ) {
        payment.setStatus(next);
        payment.setErrorCode(errorCode);
        payment.setErrorMessage(errorMessage);

        Payment saved = savePayment(payment);
        Instant occurredAt = Instant.now();
        saveHistory(saved.getId(), previous, next, errorCode, errorMessage, triggeredBy, occurredAt);
        eventPublisher.publishEvent(new PaymentStatusChangedEvent(
                saved.getId(), previous, next, errorCode, errorMessage, triggeredBy, occurredAt));
        return saved;
    }

    private Payment savePayment(Payment payment) {
        try {
            return paymentRepository.save(payment);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new IllegalStateException("Payment was concurrently modified, please retry", ex);
        }
    }

    private void saveHistory(
            String paymentId,
            PaymentStatus previous,
            PaymentStatus next,
            String errorCode,
            String errorMessage,
            TriggeredBy triggeredBy,
            Instant occurredAt
    ) {
        PaymentStatusHistory row = PaymentStatusHistory.builder()
                .paymentId(paymentId)
                .previousStatus(previous)
                .newStatus(next)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .triggeredBy(triggeredBy)
                .occurredAt(occurredAt)
                .build();

        historyRepository.save(row);
    }

    private enum Action {
        VALIDATE, SEND, COMPLETE
    }

    private void simulateExternalStep(Payment payment) {
        if (deterministicFailureAccount != null && deterministicFailureAccount.equals(payment.getDestinationAccount())) {
            throw new SimulatedProcessingException("Simulated network failure while transmitting payment");
        }
        if (randomFailureRate > 0.0d && simulationRandom.nextDouble() < randomFailureRate) {
            throw new SimulatedProcessingException("Simulated transient network failure");
        }
    }
}