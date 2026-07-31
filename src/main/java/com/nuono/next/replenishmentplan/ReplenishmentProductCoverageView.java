package com.nuono.next.replenishmentplan;

public final class ReplenishmentProductCoverageView {
    private final int totalProductCount;
    private final int forecastedProductCount;
    private final int activeProductCount;
    private final int inactiveProductCount;
    private final int unknownProductCount;

    public ReplenishmentProductCoverageView(
            int totalProductCount,
            int forecastedProductCount,
            int activeProductCount,
            int inactiveProductCount,
            int unknownProductCount
    ) {
        this.totalProductCount = Math.max(0, totalProductCount);
        this.forecastedProductCount = Math.max(0, forecastedProductCount);
        this.activeProductCount = Math.max(0, activeProductCount);
        this.inactiveProductCount = Math.max(0, inactiveProductCount);
        this.unknownProductCount = Math.max(0, unknownProductCount);
    }

    public static ReplenishmentProductCoverageView empty() {
        return new ReplenishmentProductCoverageView(0, 0, 0, 0, 0);
    }

    public int getTotalProductCount() { return totalProductCount; }
    public int getForecastedProductCount() { return forecastedProductCount; }
    public int getActiveProductCount() { return activeProductCount; }
    public int getInactiveProductCount() { return inactiveProductCount; }
    public int getUnknownProductCount() { return unknownProductCount; }
}
