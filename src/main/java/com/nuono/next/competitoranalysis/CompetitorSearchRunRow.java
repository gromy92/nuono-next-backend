package com.nuono.next.competitoranalysis;

import java.time.LocalDateTime;

public class CompetitorSearchRunRow {
    private Long id;
    private Long watchProductId;
    private Long taskId;
    private String triggerMode;
    private String status;
    private Long requestedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer keywordTotal;
    private Integer keywordSuccess;
    private Integer keywordFailed;
    private Integer candidateUpsertedCount;
    private Integer rankFactWrittenCount;
    private String errorCode;
    private String errorMessage;

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
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Integer getKeywordTotal() { return keywordTotal; }
    public void setKeywordTotal(Integer keywordTotal) { this.keywordTotal = keywordTotal; }
    public Integer getKeywordSuccess() { return keywordSuccess; }
    public void setKeywordSuccess(Integer keywordSuccess) { this.keywordSuccess = keywordSuccess; }
    public Integer getKeywordFailed() { return keywordFailed; }
    public void setKeywordFailed(Integer keywordFailed) { this.keywordFailed = keywordFailed; }
    public Integer getCandidateUpsertedCount() { return candidateUpsertedCount; }
    public void setCandidateUpsertedCount(Integer candidateUpsertedCount) { this.candidateUpsertedCount = candidateUpsertedCount; }
    public Integer getRankFactWrittenCount() { return rankFactWrittenCount; }
    public void setRankFactWrittenCount(Integer rankFactWrittenCount) { this.rankFactWrittenCount = rankFactWrittenCount; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
