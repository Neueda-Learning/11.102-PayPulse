package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

public class CreatedState extends AbstractPaymentState {

    @Override
    public PaymentStatus status() {
        return PaymentStatus.CREATED;
    }

    @Override
    public PaymentStatus validate() {
        return PaymentStatus.VALIDATED;
    }
}