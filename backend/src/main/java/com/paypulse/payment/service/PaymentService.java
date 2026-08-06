package com.paypulse.payment.service;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.common.idempotency.IdempotencyService;
import com.paypulse.fx.dto.FxRateResponse;
import com.paypulse.fx.service.FxRateService;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.api.PaymentMapper;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.service.validators.ValidationChain;
import com.paypulse.payment.service.states.InvalidStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import java.util.UUID;

/**
 * Orchestrates POST /payments: idempotency -> validation chain -> persistence
 * -> automatic lifecycle progression (FR-2.4, Feature #4, owned by M2's
 * StatusTransitionEngine).
 *
 * NOTE (Q15/MEM-029): createPayment() is deliberately NOT @Transactional at
 * this top level. saveNewPayment() and recordCreation() each commit in their
 * own transaction (Spring Data repositories / StatusTransitionEngine methods
 * are independently @Transactional), so the CREATED row is durably visible
 * to a concurrent POST /payments/{id}/cancel BEFORE runAutomaticLifecycle()
 * races it forward. paypulse.processing.simulated-delay-ms controls the
 * size of that real cancel window (0 = no delay, tests default to this).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentMapper paymentMapper;
    private final ValidationChain validationChain;
    private final StatusTransitionEngine statusTransitionEngine;
    private final Random simulationRandom;
    private final FxRateService fxRateService;

    @Value("${paypulse.simulation.create-failure-account:FAILCREATE01}")
    private String createFailureAccount;

    @Value("${paypulse.simulation.random-failure-rate:0.05}")
    private double randomFailureRate;

    @Value("${paypulse.processing.simulated-delay-ms:0}")
    private long simulatedDelayMs;

    public PaymentCreationResult createPayment(String idempotencyKey, CreatePaymentRequest request) {
        return idempotencyService.findExisting(idempotencyKey)
                .map(existing -> new PaymentCreationResult(paymentMapper.toResponse(existing), false))
                .orElseGet(() -> new PaymentCreationResult(
                        paymentMapper.toResponse(createAndProgressPayment(idempotencyKey, request)), true));
    }

    private Payment createAndProgressPayment(String idempotencyKey, CreatePaymentRequest request) {
        validationChain.validate(request);

        // Phase 1 — commits immediately (own transaction): the CREATED row
        // and its audit history entry are now durably visible to any other
        // request, including a concurrent /cancel call.
        Payment created = saveNewPayment(idempotencyKey, request);
        statusTransitionEngine.recordCreation(created, TriggeredBy.CLIENT);

        // UI-driven CREATE failure should still produce a persisted payment,
        // history rows, events, and downstream analytics/notification updates.
        if (isForcedCreateFailure(request)) {
            return statusTransitionEngine.markFailed(
                    created,
                    TriggeredBy.SYSTEM,
                    ErrorCode.PROCESSING_ERROR.name(),
                    "Simulated failure during CREATE (UI-selected forced failure)"
            );
        }

        awaitCancelWindow();

        // Re-fetch: a concurrent cancel may have already moved this payment
        // off CREATED during the window above. If so, respect that outcome
        // instead of racing the automatic lifecycle over it.
        Payment current = paymentRepository.findById(created.getId()).orElse(created);
        // forcedFailureStage is transient; carry it forward after re-fetch so
        // VALIDATE/SEND simulation can still trigger during auto-lifecycle.
        current.setForcedFailureStage(created.getForcedFailureStage());
        if (current.getStatus() != PaymentStatus.CREATED) {
            return current;
        }

        // Phase 2 — separate transaction (runAutomaticLifecycle is itself
        // @Transactional on StatusTransitionEngine).
        return statusTransitionEngine.runAutomaticLifecycle(current);
    }

    private void awaitCancelWindow() {
        if (simulatedDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(simulatedDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Payment saveNewPayment(String idempotencyKey, CreatePaymentRequest request) {
        simulateCreationFailure(request);

        Payment payment = paymentMapper.toEntity(request);
        FxRateResponse fxQuote = fxRateService.getRate(request.getCurrency(), request.getTargetCurrency());
        payment.setId(UUID.randomUUID().toString());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
        payment.setErrorCode(null);
        payment.setErrorMessage(null);
        payment.setForcedFailureStage(request.getForceFailureStage());
        payment.setTargetCurrency(fxQuote.getTo());
        payment.setFxRate(fxQuote.getRate());
        payment.setConvertedAmount(calculateConvertedAmount(request.getAmount(), fxQuote.getRate()));

        return paymentRepository.save(payment);
    }

    private BigDecimal calculateConvertedAmount(BigDecimal amount, BigDecimal fxRate) {
        if (amount == null || fxRate == null) {
            return amount;
        }
        return amount.multiply(fxRate).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        return (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();
    }

    private void simulateCreationFailure(CreatePaymentRequest request) {
        if (createFailureAccount != null && createFailureAccount.equals(request.getDestinationAccount())) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PROCESSING_ERROR,
                    "Simulated failure while creating payment (deterministic trigger)");
        }
        if (randomFailureRate > 0.0d && simulationRandom.nextDouble() < randomFailureRate) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.PROCESSING_ERROR,
                    "Simulated transient failure while creating payment");
        }
    }

    private boolean isForcedCreateFailure(CreatePaymentRequest request) {
        return "CREATE".equalsIgnoreCase(request.getForceFailureStage());
    }

    @Transactional
    public Payment cancelPayment(String id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                        "No payment found with id " + id));
        try {
            return statusTransitionEngine.cancelPayment(payment, TriggeredBy.CLIENT);
        } catch (InvalidStatusTransitionException ex) {
            throw new PaymentException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_NOT_CANCELLABLE,
                    "Payment cannot be cancelled from its current status");
        }
    }

    @Transactional
    public Payment createReversalPayment(CreatePaymentRequest request, String originalPaymentId) {
        Payment created = saveNewPayment(null, request);
        created.setReversalOfPaymentId(originalPaymentId);
        created = paymentRepository.save(created);
        statusTransitionEngine.recordCreation(created, TriggeredBy.CLIENT);
        return statusTransitionEngine.runAutomaticLifecycle(created);
    }
}