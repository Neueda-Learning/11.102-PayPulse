package com.paypulse.account.api;

import com.paypulse.account.domain.Account;
import com.paypulse.account.repository.AccountRepository;
import com.paypulse.common.error.ApiError;
import com.paypulse.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Account read endpoints — GET /api/v1/accounts, GET /api/v1/accounts/{id}.
 * Used by the frontend source-account dropdown.
 * Owner: M4 (see docs/13-WORK-DISTRIBUTION.md §2).
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "List the operator's own accounts (source-account picker)")
public class AccountController {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        List<AccountResponse> list = accountRepository.findAll().stream()
                .map(accountMapper::toResponse).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAccountById(@PathVariable String id, HttpServletRequest req) {
        return accountRepository.findById(id)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(accountMapper.toResponse(a)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiError.of(ErrorCode.ACCOUNT_NOT_FOUND,
                                "No account found with id " + id, req.getRequestURI())));
    }
}

