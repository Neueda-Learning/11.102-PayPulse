package com.paypulse.analytics.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KpiSummaryResponse {
    private long totalPayments;
    private double successRatePct;
    private double failureRatePct;
    private double cancelledRatePct;
    private long cancelledCount;
    private double avgProcessingTimeSeconds;
    private double throughputPerMinute;
    private Map<String, BigDecimal> volumeByCurrency;
    private Map<String, Long> topFailureReasons;
}