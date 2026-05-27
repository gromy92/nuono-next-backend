package com.nuono.next.salesforecast;

import java.math.BigDecimal;

public class SalesForecastFactorBreakdownView {

    private final BigDecimal baseDailySales;
    private final BigDecimal recentDailyTrendRate;
    private final BigDecimal trendFactor;
    private final BigDecimal lifecycleFactor;
    private final BigDecimal futureFactor;
    private final int forecastUnits30;
    private final int forecastUnits60;
    private final int forecastUnits90;

    public SalesForecastFactorBreakdownView(
            BigDecimal baseDailySales,
            BigDecimal recentDailyTrendRate,
            BigDecimal trendFactor,
            BigDecimal lifecycleFactor,
            BigDecimal futureFactor,
            int forecastUnits30,
            int forecastUnits60,
            int forecastUnits90
    ) {
        this.baseDailySales = baseDailySales;
        this.recentDailyTrendRate = recentDailyTrendRate;
        this.trendFactor = trendFactor;
        this.lifecycleFactor = lifecycleFactor;
        this.futureFactor = futureFactor;
        this.forecastUnits30 = forecastUnits30;
        this.forecastUnits60 = forecastUnits60;
        this.forecastUnits90 = forecastUnits90;
    }

    public BigDecimal getBaseDailySales() {
        return baseDailySales;
    }

    public BigDecimal getRecentDailyTrendRate() {
        return recentDailyTrendRate;
    }

    public BigDecimal getTrendFactor() {
        return trendFactor;
    }

    public BigDecimal getLifecycleFactor() {
        return lifecycleFactor;
    }

    public BigDecimal getFutureFactor() {
        return futureFactor;
    }

    public int getForecastUnits30() {
        return forecastUnits30;
    }

    public int getForecastUnits60() {
        return forecastUnits60;
    }

    public int getForecastUnits90() {
        return forecastUnits90;
    }
}
