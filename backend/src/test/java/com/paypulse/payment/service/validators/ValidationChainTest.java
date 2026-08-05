package com.paypulse.payment.service.validators;

import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ValidationChainTest {

    @Mock
    private CurrencyValidator currencyValidator;

    @Mock
    private AmountValidator amountValidator;

    @Mock
    private AccountValidator accountValidator;

    private ValidationChain chain() {
        return new ValidationChain(currencyValidator, amountValidator, accountValidator);
    }

    @Test
    void validate_runsAllValidatorsInOrder_currencyThenAmountThenAccount() {
        CreatePaymentRequest request = request();

        chain().validate(request);

        InOrder inOrder = inOrder(currencyValidator, amountValidator, accountValidator);
        inOrder.verify(currencyValidator).validate(request);
        inOrder.verify(amountValidator).validate(request);
        inOrder.verify(accountValidator).validate(request);
    }

    @Test
    void validate_whenCurrencyValidatorThrows_shortCircuitsBeforeAccountValidator() {
        doThrow(new PaymentException(org.springframework.http.HttpStatus.BAD_REQUEST,
                com.paypulse.common.error.ErrorCode.INVALID_CURRENCY, "bad currency"))
                .when(currencyValidator).validate(any());

        assertThatThrownBy(() -> chain().validate(request()))
                .isInstanceOf(PaymentException.class);

        verify(amountValidator, org.mockito.Mockito.never()).validate(any());
        verify(accountValidator, org.mockito.Mockito.never()).validate(any());
    }

    @Test
    void validate_whenAllPass_doesNotThrow() {
        assertThatCode(() -> chain().validate(request())).doesNotThrowAnyException();
    }

    private CreatePaymentRequest request() {
        return CreatePaymentRequest.builder()
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .destinationAccount("ACC2000002")
                .build();
    }
}