package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

public class FailedState extends AbstractPaymentState {

    @Override
    public PaymentStatus status() {
        return PaymentStatus.FAILED;
    }
}