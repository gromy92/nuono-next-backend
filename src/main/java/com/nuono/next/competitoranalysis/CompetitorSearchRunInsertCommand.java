package com.nuono.next.competitoranalysis;

public class CompetitorSearchRunInsertCommand {
    private Long id;
    private Long watchProductId;
    private Long taskId;
    private String triggerMode;
    private String status;
    private Long requestedBy;
    private Integer keywordTotal;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTriggerMode() { return triggerMode; }
    public void setTriggerMode(String triggerMode) { this.triggerMode = triggerMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public Integer getKeywordTotal() { return keywordTotal; }
    public void setKeywordTotal(Integer keywordTotal) { this.keywordTotal = keywordTotal; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
