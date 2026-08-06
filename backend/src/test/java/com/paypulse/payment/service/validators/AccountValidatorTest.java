package com.paypulse.payment.service.validators;

import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountValidatorTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountValidator validator;

    @Test
    void active_account_with_different_destination_is_valid() {
        when(accountRepository.findById("b2c3d4e5-1111-4a11-8a11-111111111111"))
                .thenReturn(Optional.of(account("ACC1000001", AccountStatus.ACTIVE, "INR")));

        assertThatCode(() -> validator.validate(request("ACC2000002", "INR")))
                .doesNotThrowAnyException();
    }

    @Test
    void missing_account_throws_account_not_found() {
        when(accountRepository.findById("b2c3d4e5-1111-4a11-8a11-111111111111"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(request("ACC2000002", "INR")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("No account found");
    }

    @Test
    void inactive_account_throws_invalid_account() {
        when(accountRepository.findById("b2c3d4e5-1111-4a11-8a11-111111111111"))
                .thenReturn(Optional.of(account("ACC1000001", AccountStatus.INACTIVE, "INR")));

        assertThatThrownBy(() -> validator.validate(request("ACC2000002", "INR")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void same_destination_as_source_account_number_throws_invalid_account() {
        when(accountRepository.findById("b2c3d4e5-1111-4a11-8a11-111111111111"))
                .thenReturn(Optional.of(account("ACC1000001", AccountStatus.ACTIVE, "INR")));

        assertThatThrownBy(() -> validator.validate(request("ACC1000001", "INR")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("differ from source account number");
    }

    @Test
    void currency_mismatch_throws_invalid_currency() {
        when(accountRepository.findById("b2c3d4e5-1111-4a11-8a11-111111111111"))
                .thenReturn(Optional.of(account("ACC1000001", AccountStatus.ACTIVE, "INR")));

        assertThatThrownBy(() -> validator.validate(request("ACC2000002", "USD")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("does not match account currency");
    }

    private CreatePaymentRequest request(String destination, String currency) {
        return CreatePaymentRequest.builder()
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .amount(new BigDecimal("100.00"))
                .currency(currency)
                .targetCurrency(currency)
                .destinationAccount(destination)
                .build();
    }

    private Account account(String accountNumber, AccountStatus status, String currency) {
        return Account.builder()
                .id("b2c3d4e5-1111-4a11-8a11-111111111111")
                .accountNumber(accountNumber)
                .currency(currency)
                .status(status)
                .label("Test")
                .build();
    }
}

