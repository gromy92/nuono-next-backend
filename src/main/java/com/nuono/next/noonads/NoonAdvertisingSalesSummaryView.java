package com.nuono.next.noonads;

import java.math.BigDecimal;

public class NoonAdvertisingSalesSummaryView {
    private int netUnits;
    private BigDecimal revenueShipped = BigDecimal.ZERO;
    private BigDecimal adSpendShareOfSales = BigDecimal.ZERO;

    public NoonAdvertisingSalesSummaryView() {
    }

    public NoonAdvertisingSalesSummaryView(int netUnits, BigDecimal revenueShipped) {
        this.netUnits = netUnits;
        this.revenueShipped = valueOrZero(revenueShipped);
    }

    public int getNetUnits() { return netUnits; }
    public void setNetUnits(int netUnits) { this.netUnits = netUnits; }
    public BigDecimal getRevenueShipped() { return revenueShipped; }
    public void setRevenueShipped(BigDecimal revenueShipped) { this.revenueShipped = valueOrZero(revenueShipped); }
    public BigDecimal getAdSpendShareOfSales() { return adSpendShareOfSales; }
    public void setAdSpendShareOfSales(BigDecimal adSpendShareOfSales) { this.adSpendShareOfSales = valueOrZero(adSpendShareOfSales); }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
