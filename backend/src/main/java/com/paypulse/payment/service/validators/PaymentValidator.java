package com.paypulse.payment.service.validators;

import com.paypulse.payment.api.dto.CreatePaymentRequest;

/**
 * Single-responsibility validator for one aspect of a CreatePaymentRequest.
 * Implementations throw PaymentException on violation.
 */
public interface PaymentValidator {
    void validate(CreatePaymentRequest request);
}

