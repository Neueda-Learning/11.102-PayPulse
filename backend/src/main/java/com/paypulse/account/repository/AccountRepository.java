package com.paypulse.account.repository;

import com.paypulse.account.domain.Account;
import com.paypulse.account.domain.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Account data access. Owner: M1.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByStatus(AccountStatus status);

    Optional<Account> findByAccountNumber(String accountNumber);
}

