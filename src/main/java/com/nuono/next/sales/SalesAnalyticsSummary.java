package com.nuono.next.sales;

import java.math.BigDecimal;

public class SalesAnalyticsSummary {

    private final int netUnits;
    private final int grossUnits;
    private final int shippedUnits;
    private final int cancelledUnits;
    private final BigDecimal revenueShipped;
    private final int yourVisitors;
    private final int totalVisitors;
    private final BigDecimal conversionVisitorsPercentage;
    private final BigDecimal buyBoxVisitorPercentage;

    public SalesAnalyticsSummary(
            int netUnits,
            int grossUnits,
            int shippedUnits,
            int cancelledUnits,
            BigDecimal revenueShipped,
            int yourVisitors,
            int totalVisitors,
            BigDecimal conversionVisitorsPercentage,
            BigDecimal buyBoxVisitorPercentage
    ) {
        this.netUnits = netUnits;
        this.grossUnits = grossUnits;
        this.shippedUnits = shippedUnits;
        this.cancelledUnits = cancelledUnits;
        this.revenueShipped = revenueShipped == null ? BigDecimal.ZERO : revenueShipped;
        this.yourVisitors = yourVisitors;
        this.totalVisitors = totalVisitors;
        this.conversionVisitorsPercentage = conversionVisitorsPercentage;
        this.buyBoxVisitorPercentage = buyBoxVisitorPercentage;
    }

    public int getNetUnits() {
        return netUnits;
    }

    public int getGrossUnits() {
        return grossUnits;
    }

    public int getShippedUnits() {
        return shippedUnits;
    }

    public int getCancelledUnits() {
        return cancelledUnits;
    }

    public BigDecimal getRevenueShipped() {
        return revenueShipped;
    }

    public int getYourVisitors() {
        return yourVisitors;
    }

    public int getTotalVisitors() {
        return totalVisitors;
    }

    public BigDecimal getConversionVisitorsPercentage() {
        return conversionVisitorsPercentage;
    }

    public BigDecimal getBuyBoxVisitorPercentage() {
        return buyBoxVisitorPercentage;
    }

}
