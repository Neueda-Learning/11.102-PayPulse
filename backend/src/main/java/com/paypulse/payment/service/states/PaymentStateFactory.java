package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentStateFactory {

    public PaymentState from(PaymentStatus status) {
        if (status == null) {
            throw new InvalidStatusTransitionException("Payment status is null");
        }

        return switch (status) {
            case CREATED -> new CreatedState();
            case VALIDATED -> new ValidatedState();
            case SENT -> new SentState();
            case COMPLETED -> new CompletedState();
            case FAILED -> new FailedState();
            case CANCELLED -> new CancelledState();
        };
    }
}