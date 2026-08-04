package com.paypulse.payment.service;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.TriggeredBy;

import java.time.Instant;

/**
 * Published by StatusTransitionEngine after every successful status transition
 * is persisted (history row already written by this point).
 *
 * Per docs/06-DESIGN-PATTERNS.md #11 (Observer): this is an optimization signal
 * for downstream consumers (e.g. M4's AnalyticsEventListener) to keep
 * lightweight KPI counters warm — it is NOT the source of truth. GET
 * /analytics/summary can always recompute directly from payment/
 * payment_status_history if this event is ever missed or unavailable.
 */
public record PaymentStatusChangedEvent(
        String paymentId,
        PaymentStatus previousStatus,
        PaymentStatus newStatus,
        String errorCode,
        String errorMessage,
        TriggeredBy triggeredBy,
        Instant occurredAt
) {
}