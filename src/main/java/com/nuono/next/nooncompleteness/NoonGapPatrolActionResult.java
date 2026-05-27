package com.nuono.next.nooncompleteness;

import java.util.ArrayList;
import java.util.List;

public class NoonGapPatrolActionResult {
    private Long gapId;
    private NoonDataGapStatus status;
    private int plannedTaskCount;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
