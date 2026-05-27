package com.nuono.next.noonsync;

import java.time.LocalDate;
import java.util.List;

public class NoonSalesCorrectionPlan {

    private final NoonSyncScope scope;
    private final NoonSyncTriggerMode triggerMode;
    private final NoonSyncTarget target;
    private final boolean due;
    private final LocalDate nextEligibleDate;
    private final int windowDays;
    private final List<Integer> candidateWindowDays;
    private final List<NoonSalesCorrectionMeasurement> measurements;
    private final boolean dailySyncCoupled;
    private final boolean scheduledDeepCorrection;

    public NoonSalesCorrectionPlan(
            NoonSyncScope scope,
            NoonSyncTriggerMode triggerMode,
            NoonSyncTarget target,
            boolean due,
            LocalDate nextEligibleDate,
            int windowDays,
            List<Integer> candidateWindowDays,
            List<NoonSalesCorrectionMeasurement> measurements,
            boolean dailySyncCoupled,
            boolean scheduledDeepCorrection
    ) {
        this.scope = scope;
        this.triggerMode = triggerMode;
        this.target = target;
        this.due = due;
        this.nextEligibleDate = nextEligibleDate;
        this.windowDays = windowDays;
        this.candidateWindowDays = candidateWindowDays == null ? List.of() : List.copyOf(candidateWindowDays);
        this.measurements = measurements == null ? List.of() : List.copyOf(measurements);
        this.dailySyncCoupled = dailySyncCoupled;
        this.scheduledDeepCorrection = scheduledDeepCorrection;
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public NoonSyncTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public NoonSyncTarget getTarget() {
        return target;
    }

    public boolean isDue() {
        return due;
    }

    public LocalDate getNextEligibleDate() {
        return nextEligibleDate;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public List<Integer> getCandidateWindowDays() {
        return candidateWindowDays;
    }

    public List<NoonSalesCorrectionMeasurement> getMeasurements() {
        return measurements;
    }

    public boolean isDailySyncCoupled() {
        return dailySyncCoupled;
    }

    public boolean isScheduledDeepCorrection() {
        return scheduledDeepCorrection;
    }
}
