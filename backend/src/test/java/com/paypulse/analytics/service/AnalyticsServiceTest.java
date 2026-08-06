package com.paypulse.analytics.service;

import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.analytics.read.AnalyticsReadRepository;
import com.paypulse.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private final AnalyticsReadRepository analyticsReadRepository = mock(AnalyticsReadRepository.class);
    private final AnalyticsService service = new AnalyticsService(analyticsReadRepository, 168);

    @Test
    void getSummary_zeroPayments_returnsZeroedRatesNoDivideByZero() {
        when(analyticsReadRepository.countCreatedBetween(any(), any())).thenReturn(0L);
        when(analyticsReadRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.COMPLETED), any(), any())).thenReturn(0L);
        when(analyticsReadRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.FAILED), any(), any())).thenReturn(0L);
        when(analyticsReadRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any())).thenReturn(0L);
        when(analyticsReadRepository.avgProcessingTimeSeconds(any(), any())).thenReturn(null);
        when(analyticsReadRepository.sumCompletedAmountByCurrency(any(), any())).thenReturn(Collections.emptyMap());
        when(analyticsReadRepository.topFailureReasons(any(), any())).thenReturn(Collections.emptyMap());
        when(analyticsReadRepository.maxCreatedAtBetween(any(), any())).thenReturn(null);

        KpiSummaryResponse result = service.getSummary(null, null);

        assertThat(result.getTotalPayments()).isZero();
        assertThat(result.getSuccessRatePct()).isZero();
        assertThat(result.getFailureRatePct()).isZero();
        assertThat(result.getAvgProcessingTimeSeconds()).isZero();
        assertThat(result.getVolumeByCurrency()).isEmpty();
        assertThat(result.getTopFailureReasons()).isEmpty();
    }

    @Test
    void getSummary_withData_computesRatesCorrectly() {
        when(analyticsReadRepository.countCreatedBetween(any(), any())).thenReturn(10L);
        when(analyticsReadRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.COMPLETED), any(), any())).thenReturn(8L);
        when(analyticsReadRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.FAILED), any(), any())).thenReturn(2L);
        when(analyticsReadRepository.countByStatusAndCreatedBetween(eq(PaymentStatus.CANCELLED), any(), any())).thenReturn(0L);
        when(analyticsReadRepository.avgProcessingTimeSeconds(any(), any())).thenReturn(12.5);
        when(analyticsReadRepository.sumCompletedAmountByCurrency(any(), any())).thenReturn(Collections.emptyMap());
        when(analyticsReadRepository.topFailureReasons(any(), any())).thenReturn(Collections.emptyMap());
        when(analyticsReadRepository.maxCreatedAtBetween(any(), any())).thenReturn(null);

        KpiSummaryResponse result = service.getSummary(null, null);

        assertThat(result.getSuccessRatePct()).isEqualTo(80.0);
        assertThat(result.getFailureRatePct()).isEqualTo(20.0);
        assertThat(result.getAvgProcessingTimeSeconds()).isEqualTo(12.5);
    }

    @Test
    void getTrend_returnsOneBucketPerHour() {
        when(analyticsReadRepository.countCreatedBetween(any(), any())).thenReturn(1L);
        when(analyticsReadRepository.countByStatusAndCreatedBetween(any(), any(), any())).thenReturn(0L);
        when(analyticsReadRepository.sumCompletedAmountByCurrency(any(), any())).thenReturn(Collections.emptyMap());

        var result = service.getTrend(6);

        assertThat(result.getBuckets()).hasSize(6);
    }

    @Test
    void getTrend_hoursExceedsMax_throwsValidationError() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getTrend(200))
                .isInstanceOf(com.paypulse.payment.service.PaymentException.class);
    }

    @Test
    void getTrend_zeroOrNegativeHours_throwsValidationError() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getTrend(0))
                .isInstanceOf(com.paypulse.payment.service.PaymentException.class);
    }
}
