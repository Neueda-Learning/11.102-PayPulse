package com.paypulse.analytics.read;

import com.paypulse.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public interface AnalyticsReadRepository {

    long countCreatedBetween(Instant from, Instant to);

    long countByStatusAndCreatedBetween(PaymentStatus status, Instant from, Instant to);

    Instant maxCreatedAtBetween(Instant from, Instant to);

    Double avgProcessingTimeSeconds(Instant from, Instant to);

    Map<String, BigDecimal> sumCompletedAmountByCurrency(Instant from, Instant to);

    Map<String, Long> topFailureReasons(Instant from, Instant to);
}

