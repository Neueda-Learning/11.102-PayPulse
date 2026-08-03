package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(PaymentStatus from, String action) {
        super("Cannot apply action '" + action + "' from status " + from);
    }

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}