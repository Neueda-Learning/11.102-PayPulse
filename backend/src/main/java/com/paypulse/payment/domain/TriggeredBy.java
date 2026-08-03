package com.paypulse.payment.domain;

/**
 * Who initiated the state transition.
 * CLIENT = explicit endpoint call (/validate, /send, /complete)
 * SYSTEM = automatic lifecycle progression.
 */
public enum TriggeredBy {
    CLIENT,
    SYSTEM
}