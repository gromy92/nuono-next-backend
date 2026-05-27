package com.nuono.next.noonsync;

public class NoonSyncTaskRequest {

    private final NoonSyncDataDomain dataDomain;
    private final NoonSyncTriggerMode triggerMode;
    private final NoonSyncScope scope;
    private final NoonSyncTarget target;
    private final NoonSyncRetryPolicy retryPolicy;

    public NoonSyncTaskRequest(
            NoonSyncDataDomain dataDomain,
            NoonSyncTriggerMode triggerMode,
            NoonSyncScope scope,
            NoonSyncTarget target,
            NoonSyncRetryPolicy retryPolicy
    ) {
        this.dataDomain = dataDomain;
        this.triggerMode = triggerMode;
        this.scope = scope;
        this.target = target;
        this.retryPolicy = retryPolicy;
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

    public NoonSyncRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
}
