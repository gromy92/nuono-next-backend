package com.nuono.next.salesforecast;

public class SalesForecastDetailView {

    private final SalesForecastFeatureValuesView featureValues;
    private final SalesForecastFactorBreakdownView factorBreakdown;
    private final String lifecycleExplanation;
    private final String calculationVersion;
    private final String configVersion;

    public SalesForecastDetailView(
            SalesForecastFeatureValuesView featureValues,
            SalesForecastFactorBreakdownView factorBreakdown,
            String lifecycleExplanation,
            String calculationVersion,
            String configVersion
    ) {
        this.featureValues = featureValues;
        this.factorBreakdown = factorBreakdown;
        this.lifecycleExplanation = lifecycleExplanation;
        this.calculationVersion = calculationVersion;
        this.configVersion = configVersion;
    }

    public static SalesForecastDetailView fromResult(SalesForecastResultRecord record) {
        return new SalesForecastDetailView(
                new SalesForecastFeatureValuesView(
                        record.getLatestFactDate(),
                        record.getHistoryUnits7(),
                        record.getHistoryUnits30(),
                        record.getHistoryUnits60(),
                        record.getHistoryUnits90(),
                        record.getObservedDays(),
                        record.getCurrentStock(),
                        record.getStockCoverDays()
                ),
                new SalesForecastFactorBreakdownView(
                        record.getBaseDailySales(),
                        record.getRecentDailyTrendRate(),
                        record.getTrendFactor(),
                        record.getLifecycleFactor(),
                        record.getFutureFactor(),
                        record.getForecastUnits30(),
                        record.getForecastUnits60(),
                        record.getForecastUnits90()
                ),
                record.getLifecycleExplanation(),
                record.getCalculationVersion(),
                record.getConfigVersion()
        );
    }

    public SalesForecastFeatureValuesView getFeatureValues() {
        return featureValues;
    }

    public SalesForecastFactorBreakdownView getFactorBreakdown() {
        return factorBreakdown;
    }

    public String getLifecycleExplanation() {
        return lifecycleExplanation;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public String getConfigVersion() {
        return configVersion;
    }
}
