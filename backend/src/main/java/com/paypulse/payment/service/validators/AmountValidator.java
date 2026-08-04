package com.paypulse.payment.service.validators;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.service.PaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validates amount business rules from docs/11-API-DESIGN.md.
 */
@Component
public class AmountValidator implements PaymentValidator {

	private static final BigDecimal MIN = new BigDecimal("0.01");
	private static final BigDecimal MAX = new BigDecimal("1000000");

	@Override
	public void validate(CreatePaymentRequest request) {
		BigDecimal amount = request.getAmount();
		if (amount == null) {
			throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AMOUNT, "amount is required");
		}
		if (amount.compareTo(MIN) < 0) {
			throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AMOUNT, "amount must be >= 0.01");
		}
		if (amount.compareTo(MAX) > 0) {
			throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AMOUNT, "amount must be <= 1000000");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AMOUNT, "amount must have at most 2 decimal places");
		}
	}
}

