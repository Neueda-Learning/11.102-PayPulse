package com.paypulse.analytics.api;

import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.analytics.dto.TrendResponse;
import com.paypulse.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Analytics", description = "KPI dashboard summary and trend data")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics/summary")
    @Operation(summary = "Get KPI summary")
    public KpiSummaryResponse getSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return analyticsService.getSummary(from, to);
    }

    @GetMapping("/analytics/trend")
    @Operation(summary = "Get hourly payment trend")
    public TrendResponse getTrend(@RequestParam(defaultValue = "24") int hours) {
        return analyticsService.getTrend(hours);
    }
}