package com.nuono.next.noonsync;

public class NoonSyncAdminHealthSummary {

    private final int activeTaskCount;
    private final int failedTaskCount;
    private final int retryableFailureCount;
    private final int pausedPlanCount;

    public NoonSyncAdminHealthSummary(
            int activeTaskCount,
            int failedTaskCount,
            int retryableFailureCount,
            int pausedPlanCount
    ) {
        this.activeTaskCount = activeTaskCount;
        this.failedTaskCount = failedTaskCount;
        this.retryableFailureCount = retryableFailureCount;
        this.pausedPlanCount = pausedPlanCount;
    }

    public int getActiveTaskCount() {
        return activeTaskCount;
    }

    public int getFailedTaskCount() {
        return failedTaskCount;
    }

    public int getRetryableFailureCount() {
        return retryableFailureCount;
    }

    public int getPausedPlanCount() {
        return pausedPlanCount;
    }
}
