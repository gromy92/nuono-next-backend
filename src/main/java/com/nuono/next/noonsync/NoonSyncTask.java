package com.nuono.next.noonsync;

public class NoonSyncTask {

    private final long id;
    private final NoonSyncDataDomain dataDomain;
    private final NoonSyncTriggerMode triggerMode;
    private final NoonSyncScope scope;
    private final NoonSyncTarget target;
    private final NoonSyncTaskStatus status;
    private final NoonSyncRetryPolicy retryPolicy;
    private final NoonSyncFailureReason failureReason;
    private final String diagnosticSummary;
    private final String providerTraceId;

    public NoonSyncTask(
            long id,
            NoonSyncDataDomain dataDomain,
            NoonSyncTriggerMode triggerMode,
            NoonSyncScope scope,
            NoonSyncTarget target,
            NoonSyncTaskStatus status,
            NoonSyncRetryPolicy retryPolicy
    ) {
        this(id, dataDomain, triggerMode, scope, target, status, retryPolicy, null, null, null);
    }

    private NoonSyncTask(
            long id,
            NoonSyncDataDomain dataDomain,
            NoonSyncTriggerMode triggerMode,
            NoonSyncScope scope,
            NoonSyncTarget target,
            NoonSyncTaskStatus status,
            NoonSyncRetryPolicy retryPolicy,
            NoonSyncFailureReason failureReason,
            String diagnosticSummary,
            String providerTraceId
    ) {
        this.id = id;
        this.dataDomain = dataDomain;
        this.triggerMode = triggerMode;
        this.scope = scope;
        this.target = target;
        this.status = status;
        this.retryPolicy = retryPolicy;
        this.failureReason = failureReason;
        this.diagnosticSummary = diagnosticSummary;
        this.providerTraceId = providerTraceId;
    }

    public long getId() {
        return id;
    }

    public NoonSyncDataDomain getDataDomain() {
        return dataDomain;
    }

    public NoonSyncTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public NoonSyncTarget getTarget() {
        return target;
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

    public boolean isRetryableFailure() {
        return status == NoonSyncTaskStatus.FAILED && retryPolicy == NoonSyncRetryPolicy.RETRYABLE;
    }

    NoonSyncTask withStatus(NoonSyncTaskStatus nextStatus, String diagnosticSummary) {
        return new NoonSyncTask(
                id,
                dataDomain,
                triggerMode,
                scope,
                target,
                nextStatus,
                retryPolicy,
                failureReason,
                diagnosticSummary,
                providerTraceId
        );
    }

    public NoonSyncTask partial(
            NoonSyncFailureReason failureReason,
            NoonSyncRetryPolicy retryPolicy,
            NoonSyncDiagnostic diagnostic
    ) {
        return new NoonSyncTask(
                id,
                dataDomain,
                triggerMode,
                scope,
                target,
                NoonSyncTaskStatus.PARTIAL,
                retryPolicy,
                failureReason,
                diagnostic == null ? null : diagnostic.getSafeSummary(),
                diagnostic == null ? null : diagnostic.getProviderTraceId()
        );
    }

    public NoonSyncTask failed(
            NoonSyncFailureReason failureReason,
            NoonSyncRetryPolicy retryPolicy,
            NoonSyncDiagnostic diagnostic
    ) {
        return new NoonSyncTask(
                id,
                dataDomain,
                triggerMode,
                scope,
                target,
                NoonSyncTaskStatus.FAILED,
                retryPolicy,
                failureReason,
                diagnostic == null ? null : diagnostic.getSafeSummary(),
                diagnostic == null ? null : diagnostic.getProviderTraceId()
        );
    }

    boolean isActive() {
        return status == NoonSyncTaskStatus.QUEUED || status == NoonSyncTaskStatus.RUNNING;
    }

    boolean hasSameLockTarget(NoonSyncTaskRequest request) {
        return dataDomain == request.getDataDomain()
                && scope.equals(request.getScope())
                && target.overlaps(request.getTarget());
    }
}
