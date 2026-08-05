package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

public abstract class AbstractPaymentState implements PaymentState {

    @Override
    public PaymentStatus validate() {
        throw new InvalidStatusTransitionException(status(), "validate");
    }

    @Override
    public PaymentStatus send() {
        throw new InvalidStatusTransitionException(status(), "send");
    }

    @Override
    public PaymentStatus complete() {
        throw new InvalidStatusTransitionException(status(), "complete");
    }

    @Override
    public PaymentStatus cancel() {
        throw new InvalidStatusTransitionException(status(), "cancel");
    }
}