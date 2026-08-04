package com.paypulse.analytics.dto;

public class TrendResponse {

    private String date;
    private long paymentCount;

    public TrendResponse() {
    }

    public TrendResponse(String date, long paymentCount) {
        this.date = date;
        this.paymentCount = paymentCount;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getPaymentCount() {
        return paymentCount;
    }

    public void setPaymentCount(long paymentCount) {
        this.paymentCount = paymentCount;
    }
}
