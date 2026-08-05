package com.paypulse.payment.service.validators;

import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class AmountValidatorTest {

    private final AmountValidator validator = new AmountValidator();

    @Test
    void valid_amount_doesNotThrow() {
        assertThatCode(() -> validator.validate(request("INR", new BigDecimal("250.00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void null_amount_throws() {
        assertThatThrownBy(() -> validator.validate(request("INR", null)))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void zero_amount_throws() {
        assertThatThrownBy(() -> validator.validate(request("INR", BigDecimal.ZERO)))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("0.01");
    }

    @Test
    void amount_above_max_throws() {
        assertThatThrownBy(() -> validator.validate(request("INR", new BigDecimal("1000001"))))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("1000000");
    }

    @Test
    void more_than_two_decimal_places_throws() {
        assertThatThrownBy(() -> validator.validate(request("INR", new BigDecimal("1.234"))))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("2 decimal places");
    }

    @Test
    void exact_min_doesNotThrow() {
        assertThatCode(() -> validator.validate(request("INR", new BigDecimal("0.01"))))
                .doesNotThrowAnyException();
    }

    @Test
    void exact_max_doesNotThrow() {
        assertThatCode(() -> validator.validate(request("INR", new BigDecimal("1000000"))))
                .doesNotThrowAnyException();
    }

    private CreatePaymentRequest request(String currency, BigDecimal amount) {
        return CreatePaymentRequest.builder()
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .currency(currency)
                .amount(amount)
                .destinationAccount("ACC2000002")
                .build();
    }
}

