package com.paypulse.payment.service.validators;

import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyValidatorTest {

    private final CurrencyValidator validator = new CurrencyValidator();

    @Test
    void inr_doesNotThrow() {
        assertThatCode(() -> validator.validate(request("INR"))).doesNotThrowAnyException();
    }

    @Test
    void usd_doesNotThrow() {
        assertThatCode(() -> validator.validate(request("USD"))).doesNotThrowAnyException();
    }

    @Test
    void null_currency_throws() {
        assertThatThrownBy(() -> validator.validate(request(null)))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void blank_currency_throws() {
        assertThatThrownBy(() -> validator.validate(request("   ")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void unsupported_currency_throws() {
        assertThatThrownBy(() -> validator.validate(request("EUR")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("INR or USD");
    }

    @Test
    void lowercase_inr_throws() {
        // currency field is case-sensitive per spec — "inr" is invalid
        assertThatThrownBy(() -> validator.validate(request("inr")))
                .isInstanceOf(PaymentException.class);
    }

    private CreatePaymentRequest request(String currency) {
        return CreatePaymentRequest.builder()
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .currency(currency)
                .amount(new BigDecimal("100.00"))
                .destinationAccount("ACC2000002")
                .build();
    }
}

