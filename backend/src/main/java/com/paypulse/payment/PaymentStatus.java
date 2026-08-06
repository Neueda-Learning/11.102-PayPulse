package com.paypulse.payment;

/**
 * Payment lifecycle states (state machine).
 * Legal transitions documented in docs/04-SRS.md FR-10.
 * Owner: M2
 */
public enum PaymentStatus {
    CREATED,
    VALIDATED,
    SENT,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}

