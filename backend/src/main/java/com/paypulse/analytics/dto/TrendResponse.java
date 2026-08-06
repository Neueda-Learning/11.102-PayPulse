package com.paypulse.analytics.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrendResponse {
    private List<Bucket> buckets;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Bucket {
        private Instant periodStart;
        private long created;
        private long completed;
        private long failed;
        private long cancelled;
        private Map<String, BigDecimal> volumeByCurrency;
    }
}