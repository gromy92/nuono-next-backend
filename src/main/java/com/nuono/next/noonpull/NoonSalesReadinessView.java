package com.nuono.next.noonpull;

import java.math.BigDecimal;

public class NoonSalesReadinessView {
    private String qualityState;
    private boolean metricsAllowed;
    private BigDecimal salesAmount;
    private Long unitsSold;

    public String getQualityState() {
        return qualityState;
    }

    public void setQualityState(String qualityState) {
        this.qualityState = qualityState;
    }

    public boolean isMetricsAllowed() {
        return metricsAllowed;
    }

    public void setMetricsAllowed(boolean metricsAllowed) {
        this.metricsAllowed = metricsAllowed;
    }

    public BigDecimal getSalesAmount() {
        return salesAmount;
    }

    public void setSalesAmount(BigDecimal salesAmount) {
        this.salesAmount = salesAmount;
    }

    public Long getUnitsSold() {
        return unitsSold;
    }

    public void setUnitsSold(Long unitsSold) {
        this.unitsSold = unitsSold;
    }
}
