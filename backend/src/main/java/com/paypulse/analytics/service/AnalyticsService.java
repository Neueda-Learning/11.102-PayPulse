package com.paypulse.analytics.service;

import com.paypulse.analytics.dto.KpiSummaryResponse;
import com.paypulse.analytics.dto.TrendResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnalyticsService {

    public KpiSummaryResponse getSummary() {

        return new KpiSummaryResponse(
                100,
                85,
                15,
                85.0
        );
    }

    public List<TrendResponse> getTrend() {

        List<TrendResponse> trends = new ArrayList<>();

        trends.add(new TrendResponse("2026-08-01", 12));
        trends.add(new TrendResponse("2026-08-02", 18));
        trends.add(new TrendResponse("2026-08-03", 15));
        trends.add(new TrendResponse("2026-08-04", 20));
        trends.add(new TrendResponse("2026-08-05", 10));

        return trends;
    }
}
