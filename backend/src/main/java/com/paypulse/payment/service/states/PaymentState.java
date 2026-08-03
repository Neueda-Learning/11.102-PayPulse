package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

/**
 * State contract for payment lifecycle transitions.
 */
public interface PaymentState {

    PaymentStatus status();

    PaymentStatus validate();

    PaymentStatus send();

    PaymentStatus complete();
}