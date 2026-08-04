package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

public class SentState extends AbstractPaymentState {

    @Override
    public PaymentStatus status() {
        return PaymentStatus.SENT;
    }

    @Override
    public PaymentStatus complete() {
        return PaymentStatus.COMPLETED;
    }
}