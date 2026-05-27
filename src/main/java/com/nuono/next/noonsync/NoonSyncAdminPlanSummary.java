package com.nuono.next.noonsync;

public class NoonSyncAdminPlanSummary {

    private final long planId;
    private final String planKey;
    private final NoonSyncDataDomain dataDomain;
    private final NoonSyncTriggerMode triggerMode;
    private final boolean paused;
    private final String pauseReason;

    public NoonSyncAdminPlanSummary(NoonSyncPlan plan) {
        this.planId = plan.getId();
        this.planKey = plan.getDefinition().getKey();
        this.dataDomain = plan.getDefinition().getDataDomain();
        this.triggerMode = plan.getDefinition().getTriggerMode();
        this.paused = plan.isPaused();
        this.pauseReason = plan.getPauseReason();
    }

    public long getPlanId() {
        return planId;
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

    public boolean isPaused() {
        return paused;
    }

    public String getPauseReason() {
        return pauseReason;
    }
}
