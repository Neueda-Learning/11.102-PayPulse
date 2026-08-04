package com.paypulse.payment.service;

import com.paypulse.payment.api.dto.PaymentResponse;

/**
 * Signals whether POST /payments created a new resource or reused an existing
 * one via idempotency.
 */
public record PaymentCreationResult(PaymentResponse payment, boolean created) {
}

