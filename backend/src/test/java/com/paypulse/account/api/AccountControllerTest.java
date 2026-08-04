package com.paypulse.account.api;

import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import com.paypulse.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AccountRepository accountRepository;
    @MockBean private AccountMapper accountMapper;

    private Account sampleAccount() {
        return Account.builder()
                .id("acc-1").label("Main").accountNumber("ACC001")
                .currency("INR").status(AccountStatus.ACTIVE).build();
    }

    private AccountResponse sampleResponse() {
        return AccountResponse.builder()
                .id("acc-1").label("Main").accountNumber("ACC001")
                .currency("INR").status("ACTIVE").build();
    }

    @Test
    void listAccounts_returnsAll() throws Exception {
        when(accountRepository.findAll()).thenReturn(List.of(sampleAccount()));
        when(accountMapper.toResponse(any())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("acc-1"));
    }

    @Test
    void getById_found_returns200() throws Exception {
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(sampleAccount()));
        when(accountMapper.toResponse(any())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/accounts/acc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC001"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(accountRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }
}