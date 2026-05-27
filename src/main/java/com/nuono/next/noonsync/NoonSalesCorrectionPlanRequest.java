package com.nuono.next.noonsync;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class NoonSalesCorrectionPlanRequest {

    private final NoonSyncScope scope;
    private final LocalDate latestAvailableSalesDay;
    private final LocalDate lastSuccessfulCorrectionDate;
    private final NoonSalesCorrectionPolicy policy;
    private final List<NoonSalesCorrectionMeasurement> measurements;

    public NoonSalesCorrectionPlanRequest(
            NoonSyncScope scope,
            LocalDate latestAvailableSalesDay,
            LocalDate lastSuccessfulCorrectionDate,
            NoonSalesCorrectionPolicy policy,
            List<NoonSalesCorrectionMeasurement> measurements
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.latestAvailableSalesDay = Objects.requireNonNull(latestAvailableSalesDay, "latestAvailableSalesDay");
        this.lastSuccessfulCorrectionDate = lastSuccessfulCorrectionDate;
        this.policy = policy == null ? NoonSalesCorrectionPolicy.defaultPolicy() : policy;
        this.measurements = measurements == null ? List.of() : List.copyOf(measurements);
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public LocalDate getLatestAvailableSalesDay() {
        return latestAvailableSalesDay;
    }

    public LocalDate getLastSuccessfulCorrectionDate() {
        return lastSuccessfulCorrectionDate;
    }

    public NoonSalesCorrectionPolicy getPolicy() {
        return policy;
    }

    public List<NoonSalesCorrectionMeasurement> getMeasurements() {
        return measurements;
    }
}
