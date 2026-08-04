package com.paypulse.payment.service;

import com.paypulse.common.error.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Domain-friendly API exception carrying the HTTP status and frozen error code.
 */
@Getter
public class PaymentException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    public PaymentException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}

