package com.paypulse.analytics.service;

import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.repository.PaymentRepository;
import com.paypulse.payment.repository.PaymentStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentStatusHistoryRepository historyRepository = mock(PaymentStatusHistoryRepository.class);
    private final AnalyticsService service = new AnalyticsService(paymentRepository, historyRepository);

    @Test
    void getSummary_zeroPayments_returnsZeroedRatesNoDivideByZero() {
        when(paymentRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(paymentRepository.countByStatusAndCreatedAtBetween(eq(PaymentStatus.COMPLETED), any(), any())).thenReturn(0L);
        when(paymentRepository.countByStatusAndCreatedAtBetween(eq(PaymentStatus.FAILED), any(), any())).thenReturn(0L);
        when(historyRepository.avgProcessingTimeSeconds(any(), any())).thenReturn(null);
        when(paymentRepository.sumAmountByCurrency(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.topFailureReasons(any(), any())).thenReturn(Collections.emptyList());

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
        when(paymentRepository.countByCreatedAtBetween(any(), any())).thenReturn(10L);
        when(paymentRepository.countByStatusAndCreatedAtBetween(eq(PaymentStatus.COMPLETED), any(), any())).thenReturn(8L);
        when(paymentRepository.countByStatusAndCreatedAtBetween(eq(PaymentStatus.FAILED), any(), any())).thenReturn(2L);
        when(historyRepository.avgProcessingTimeSeconds(any(), any())).thenReturn(12.5);
        when(paymentRepository.sumAmountByCurrency(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.topFailureReasons(any(), any())).thenReturn(Collections.emptyList());

        KpiSummaryResponse result = service.getSummary(null, null);

        assertThat(result.getSuccessRatePct()).isEqualTo(80.0);
        assertThat(result.getFailureRatePct()).isEqualTo(20.0);
        assertThat(result.getAvgProcessingTimeSeconds()).isEqualTo(12.5);
    }

    @Test
    void getTrend_returnsOneBucketPerHour() {
        when(paymentRepository.countByCreatedAtBetween(any(), any())).thenReturn(1L);
        when(paymentRepository.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);

        var result = service.getTrend(6);

        assertThat(result.getBuckets()).hasSize(6);
    }
}
