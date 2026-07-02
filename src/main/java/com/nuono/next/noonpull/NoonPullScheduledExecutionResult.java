package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.List;

public class NoonPullScheduledExecutionResult {
    private int createdTaskCount;
    private int executedTaskCount;
    private int failedTaskCount;
    private int skippedTaskCount;
    private boolean enabled = true;
    private final List<NoonPullTaskRecord> taskOutcomes = new ArrayList<>();

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

    public void addTaskOutcome(NoonPullTaskRecord task) {
        if (task != null) {
            taskOutcomes.add(task.copy());
        }
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

    public List<NoonPullTaskRecord> getTaskOutcomes() {
        List<NoonPullTaskRecord> copy = new ArrayList<>();
        for (NoonPullTaskRecord task : taskOutcomes) {
            copy.add(task.copy());
        }
        return copy;
    }
}
