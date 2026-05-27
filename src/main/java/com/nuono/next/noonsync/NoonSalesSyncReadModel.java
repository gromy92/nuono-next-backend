package com.nuono.next.noonsync;

import java.time.LocalDate;
import java.util.List;

public class NoonSalesSyncReadModel {

    private final NoonSalesSyncSurfaceState state;
    private final NoonSalesCorrectionSurfaceState correctionState;
    private final LocalDate latestAvailableSalesDate;
    private final NoonSyncTaskStatus taskStatus;
    private final NoonSyncFailureReason failureReason;
    private final String diagnosticSummary;
    private final boolean dataSufficient;
    private final boolean businessMetricsAllowed;
    private final List<NoonSyncRequiredWork> requiredWork;
    private final List<Long> sourceBatchIds;

    public NoonSalesSyncReadModel(
            NoonSalesSyncSurfaceState state,
            NoonSalesCorrectionSurfaceState correctionState,
            LocalDate latestAvailableSalesDate,
            NoonSyncTaskStatus taskStatus,
            NoonSyncFailureReason failureReason,
            String diagnosticSummary,
            boolean dataSufficient,
            boolean businessMetricsAllowed,
            List<NoonSyncRequiredWork> requiredWork
    ) {
        this(
                state,
                correctionState,
                latestAvailableSalesDate,
                taskStatus,
                failureReason,
                diagnosticSummary,
                dataSufficient,
                businessMetricsAllowed,
                requiredWork,
                List.of()
        );
    }

    public NoonSalesSyncReadModel(
            NoonSalesSyncSurfaceState state,
            NoonSalesCorrectionSurfaceState correctionState,
            LocalDate latestAvailableSalesDate,
            NoonSyncTaskStatus taskStatus,
            NoonSyncFailureReason failureReason,
            String diagnosticSummary,
            boolean dataSufficient,
            boolean businessMetricsAllowed,
            List<NoonSyncRequiredWork> requiredWork,
            List<Long> sourceBatchIds
    ) {
        this.state = state;
        this.correctionState = correctionState == null ? NoonSalesCorrectionSurfaceState.OK : correctionState;
        this.latestAvailableSalesDate = latestAvailableSalesDate;
        this.taskStatus = taskStatus;
        this.failureReason = failureReason;
        this.diagnosticSummary = diagnosticSummary;
        this.dataSufficient = dataSufficient;
        this.businessMetricsAllowed = businessMetricsAllowed;
        this.requiredWork = requiredWork == null ? List.of() : List.copyOf(requiredWork);
        this.sourceBatchIds = sourceBatchIds == null ? List.of() : List.copyOf(sourceBatchIds);
    }

    public NoonSalesSyncSurfaceState getState() {
        return state;
    }

    public NoonSalesCorrectionSurfaceState getCorrectionState() {
        return correctionState;
    }

    public LocalDate getLatestAvailableSalesDate() {
        return latestAvailableSalesDate;
    }

    public NoonSyncTaskStatus getTaskStatus() {
        return taskStatus;
    }

    public NoonSyncFailureReason getFailureReason() {
        return failureReason;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public boolean isDataSufficient() {
        return dataSufficient;
    }

    public boolean isBusinessMetricsAllowed() {
        return businessMetricsAllowed;
    }

    public List<NoonSyncRequiredWork> getRequiredWork() {
        return requiredWork;
    }

    public List<Long> getSourceBatchIds() {
        return sourceBatchIds;
    }
}
