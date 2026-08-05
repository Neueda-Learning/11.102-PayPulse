package com.paypulse.analytics.api;

import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.analytics.dto.TrendResponse;
import com.paypulse.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AnalyticsService analyticsService;

    @Test
    void getSummary_returns200WithBody() throws Exception {
        when(analyticsService.getSummary(any(), any())).thenReturn(
                KpiSummaryResponse.builder()
                        .totalPayments(5).successRatePct(80.0).failureRatePct(20.0)
                        .avgProcessingTimeSeconds(3.2).throughputPerMinute(1.5)
                        .volumeByCurrency(Collections.emptyMap())
                        .topFailureReasons(Collections.emptyMap())
                        .build());

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPayments").value(5));
    }

    @Test
    void getTrend_returns200WithBuckets() throws Exception {
        when(analyticsService.getTrend(anyInt())).thenReturn(
                TrendResponse.builder().buckets(List.of()).build());

        mockMvc.perform(get("/api/v1/analytics/trend?hours=6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets").isArray());
    }
}