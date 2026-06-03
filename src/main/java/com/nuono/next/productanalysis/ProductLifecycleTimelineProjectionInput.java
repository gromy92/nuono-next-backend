package com.nuono.next.productanalysis;

import java.time.LocalDate;

public class ProductLifecycleTimelineProjectionInput {

    private final String currentLifecycleCode;
    private final String currentLifecycleLabel;
    private final LocalDate currentStageStartDate;
    private final LocalDate analysisDate;
    private final ProductLifecycleStagePeriodConfig periodConfig;
    private final int forecastDays;

    public ProductLifecycleTimelineProjectionInput(
            String currentLifecycleCode,
            String currentLifecycleLabel,
            LocalDate currentStageStartDate,
            LocalDate analysisDate,
            ProductLifecycleStagePeriodConfig periodConfig,
            int forecastDays
    ) {
        this.currentLifecycleCode = currentLifecycleCode;
        this.currentLifecycleLabel = currentLifecycleLabel;
        this.currentStageStartDate = currentStageStartDate;
        this.analysisDate = analysisDate;
        this.periodConfig = periodConfig;
        this.forecastDays = forecastDays;
    }

    public String getCurrentLifecycleCode() {
        return currentLifecycleCode;
    }

    public String getCurrentLifecycleLabel() {
        return currentLifecycleLabel;
    }

    public LocalDate getCurrentStageStartDate() {
        return currentStageStartDate;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public ProductLifecycleStagePeriodConfig getPeriodConfig() {
        return periodConfig;
    }

    public int getForecastDays() {
        return forecastDays;
    }
}
