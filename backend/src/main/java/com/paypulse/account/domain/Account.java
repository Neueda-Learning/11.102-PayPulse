package com.paypulse.account.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Account entity — the operator's own payment accounts.
 * One operator, multiple accounts (INR or USD each).
 * Seeded in V3__seed_accounts.sql. No account-creation UI in MVP scope.
 * Owner: M1 (see docs/13-WORK-DISTRIBUTION.md §2)
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 100, nullable = false)
    private String label;

    @Column(name = "account_number", length = 20, nullable = false, unique = true)
    private String accountNumber;

    @Column(length = 3, nullable = false)
    private String currency;

    @Column(name = "owner_email", length = 255)
    private String ownerEmail;

    @Column(name = "owner_name", length = 100)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AccountStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

