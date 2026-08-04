package com.paypulse.payment.service.validators;

import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.service.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates that the source account exists, is ACTIVE, and currency matches.
 */
@Component
@RequiredArgsConstructor
public class AccountValidator implements PaymentValidator {

    private final AccountRepository accountRepository;

    @Override
    public void validate(CreatePaymentRequest request) {
        Account account = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new PaymentException(HttpStatus.NOT_FOUND, ErrorCode.ACCOUNT_NOT_FOUND,
                        "No account found with id " + request.getSourceAccountId()));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ACCOUNT,
                    "Source account is not ACTIVE");
        }

        if (request.getCurrency() != null
                && !account.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CURRENCY,
                    "Payment currency '" + request.getCurrency()
                            + "' does not match account currency '" + account.getCurrency() + "'");
        }

        if (request.getDestinationAccount() != null
                && account.getAccountNumber() != null
                && request.getDestinationAccount().equalsIgnoreCase(account.getAccountNumber())) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ACCOUNT,
                    "destinationAccount must differ from source account number");
        }
    }
}

