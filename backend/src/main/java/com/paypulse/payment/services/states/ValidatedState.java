package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

public class ValidatedState extends AbstractPaymentState {

    @Override
    public PaymentStatus status() {
        return PaymentStatus.VALIDATED;
    }

    @Override
    public PaymentStatus send() {
        return PaymentStatus.SENT;
    }
}