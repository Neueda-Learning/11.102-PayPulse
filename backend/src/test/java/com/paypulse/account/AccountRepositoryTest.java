package com.paypulse.account;
import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import com.paypulse.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DataJpaTest
public class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    private Account newAccount(String accountNumber, AccountStatus status) {
        return Account.builder()
                .id(UUID.randomUUID().toString())
                .label("Test Account")
                .accountNumber(accountNumber)
                .currency("INR")
                .status(status)
                .build();
    }

    @Test
    void save_andFindById_returnsAccount() {
        Account saved = accountRepository.save(newAccount("ACCTEST01", AccountStatus.ACTIVE));

        assertThat(accountRepository.findById(saved.getId())).isPresent();


    }

    @Test
    void findByStatus_returnsOnlyMatching() {
        accountRepository.save(newAccount("ACCTEST02", AccountStatus.ACTIVE));
        accountRepository.save(newAccount("ACCTEST03", AccountStatus.INACTIVE));

        var activeOnly = accountRepository.findByStatus(AccountStatus.ACTIVE);

        assertThat(activeOnly).extracting(Account::getAccountNumber).contains("ACCTEST02");
        assertThat(activeOnly).extracting(Account::getAccountNumber).doesNotContain("ACCTEST03");
    }

    @Test
    void findByAccountNumber_whenExists_returnsAccount() {
        accountRepository.save(newAccount("ACCTEST04", AccountStatus.ACTIVE));

        assertThat(accountRepository.findByAccountNumber("ACCTEST04")).isPresent();
    }

    @Test
    void duplicateAccountNumber_throwsDataIntegrityViolation() {
        accountRepository.saveAndFlush(newAccount("ACCTEST05", AccountStatus.ACTIVE));

        assertThatThrownBy(() ->
                accountRepository.saveAndFlush(newAccount("ACCTEST05", AccountStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

}



