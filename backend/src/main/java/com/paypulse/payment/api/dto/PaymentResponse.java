package com.paypulse.payment.api.dto;

import com.paypulse.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response payload for payment resources.
 * Shared by M3 create() and M4 read/list endpoints.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private String id;
    private String sourceAccountId;
    private BigDecimal amount;
    private String currency;
    private String destinationAccount;
    private String reference;
    private PaymentStatus status;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}

