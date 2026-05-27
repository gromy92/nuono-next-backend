package com.nuono.next.noonsync;

import java.util.List;

public class NoonProductSyncReadModel {

    private final NoonProductSyncSurfaceState surfaceState;
    private final NoonSyncTaskStatus taskStatus;
    private final NoonSyncFailureReason failureReason;
    private final String diagnosticSummary;
    private final List<NoonSyncRequiredWork> requiredWork;
    private final boolean externalWriteAllowed;

    public NoonProductSyncReadModel(
            NoonProductSyncSurfaceState surfaceState,
            NoonSyncTaskStatus taskStatus,
            NoonSyncFailureReason failureReason,
            String diagnosticSummary,
            List<NoonSyncRequiredWork> requiredWork,
            boolean externalWriteAllowed
    ) {
        this.surfaceState = surfaceState;
        this.taskStatus = taskStatus;
        this.failureReason = failureReason;
        this.diagnosticSummary = diagnosticSummary;
        this.requiredWork = requiredWork == null ? List.of() : List.copyOf(requiredWork);
        this.externalWriteAllowed = externalWriteAllowed;
    }

    public NoonProductSyncSurfaceState getSurfaceState() {
        return surfaceState;
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

    public List<NoonSyncRequiredWork> getRequiredWork() {
        return requiredWork;
    }

    public boolean isExternalWriteAllowed() {
        return externalWriteAllowed;
    }
}
