package com.paypulse.payment.service.validators;

import com.paypulse.payment.api.dto.CreatePaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs all registered validators in order.
 * Order: CurrencyValidator → AmountValidator → AccountValidator (account last — DB call).
 */
@Component
@RequiredArgsConstructor
public class ValidationChain {

    private final CurrencyValidator currencyValidator;
    private final AmountValidator amountValidator;
    private final AccountValidator accountValidator;

    public void validate(CreatePaymentRequest request) {
        List<PaymentValidator> validators = List.of(
                currencyValidator,
                amountValidator,
                accountValidator
        );
        for (PaymentValidator validator : validators) {
            validator.validate(request);
        }
    }
}

