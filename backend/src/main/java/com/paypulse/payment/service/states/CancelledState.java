package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

public class CancelledState extends AbstractPaymentState {
    @Override
    public PaymentStatus status() {
        return PaymentStatus.CANCELLED;
    }
}