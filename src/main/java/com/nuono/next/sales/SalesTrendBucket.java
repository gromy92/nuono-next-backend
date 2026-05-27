package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesTrendBucket {

    private final LocalDate bucketStart;
    private final String bucketLabel;
    private final SalesAnalyticsSummary summary;

    public SalesTrendBucket(LocalDate bucketStart, String bucketLabel, SalesAnalyticsSummary summary) {
        this.bucketStart = bucketStart;
        this.bucketLabel = bucketLabel;
        this.summary = summary;
    }

    public LocalDate getBucketStart() {
        return bucketStart;
    }

    public String getBucketLabel() {
        return bucketLabel;
    }

    public int getNetUnits() {
        return summary.getNetUnits();
    }

    public int getGrossUnits() {
        return summary.getGrossUnits();
    }

    public int getShippedUnits() {
        return summary.getShippedUnits();
    }

    public int getCancelledUnits() {
        return summary.getCancelledUnits();
    }

    public BigDecimal getRevenueShipped() {
        return summary.getRevenueShipped();
    }

    public int getYourVisitors() {
        return summary.getYourVisitors();
    }

    public int getTotalVisitors() {
        return summary.getTotalVisitors();
    }

    public BigDecimal getConversionVisitorsPercentage() {
        return summary.getConversionVisitorsPercentage();
    }

    public BigDecimal getBuyBoxVisitorPercentage() {
        return summary.getBuyBoxVisitorPercentage();
    }
}
