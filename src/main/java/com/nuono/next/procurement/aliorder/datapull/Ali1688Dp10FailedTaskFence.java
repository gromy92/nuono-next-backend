package com.nuono.next.procurement.aliorder.datapull;

/** Eligible FAILED task state locked before its cleanup marker is inspected. */
public class Ali1688Dp10FailedTaskFence {
    private Long taskId;
    private Long fenceEpoch;
    private String stepCode;
    private String checkpoint;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long fenceEpoch) { this.fenceEpoch = fenceEpoch; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public String getCheckpoint() { return checkpoint; }
    public void setCheckpoint(String checkpoint) { this.checkpoint = checkpoint; }
}
