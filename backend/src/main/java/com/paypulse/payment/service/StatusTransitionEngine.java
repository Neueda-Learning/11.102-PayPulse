package com.paypulse.payment.service;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.domain.PaymentStatusHistory;
import com.paypulse.payment.domain.TriggeredBy;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.repository.PaymentStatusHistoryRepository;
import com.paypulse.payment.service.states.InvalidStatusTransitionException;
import com.paypulse.payment.service.states.PaymentState;
import com.paypulse.payment.service.states.PaymentStateFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatusTransitionEngine {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final PaymentStateFactory stateFactory;

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
        return transition(payment, Action.SEND, triggeredBy, null, null);
    }

    @Transactional
    public Payment completePayment(Payment payment, TriggeredBy triggeredBy) {
        return transition(payment, Action.COMPLETE, triggeredBy, null, null);
    }

    @Transactional
    public Payment markFailed(Payment payment, TriggeredBy triggeredBy, String errorCode, String errorMessage) {
        PaymentStatus previous = payment.getStatus();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorCode(errorCode);
        payment.setErrorMessage(errorMessage);

        Payment saved = savePayment(payment);
        saveHistory(saved.getId(), previous, PaymentStatus.FAILED, errorCode, errorMessage, triggeredBy);
        return saved;
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

        payment.setStatus(next);
        payment.setErrorCode(errorCode);
        payment.setErrorMessage(errorMessage);

        Payment saved = savePayment(payment);
        saveHistory(saved.getId(), previous, next, errorCode, errorMessage, triggeredBy);
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
            TriggeredBy triggeredBy
    ) {
        PaymentStatusHistory row = PaymentStatusHistory.builder()
                .id(UUID.randomUUID().toString())
                .paymentId(paymentId)
                .previousStatus(previous)
                .newStatus(next)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .triggeredBy(triggeredBy)
                .occurredAt(Instant.now())
                .build();

        historyRepository.save(row);
    }

    private enum Action {
        VALIDATE, SEND, COMPLETE
    }
}