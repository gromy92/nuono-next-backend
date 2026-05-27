package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesForecastFeatureValuesView {

    private final LocalDate latestFactDate;
    private final int historyUnits7;
    private final int historyUnits30;
    private final int historyUnits60;
    private final int historyUnits90;
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
        this.latestFactDate = latestFactDate;
        this.historyUnits7 = historyUnits7;
        this.historyUnits30 = historyUnits30;
        this.historyUnits60 = historyUnits60;
        this.historyUnits90 = historyUnits90;
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
