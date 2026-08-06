package com.paypulse.analytics.service;

import com.paypulse.analytics.read.AnalyticsReadRepository;
import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.analytics.dto.TrendResponse;
import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.service.PaymentException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final AnalyticsReadRepository analyticsReadRepository;
    private final int maxTrendHours;
    private final int throughputWindowMinutes;

    /**
     * Kept for backward compatibility with existing unit tests that construct
     * this service with 3 args — defaults the throughput trailing window to
     * 5 minutes. The 4-arg constructor below is the one Spring actually uses.
     */
    public AnalyticsService(AnalyticsReadRepository analyticsReadRepository,
                            int maxTrendHours) {
        this(analyticsReadRepository, maxTrendHours, 5);
    }

    @Autowired
    public AnalyticsService(AnalyticsReadRepository analyticsReadRepository,
                            @Value("${paypulse.analytics.trend.max-hours:168}") int maxTrendHours,
                            @Value("${paypulse.analytics.throughput.window-minutes:5}") int throughputWindowMinutes) {
        this.analyticsReadRepository = analyticsReadRepository;
        this.maxTrendHours = maxTrendHours;
        this.throughputWindowMinutes = throughputWindowMinutes;
    }

    public KpiSummaryResponse getSummary(String fromStr, String toStr) {

        Instant to = (toStr != null && !toStr.isBlank())
                ? Instant.parse(toStr)
                : Instant.now();

        Instant from = (fromStr != null && !fromStr.isBlank())
                ? Instant.parse(fromStr)
                : to.minus(24, ChronoUnit.HOURS);

        long total = analyticsReadRepository.countCreatedBetween(from, to);

        long completed = analyticsReadRepository.countByStatusAndCreatedBetween(
                PaymentStatus.COMPLETED, from, to);

        long failed = analyticsReadRepository.countByStatusAndCreatedBetween(
                PaymentStatus.FAILED, from, to);

        long cancelled = analyticsReadRepository.countByStatusAndCreatedBetween(
                PaymentStatus.CANCELLED, from, to);

        long terminal = completed + failed + cancelled;

        double successRate = terminal == 0 ? 0.0 : (completed * 100.0) / terminal;
        double failureRate = terminal == 0 ? 0.0 : (failed * 100.0) / terminal;
        double cancelledRate = terminal == 0 ? 0.0 : (cancelled * 100.0) / terminal;

        Double avgSeconds = analyticsReadRepository.avgProcessingTimeSeconds(from, to);


        // Throughput is intentionally NOT total/minutes over the (potentially
        // multi-day) requested from/to window — that would make even a
        // healthy system look like it has ~0 throughput once the window
        // spans more than a few minutes (e.g. 10 payments / 1440 minutes
        // rounds to 0.00). Per FR-12.1 this is a "trailing window" metric.
        //
        // It's anchored to the most recent payment activity actually
        // present in the window (not blindly to `to`/Instant.now()) —
        // otherwise, for a batch of historical/seeded data whose
        // createdAt timestamps are hours/days in the past relative to
        // wall-clock "now", a naive "last N minutes before now" slice
        // would always be empty and throughput would permanently read
        // 0 even though the data clearly shows real processing activity.
        Instant lastActivity = analyticsReadRepository.maxCreatedAtBetween(from, to);
        double throughput = 0.0;
        if (lastActivity != null) {
            Instant anchor = lastActivity.isAfter(to) ? to : lastActivity;
            Duration requestedWindow = Duration.between(from, anchor);
            Duration throughputWindow = requestedWindow.compareTo(Duration.ofMinutes(throughputWindowMinutes)) < 0
                    ? requestedWindow
                    : Duration.ofMinutes(throughputWindowMinutes);
            Instant throughputFrom = anchor.minus(throughputWindow);
            if (throughputFrom.isBefore(from)) {
                throughputFrom = from;
            }
            long trailingCount = analyticsReadRepository.countCreatedBetween(throughputFrom, anchor);
            double throughputMinutes = Math.max(1.0, throughputWindow.toSeconds() / 60.0);
            throughput = trailingCount / throughputMinutes;
        }

        Map<String, BigDecimal> volumeByCurrency = new LinkedHashMap<>(
                analyticsReadRepository.sumCompletedAmountByCurrency(from, to));

        Map<String, Long> topFailures = new LinkedHashMap<>(
                analyticsReadRepository.topFailureReasons(from, to));

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

            long created = analyticsReadRepository.countCreatedBetween(bucketStart, bucketEnd);

            long completed = analyticsReadRepository.countByStatusAndCreatedBetween(
                    PaymentStatus.COMPLETED,
                    bucketStart,
                    bucketEnd
            );

            long failed = analyticsReadRepository.countByStatusAndCreatedBetween(
                    PaymentStatus.FAILED,
                    bucketStart,
                    bucketEnd
            );

            long cancelled = analyticsReadRepository.countByStatusAndCreatedBetween(
                    PaymentStatus.CANCELLED,
                    bucketStart,
                    bucketEnd
            );

            Map<String, BigDecimal> volumeByCurrency = created > 0
                    ? new LinkedHashMap<>(analyticsReadRepository.sumCompletedAmountByCurrency(bucketStart, bucketEnd))
                    : new LinkedHashMap<>();

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