package com.nuono.next.sales;

import java.time.LocalDate;

public class SalesHistoryCoverage {
    private final LocalDate requestedDateFrom;
    private final LocalDate requestedDateTo;
    private final LocalDate salesFactDateFrom;
    private final LocalDate salesFactDateTo;
    private final LocalDate priceDateFrom;
    private final LocalDate priceDateTo;
    private final boolean salesFactsFullyCovered;
    private final boolean priceFactsFullyCovered;
    private final SalesHistoryBackfillStatus backfill;

    public SalesHistoryCoverage(
            LocalDate requestedDateFrom,
            LocalDate requestedDateTo,
            LocalDate salesFactDateFrom,
            LocalDate salesFactDateTo,
            LocalDate priceDateFrom,
            LocalDate priceDateTo,
            boolean salesFactsFullyCovered,
            boolean priceFactsFullyCovered,
            SalesHistoryBackfillStatus backfill
    ) {
        this.requestedDateFrom = requestedDateFrom;
        this.requestedDateTo = requestedDateTo;
        this.salesFactDateFrom = salesFactDateFrom;
        this.salesFactDateTo = salesFactDateTo;
        this.priceDateFrom = priceDateFrom;
        this.priceDateTo = priceDateTo;
        this.salesFactsFullyCovered = salesFactsFullyCovered;
        this.priceFactsFullyCovered = priceFactsFullyCovered;
        this.backfill = backfill == null ? SalesHistoryBackfillStatus.needsBackfill() : backfill;
    }

    public LocalDate getRequestedDateFrom() {
        return requestedDateFrom;
    }

    public LocalDate getRequestedDateTo() {
        return requestedDateTo;
    }

    public LocalDate getSalesFactDateFrom() {
        return salesFactDateFrom;
    }

    public LocalDate getSalesFactDateTo() {
        return salesFactDateTo;
    }

    public LocalDate getPriceDateFrom() {
        return priceDateFrom;
    }

    public LocalDate getPriceDateTo() {
        return priceDateTo;
    }

    public boolean isSalesFactsFullyCovered() {
        return salesFactsFullyCovered;
    }

    public boolean isPriceFactsFullyCovered() {
        return priceFactsFullyCovered;
    }

    public SalesHistoryBackfillStatus getBackfill() {
        return backfill;
    }
}
