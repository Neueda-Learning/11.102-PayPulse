package com.paypulse.analytics.dto;

import lombok.*;
import java.time.Instant;
import java.util.List;

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
    }
}