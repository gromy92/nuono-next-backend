package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesForecastFeatureValuesView {

    private final LocalDate latestFactDate;
    private final int historyUnits7;
    private final int historyUnits30;
    private final int historyUnits60;
    private final int historyUnits90;
    private final BigDecimal adjustedHistoryUnits7;
    private final BigDecimal adjustedHistoryUnits30;
    private final BigDecimal adjustedHistoryUnits60;
    private final BigDecimal adjustedHistoryUnits90;
    private final int observedDays;
    private final Integer currentStock;
    private final BigDecimal stockCoverDays;

    public SalesForecastFeatureValuesView(
            LocalDate latestFactDate,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            int observedDays,
            Integer currentStock,
            BigDecimal stockCoverDays
    ) {
        this(
                latestFactDate,
                historyUnits7,
                historyUnits30,
                historyUnits60,
                historyUnits90,
                BigDecimal.valueOf(historyUnits7),
                BigDecimal.valueOf(historyUnits30),
                BigDecimal.valueOf(historyUnits60),
                BigDecimal.valueOf(historyUnits90),
                observedDays,
                currentStock,
                stockCoverDays
        );
    }

    public SalesForecastFeatureValuesView(
            LocalDate latestFactDate,
            int historyUnits7,
            int historyUnits30,
            int historyUnits60,
            int historyUnits90,
            BigDecimal adjustedHistoryUnits7,
            BigDecimal adjustedHistoryUnits30,
            BigDecimal adjustedHistoryUnits60,
            BigDecimal adjustedHistoryUnits90,
            int observedDays,
            Integer currentStock,
            BigDecimal stockCoverDays
    ) {
        this.latestFactDate = latestFactDate;
        this.historyUnits7 = historyUnits7;
        this.historyUnits30 = historyUnits30;
        this.historyUnits60 = historyUnits60;
        this.historyUnits90 = historyUnits90;
        this.adjustedHistoryUnits7 = adjustedHistoryUnits7 == null ? BigDecimal.valueOf(historyUnits7) : adjustedHistoryUnits7;
        this.adjustedHistoryUnits30 = adjustedHistoryUnits30 == null ? BigDecimal.valueOf(historyUnits30) : adjustedHistoryUnits30;
        this.adjustedHistoryUnits60 = adjustedHistoryUnits60 == null ? BigDecimal.valueOf(historyUnits60) : adjustedHistoryUnits60;
        this.adjustedHistoryUnits90 = adjustedHistoryUnits90 == null ? BigDecimal.valueOf(historyUnits90) : adjustedHistoryUnits90;
        this.observedDays = observedDays;
        this.currentStock = currentStock;
        this.stockCoverDays = stockCoverDays;
    }

    public LocalDate getLatestFactDate() {
        return latestFactDate;
    }

    public int getHistoryUnits7() {
        return historyUnits7;
    }

    public int getHistoryUnits30() {
        return historyUnits30;
    }

    public int getHistoryUnits60() {
        return historyUnits60;
    }

    public int getHistoryUnits90() {
        return historyUnits90;
    }

    public BigDecimal getAdjustedHistoryUnits7() {
        return adjustedHistoryUnits7;
    }

    public BigDecimal getAdjustedHistoryUnits30() {
        return adjustedHistoryUnits30;
    }

    public BigDecimal getAdjustedHistoryUnits60() {
        return adjustedHistoryUnits60;
    }

    public BigDecimal getAdjustedHistoryUnits90() {
        return adjustedHistoryUnits90;
    }

    public int getObservedDays() {
        return observedDays;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public BigDecimal getStockCoverDays() {
        return stockCoverDays;
    }
}
