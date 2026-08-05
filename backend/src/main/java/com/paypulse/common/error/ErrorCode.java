package com.paypulse.common.error;

/**
 * All error codes used by this API.
 * Shape of every error response: { errorCode, message, timestamp, path }
 * See docs/11-API-DESIGN.md §12 for HTTP status mapping.
 * Owner: M3
 */
public enum ErrorCode {
    // Validation
    VALIDATION_FAILED,
    INVALID_ACCOUNT,
    INVALID_CURRENCY,
    INVALID_AMOUNT,

    // Not found
    PAYMENT_NOT_FOUND,
    ACCOUNT_NOT_FOUND,

    // Business
    DUPLICATE_PAYMENT,
    INVALID_STATUS_TRANSITION,
    INSUFFICIENT_FUNDS,
    PAYMENT_NOT_CANCELLABLE,
    PAYMENT_ALREADY_REVERSED,

    // System / simulation
    PROCESSING_ERROR,
    NETWORK_ERROR,

    // Rate limiting (NFR-11)
    RATE_LIMIT_EXCEEDED
}

