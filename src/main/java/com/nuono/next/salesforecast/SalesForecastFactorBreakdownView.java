package com.nuono.next.salesforecast;

import java.math.BigDecimal;

public class SalesForecastFactorBreakdownView {

    private final BigDecimal baseDailySales;
    private final BigDecimal recentDailyTrendRate;
    private final BigDecimal trendFactor;
    private final BigDecimal futureFactor30;
    private final BigDecimal futureFactor60;
    private final BigDecimal futureFactor90;
    private final int forecastUnits30;
    private final int forecastUnits60;
    private final int forecastUnits90;

    public SalesForecastFactorBreakdownView(
            BigDecimal baseDailySales,
            BigDecimal recentDailyTrendRate,
            BigDecimal trendFactor,
            BigDecimal futureFactor,
            BigDecimal futureFactor60,
            BigDecimal futureFactor90,
            int forecastUnits30,
            int forecastUnits60,
            int forecastUnits90
    ) {
        this.baseDailySales = baseDailySales;
        this.recentDailyTrendRate = recentDailyTrendRate;
        this.trendFactor = trendFactor;
        this.futureFactor30 = futureFactor;
        this.futureFactor60 = futureFactor60;
        this.futureFactor90 = futureFactor90;
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

    public BigDecimal getFutureFactor() {
        return futureFactor30;
    }

    public BigDecimal getFutureFactor30() {
        return futureFactor30;
    }

    public BigDecimal getFutureFactor60() {
        return futureFactor60;
    }

    public BigDecimal getFutureFactor90() {
        return futureFactor90;
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
