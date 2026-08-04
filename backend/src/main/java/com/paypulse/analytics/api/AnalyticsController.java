package com.paypulse.analytics.api;

import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.analytics.dto.TrendResponse;
import com.paypulse.analytics.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics/summary")
    public KpiSummaryResponse getSummary() {
        return analyticsService.getSummary();
    }

    @GetMapping("/analytics/trend")
    public List<TrendResponse> getTrend() {
        return analyticsService.getTrend();
    }
}

