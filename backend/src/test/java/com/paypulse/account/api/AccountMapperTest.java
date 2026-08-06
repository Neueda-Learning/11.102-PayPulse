package com.paypulse.account.api;

import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountMapperTest {

    @Autowired
    private AccountMapper accountMapper;

    @MockBean
    private io.github.bucket4j.distributed.proxy.ProxyManager<byte[]> proxyManager;
    @Test
    void toResponse_allFieldsMapped() {
        Account account = Account.builder()
                .id("acc-123")
                .label("Main INR Account")
                .accountNumber("ACC1000001")
                .currency("INR")
                .status(AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        AccountResponse response = accountMapper.toResponse(account);

        assertThat(response.getId()).isEqualTo("acc-123");
        assertThat(response.getLabel()).isEqualTo("Main INR Account");
        assertThat(response.getAccountNumber()).isEqualTo("ACC1000001");
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void toResponse_inactiveAccount_statusMappedCorrectly() {
        Account account = Account.builder()
                .id("acc-456")
                .label("Inactive USD")
                .accountNumber("ACC1000002")
                .currency("USD")
                .status(AccountStatus.INACTIVE)
                .build();

        AccountResponse response = accountMapper.toResponse(account);

        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        assertThat(response.getCurrency()).isEqualTo("USD");
    }
}