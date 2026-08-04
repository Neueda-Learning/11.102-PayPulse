package com.paypulse.payment.api.dto;

import com.paypulse.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response payload for a single payment status-transition audit entry.
 * Owner: M2. Consumed by GET /payments/{id}/history and by M4's
 * PaymentDetailsPage.tsx via the frozen StatusHistoryTimeline contract.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistoryResponse {

    private PaymentStatus previousStatus;
    private PaymentStatus newStatus;
    private String errorCode;
    private String errorMessage;
    private String triggeredBy;
    private Instant occurredAt;
}