package com.paypulse.payment.service.validators;

import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static com.paypulse.common.error.ErrorCode.INVALID_CURRENCY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ValidationChainTest {

	@Mock
	private CurrencyValidator currencyValidator;

	@Mock
	private AmountValidator amountValidator;

	@Mock
	private AccountValidator accountValidator;

	@InjectMocks
	private ValidationChain validationChain;

	@Test
	void validate_runs_in_expected_order() {
		CreatePaymentRequest request = request();

		validationChain.validate(request);

		InOrder order = inOrder(currencyValidator, amountValidator, accountValidator);
		order.verify(currencyValidator).validate(request);
		order.verify(amountValidator).validate(request);
		order.verify(accountValidator).validate(request);
	}

	@Test
	void validate_stops_on_first_failure() {
		CreatePaymentRequest request = request();
		doThrow(new PaymentException(HttpStatus.BAD_REQUEST, INVALID_CURRENCY, "bad currency"))
				.when(currencyValidator).validate(request);

		assertThatThrownBy(() -> validationChain.validate(request))
				.isInstanceOf(PaymentException.class)
				.hasMessageContaining("bad currency");

		verifyNoInteractions(amountValidator, accountValidator);
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


