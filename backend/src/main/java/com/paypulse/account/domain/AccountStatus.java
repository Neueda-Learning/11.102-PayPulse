package com.paypulse.account.domain;

/**
 * Account status — a simple flag, NOT a state machine.
 * See docs/10-UML-STATE-DIAGRAM.md §5 for rationale.
 * Owner: M1
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE
}

