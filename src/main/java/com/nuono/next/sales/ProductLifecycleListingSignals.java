package com.nuono.next.sales;

import java.time.LocalDate;

public class ProductLifecycleListingSignals {

    private final ProductLifecycleStateQuery query;
    private final LocalDate officialListingDate;
    private final LocalDate earliestInventoryDate;
    private final LocalDate earliestPvDate;
    private final LocalDate earliestSalesDate;
    private final LocalDate productPulledDate;
    private final int historicalSignalDays;
    private final int salesSignalDays;
    private final int pvSignalDays;
    private final int inventorySignalDays;

    public ProductLifecycleListingSignals(
            ProductLifecycleStateQuery query,
            LocalDate officialListingDate,
            LocalDate earliestInventoryDate,
            LocalDate earliestPvDate,
            LocalDate earliestSalesDate,
            LocalDate productPulledDate,
            int historicalSignalDays,
            int salesSignalDays,
            int pvSignalDays,
            int inventorySignalDays
    ) {
        this.query = query;
        this.officialListingDate = officialListingDate;
        this.earliestInventoryDate = earliestInventoryDate;
        this.earliestPvDate = earliestPvDate;
        this.earliestSalesDate = earliestSalesDate;
        this.productPulledDate = productPulledDate;
        this.historicalSignalDays = Math.max(0, historicalSignalDays);
        this.salesSignalDays = Math.max(0, salesSignalDays);
        this.pvSignalDays = Math.max(0, pvSignalDays);
        this.inventorySignalDays = Math.max(0, inventorySignalDays);
    }

    public ProductLifecycleStateQuery getQuery() {
        return query;
    }

    public LocalDate getOfficialListingDate() {
        return officialListingDate;
    }

    public LocalDate getEarliestInventoryDate() {
        return earliestInventoryDate;
    }

    public LocalDate getEarliestPvDate() {
        return earliestPvDate;
    }

    public LocalDate getEarliestSalesDate() {
        return earliestSalesDate;
    }

    public LocalDate getProductPulledDate() {
        return productPulledDate;
    }

    public int getHistoricalSignalDays() {
        return historicalSignalDays;
    }

    public int getSalesSignalDays() {
        return salesSignalDays;
    }

    public int getPvSignalDays() {
        return pvSignalDays;
    }

    public int getInventorySignalDays() {
        return inventorySignalDays;
    }

    public boolean hasSixtyDayHistoricalSignals() {
        return historicalSignalDays >= 60;
    }
}
