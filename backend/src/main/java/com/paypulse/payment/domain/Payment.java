package com.paypulse.payment.domain;

import com.paypulse.payment.PaymentStatus;
import  jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import  java.math.BigDecimal;
import java.time.Instant;


// Payment entity - Core transaction record.

@Entity
@Table(name= "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Payment {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "source_account_id", length = 36,nullable = false)
    private String sourceAccountId;

    @Column(name = "destination_account", length = 20,nullable = false)
    private String destinationAccount;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency;


    @Column(length = 255)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentStatus status;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Not persisted — carries a UI-selected forced-failure stage
     * (NONE/CREATE/VALIDATE/SEND) through the same-request automatic
     * lifecycle (validate -> send -> complete) so StatusTransitionEngine
     * can deterministically fail exactly where the caller asked.
     */
    @Transient
    private String forcedFailureStage;

}
