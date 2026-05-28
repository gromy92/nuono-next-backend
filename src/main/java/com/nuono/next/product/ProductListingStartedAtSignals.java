package com.nuono.next.product;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductListingStartedAtSignals {

    private final LocalDate firstPvDate;
    private final LocalDateTime firstInventoryAt;
    private final LocalDate firstSalesDate;
    private final LocalDateTime firstPurchaseAt;
    private final LocalDateTime fallbackNow;

    public ProductListingStartedAtSignals(
            LocalDate firstPvDate,
            LocalDateTime firstInventoryAt,
            LocalDate firstSalesDate,
            LocalDateTime firstPurchaseAt,
            LocalDateTime fallbackNow
    ) {
        this.firstPvDate = firstPvDate;
        this.firstInventoryAt = firstInventoryAt;
        this.firstSalesDate = firstSalesDate;
        this.firstPurchaseAt = firstPurchaseAt;
        this.fallbackNow = fallbackNow;
    }

    public LocalDate getFirstPvDate() {
        return firstPvDate;
    }

    public LocalDateTime getFirstInventoryAt() {
        return firstInventoryAt;
    }

    public LocalDate getFirstSalesDate() {
        return firstSalesDate;
    }

    public LocalDateTime getFirstPurchaseAt() {
        return firstPurchaseAt;
    }

    public LocalDateTime getFallbackNow() {
        return fallbackNow;
    }
}
