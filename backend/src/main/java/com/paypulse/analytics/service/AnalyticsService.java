package com.paypulse.analytics.service;

import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.analytics.dto.TrendResponse;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.repository.PaymentStatusHistoryRepository;
import com.paypulse.payment.service.PaymentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final int maxTrendHours;

    public AnalyticsService(PaymentRepository paymentRepository,
                            PaymentStatusHistoryRepository historyRepository,
                            @Value("${paypulse.analytics.trend.max-hours:168}") int maxTrendHours) {
        this.paymentRepository = paymentRepository;
        this.historyRepository = historyRepository;
        this.maxTrendHours = maxTrendHours;
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

        long cancelled = paymentRepository.countByStatusAndCreatedAtBetween(
                PaymentStatus.CANCELLED, from, to);

        long terminal = completed + failed + cancelled;

        double successRate = terminal == 0 ? 0.0 : (completed * 100.0) / terminal;
        double failureRate = terminal == 0 ? 0.0 : (failed * 100.0) / terminal;
        double cancelledRate = terminal == 0 ? 0.0 : (cancelled * 100.0) / terminal;

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
                .cancelledRatePct(round2(cancelledRate))
                .cancelledCount(cancelled)
                .avgProcessingTimeSeconds(avgSeconds == null ? 0.0 : round2(avgSeconds))
                .throughputPerMinute(round2(throughput))
                .volumeByCurrency(volumeByCurrency)
                .topFailureReasons(topFailures)
                .build();
    }

    /**
     * V2 (feature #13 deepening): now validates the requested window against a
     * configurable cap (paypulse.analytics.trend.max-hours, default 7 days) —
     * defensive bound against an unreasonably large aggregation request — and
     * enriches each hourly bucket with a per-currency volume breakdown, plus a
     * cancelled-count field, so the dashboard's trend view can show more than
     * just created/completed/failed status counts.
     */
    public TrendResponse getTrend(int hours) {

        if (hours <= 0 || hours > maxTrendHours) {
            throw new PaymentException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "hours must be between 1 and " + maxTrendHours
            );
        }

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

            long cancelled = paymentRepository.countByStatusAndCreatedAtBetween(
                    PaymentStatus.CANCELLED,
                    bucketStart,
                    bucketEnd
            );

            Map<String, BigDecimal> volumeByCurrency = new LinkedHashMap<>();
            if (created > 0) {
                for (Object[] row : paymentRepository.sumAmountByCurrency(bucketStart, bucketEnd)) {
                    volumeByCurrency.put((String) row[0], (BigDecimal) row[1]);
                }
            }

            buckets.add(
                    TrendResponse.Bucket.builder()
                            .periodStart(bucketStart)
                            .created(created)
                            .completed(completed)
                            .failed(failed)
                            .cancelled(cancelled)
                            .volumeByCurrency(volumeByCurrency)
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