package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

public class CompletedState extends AbstractPaymentState {

    @Override
    public PaymentStatus status() {
        return PaymentStatus.COMPLETED;
    }
}