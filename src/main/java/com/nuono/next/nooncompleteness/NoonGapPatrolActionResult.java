package com.nuono.next.nooncompleteness;

import java.util.ArrayList;
import java.util.List;

public class NoonGapPatrolActionResult {
    private Long gapId;
    private NoonDataGapStatus status;
    private int plannedTaskCount;
    private int executedTaskCount;
    private int failedTaskCount;
    private int skippedTaskCount;
    private List<Long> plannedTaskIds = new ArrayList<>();
    private String message;

    public Long getGapId() {
        return gapId;
    }

    public void setGapId(Long gapId) {
        this.gapId = gapId;
    }

    public NoonDataGapStatus getStatus() {
        return status;
    }

    public void setStatus(NoonDataGapStatus status) {
        this.status = status;
    }

    public int getPlannedTaskCount() {
        return plannedTaskCount;
    }

    public void setPlannedTaskCount(int plannedTaskCount) {
        this.plannedTaskCount = plannedTaskCount;
    }

    public List<Long> getPlannedTaskIds() {
        return plannedTaskIds;
    }

    public void setPlannedTaskIds(List<Long> plannedTaskIds) {
        this.plannedTaskIds = plannedTaskIds == null ? new ArrayList<>() : plannedTaskIds;
    }

    public int getExecutedTaskCount() {
        return executedTaskCount;
    }

    public void setExecutedTaskCount(int executedTaskCount) {
        this.executedTaskCount = Math.max(executedTaskCount, 0);
    }

    public int getFailedTaskCount() {
        return failedTaskCount;
    }

    public void setFailedTaskCount(int failedTaskCount) {
        this.failedTaskCount = Math.max(failedTaskCount, 0);
    }

    public int getSkippedTaskCount() {
        return skippedTaskCount;
    }

    public void setSkippedTaskCount(int skippedTaskCount) {
        this.skippedTaskCount = Math.max(skippedTaskCount, 0);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
