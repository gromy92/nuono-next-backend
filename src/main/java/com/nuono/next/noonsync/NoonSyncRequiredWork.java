package com.nuono.next.noonsync;

public class NoonSyncRequiredWork {

    private final String planKey;
    private final NoonSyncDataDomain dataDomain;
    private final NoonSyncTriggerMode triggerMode;
    private final NoonSyncTarget target;

    public NoonSyncRequiredWork(
            String planKey,
            NoonSyncDataDomain dataDomain,
            NoonSyncTriggerMode triggerMode,
            NoonSyncTarget target
    ) {
        this.planKey = planKey;
        this.dataDomain = dataDomain;
        this.triggerMode = triggerMode;
        this.target = target;
    }

    public String getPlanKey() {
        return planKey;
    }

    public NoonSyncDataDomain getDataDomain() {
        return dataDomain;
    }

    public NoonSyncTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public NoonSyncTarget getTarget() {
        return target;
    }
}
