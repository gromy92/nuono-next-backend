package com.nuono.next.procurement.aliorder.datapull;

/** Oldest exact FAILED DP-10 generation selected for one bounded retention advance. */
public class Ali1688Dp10FailedStageCandidate {
    private Long taskId;
    private Long generationNo;
    private Boolean markerCandidate;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getGenerationNo() { return generationNo; }
    public void setGenerationNo(Long generationNo) { this.generationNo = generationNo; }
    public Boolean getMarkerCandidate() { return markerCandidate; }
    public void setMarkerCandidate(Boolean markerCandidate) {
        this.markerCandidate = markerCandidate;
    }
}
