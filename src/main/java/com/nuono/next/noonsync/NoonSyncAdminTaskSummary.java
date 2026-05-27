package com.nuono.next.noonsync;

public class NoonSyncAdminTaskSummary {

    private final long taskId;
    private final NoonSyncDataDomain dataDomain;
    private final NoonSyncTriggerMode triggerMode;
    private final NoonSyncTaskStatus status;
    private final NoonSyncRetryPolicy retryPolicy;
    private final NoonSyncFailureReason failureReason;
    private final String diagnosticSummary;
    private final String providerTraceId;
    private final NoonSyncTarget target;

    public NoonSyncAdminTaskSummary(NoonSyncTask task) {
        this.taskId = task.getId();
        this.dataDomain = task.getDataDomain();
        this.triggerMode = task.getTriggerMode();
        this.status = task.getStatus();
        this.retryPolicy = task.getRetryPolicy();
        this.failureReason = task.getFailureReason();
        this.diagnosticSummary = task.getDiagnosticSummary();
        this.providerTraceId = task.getProviderTraceId();
        this.target = task.getTarget();
    }

    public long getTaskId() {
        return taskId;
    }

    public NoonSyncDataDomain getDataDomain() {
        return dataDomain;
    }

    public NoonSyncTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public NoonSyncTaskStatus getStatus() {
        return status;
    }

    public NoonSyncRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public NoonSyncFailureReason getFailureReason() {
        return failureReason;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public String getProviderTraceId() {
        return providerTraceId;
    }

    public NoonSyncTarget getTarget() {
        return target;
    }
}
