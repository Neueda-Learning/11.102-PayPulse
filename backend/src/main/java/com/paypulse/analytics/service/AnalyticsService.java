package com.paypulse.analytics.service;

import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.analytics.dto.TrendResponse;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.repository.PaymentStatusHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;

    public AnalyticsService(PaymentRepository paymentRepository,
                            PaymentStatusHistoryRepository historyRepository) {
        this.paymentRepository = paymentRepository;
        this.historyRepository = historyRepository;
    }

    public KpiSummaryResponse getSummary(String fromStr, String toStr) {

        Instant to = (toStr != null && !toStr.isBlank())
                ? Instant.parse(toStr)
                : Instant.now();

        Instant from = (fromStr != null && !fromStr.isBlank())
                ? Instant.parse(fromStr)
                : to.minus(24, ChronoUnit.HOURS);

        long total = paymentRepository.countByCreatedAtBetween(from, to);

        long completed = paymentRepository.countByStatusAndCreatedAtBetween(
                PaymentStatus.COMPLETED, from, to);

        long failed = paymentRepository.countByStatusAndCreatedAtBetween(
                PaymentStatus.FAILED, from, to);

        long terminal = completed + failed;

        double successRate = terminal == 0 ? 0.0 : (completed * 100.0) / terminal;
        double failureRate = terminal == 0 ? 0.0 : (failed * 100.0) / terminal;

        Double avgSeconds = historyRepository.avgProcessingTimeSeconds(from, to);

        double minutes = Math.max(
                1.0,
                (double) ChronoUnit.MINUTES.between(from, to)
        );

        double throughput = total / minutes;

        Map<String, BigDecimal> volumeByCurrency = new LinkedHashMap<>();

        for (Object[] row : paymentRepository.sumAmountByCurrency(from, to)) {
            volumeByCurrency.put(
                    (String) row[0],
                    (BigDecimal) row[1]
            );
        }

        Map<String, Long> topFailures = new LinkedHashMap<>();

        for (Object[] row : paymentRepository.topFailureReasons(from, to)) {

            if (row[0] != null) {

                Long count = ((Number) row[1]).longValue();

                topFailures.put(
                        (String) row[0],
                        count
                );
            }
        }

        return KpiSummaryResponse.builder()
                .totalPayments(total)
                .successRatePct(round2(successRate))
                .failureRatePct(round2(failureRate))
                .avgProcessingTimeSeconds(avgSeconds == null ? 0.0 : round2(avgSeconds))
                .throughputPerMinute(round2(throughput))
                .volumeByCurrency(volumeByCurrency)
                .topFailureReasons(topFailures)
                .build();
    }

    public TrendResponse getTrend(int hours) {

        Instant to = Instant.now();
        Instant from = to.minus(hours, ChronoUnit.HOURS);

        List<TrendResponse.Bucket> buckets = new ArrayList<>();

        for (int i = 0; i < hours; i++) {

            Instant bucketStart = from.plus(i, ChronoUnit.HOURS);
            Instant bucketEnd = bucketStart.plus(1, ChronoUnit.HOURS);

            long created = paymentRepository.countByCreatedAtBetween(bucketStart, bucketEnd);

            long completed = paymentRepository.countByStatusAndCreatedAtBetween(
                    PaymentStatus.COMPLETED,
                    bucketStart,
                    bucketEnd
            );

            long failed = paymentRepository.countByStatusAndCreatedAtBetween(
                    PaymentStatus.FAILED,
                    bucketStart,
                    bucketEnd
            );

            buckets.add(
                    TrendResponse.Bucket.builder()
                            .periodStart(bucketStart)
                            .created(created)
                            .completed(completed)
                            .failed(failed)
                            .build()
            );
        }

        return TrendResponse.builder()
                .buckets(buckets)
                .build();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}