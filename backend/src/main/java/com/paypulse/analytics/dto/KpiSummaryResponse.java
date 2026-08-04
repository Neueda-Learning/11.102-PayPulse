package com.paypulse.analytics.dto;

public class KpiSummaryResponse {

    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    private double successRate;

    public KpiSummaryResponse() {
    }

    public KpiSummaryResponse(long totalPayments,
                              long successfulPayments,
                              long failedPayments,
                              double successRate) {
        this.totalPayments = totalPayments;
        this.successfulPayments = successfulPayments;
        this.failedPayments = failedPayments;
        this.successRate = successRate;
    }

    public long getTotalPayments() {
        return totalPayments;
    }

    public void setTotalPayments(long totalPayments) {
        this.totalPayments = totalPayments;
    }

    public long getSuccessfulPayments() {
        return successfulPayments;
    }

    public void setSuccessfulPayments(long successfulPayments) {
        this.successfulPayments = successfulPayments;
    }

    public long getFailedPayments() {
        return failedPayments;
    }

    public void setFailedPayments(long failedPayments) {
        this.failedPayments = failedPayments;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }
}
