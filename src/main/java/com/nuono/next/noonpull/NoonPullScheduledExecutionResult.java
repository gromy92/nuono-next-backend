package com.nuono.next.noonpull;

public class NoonPullScheduledExecutionResult {
    private int createdTaskCount;
    private int executedTaskCount;
    private int failedTaskCount;
    private int skippedTaskCount;
    private boolean enabled = true;

    public void created(int count) {
        createdTaskCount += Math.max(0, count);
    }

    public void executed() {
        executedTaskCount++;
    }

    public void failed() {
        failedTaskCount++;
    }

    public void skipped() {
        skippedTaskCount++;
    }

    public int getCreatedTaskCount() {
        return createdTaskCount;
    }

    public int getExecutedTaskCount() {
        return executedTaskCount;
    }

    public int getFailedTaskCount() {
        return failedTaskCount;
    }

    public int getSkippedTaskCount() {
        return skippedTaskCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
