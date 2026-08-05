package com.nuono.next.datapull.snapshot;

/** Current runtime-task fence selected under a database row lock. */
public final class SnapshotStageTaskRow {
    private Long taskId;
    private Long fenceEpoch;
    private String state;
    private Boolean leaseValid;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getFenceEpoch() {
        return fenceEpoch;
    }

    public void setFenceEpoch(Long fenceEpoch) {
        this.fenceEpoch = fenceEpoch;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Boolean getLeaseValid() {
        return leaseValid;
    }

    public void setLeaseValid(Boolean leaseValid) {
        this.leaseValid = leaseValid;
    }
}
