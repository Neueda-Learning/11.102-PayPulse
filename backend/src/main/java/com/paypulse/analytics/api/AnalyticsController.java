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
import com.paypulse.analytics.service.DashboardStreamService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Analytics", description = "KPI dashboard summary and trend data")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    private final DashboardStreamService dashboardStreamService;

    public AnalyticsController(AnalyticsService analyticsService,
                               DashboardStreamService dashboardStreamService) {
        this.analyticsService = analyticsService;
        this.dashboardStreamService = dashboardStreamService;
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

    @GetMapping(value = "/analytics/stream", produces = "text/event-stream")
    @Operation(summary = "SSE stream of live KPI updates")
    public SseEmitter stream() {
        return dashboardStreamService.subscribe();
    }
}