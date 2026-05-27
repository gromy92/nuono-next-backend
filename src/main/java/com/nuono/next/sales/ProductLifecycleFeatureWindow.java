package com.nuono.next.sales;

public class ProductLifecycleFeatureWindow {

    private final int dayCount;
    private final int observedDays;
    private final int salesUnits;
    private final Long pv;
    private final int activeSalesDays;
    private final int pvObservedDays;
    private final boolean pvEstimated;

    public ProductLifecycleFeatureWindow(
            int dayCount,
            int observedDays,
            int salesUnits,
            Long pv,
            int activeSalesDays,
            int pvObservedDays,
            boolean pvEstimated
    ) {
        this.dayCount = dayCount;
        this.observedDays = observedDays;
        this.salesUnits = salesUnits;
        this.pv = pv;
        this.activeSalesDays = activeSalesDays;
        this.pvObservedDays = pvObservedDays;
        this.pvEstimated = pvEstimated;
    }

    public int getDayCount() {
        return dayCount;
    }

    public int getObservedDays() {
        return observedDays;
    }

    public int getSalesUnits() {
        return salesUnits;
    }

    public Long getPv() {
        return pv;
    }

    public int getActiveSalesDays() {
        return activeSalesDays;
    }

    public int getPvObservedDays() {
        return pvObservedDays;
    }

    public boolean isPvEstimated() {
        return pvEstimated;
    }

    public ProductLifecycleFeatureWindow withEstimatedPv(long estimatedPv) {
        return new ProductLifecycleFeatureWindow(
                dayCount,
                observedDays,
                salesUnits,
                estimatedPv,
                activeSalesDays,
                pvObservedDays,
                true
        );
    }
}
