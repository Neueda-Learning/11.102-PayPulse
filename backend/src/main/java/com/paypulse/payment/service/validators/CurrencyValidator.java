package com.paypulse.payment.service.validators;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.service.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates that currency is INR or USD (matches openapi.yaml CurrencyCode enum).
 */
@Component
public class CurrencyValidator implements PaymentValidator {

    @Override
    public void validate(CreatePaymentRequest request) {
        validateCurrency("currency", request.getCurrency());
        validateCurrency("targetCurrency", request.getTargetCurrency());
    }

    private void validateCurrency(String fieldName, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CURRENCY, fieldName + " is required");
        }
        if (!currency.equals("INR") && !currency.equals("USD")) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CURRENCY,
                    fieldName + " must be INR or USD, got: " + currency);
        }
    }
}

