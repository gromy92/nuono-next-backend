package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;

public class CompetitorRefreshRunView {
    private Long taskId;
    private Long runId;
    private Long watchProductId;
    private String taskStatus;
    private String runStatus;
    private Integer progressPercent;
    private String message;
    private String errorCode;
    private String errorMessage;
    private Integer keywordTotal;
    private Integer keywordSuccess;
    private Integer keywordFailed;

    public static CompetitorRefreshRunView from(OperationalTask task, CompetitorSearchRunRow run) {
        CompetitorRefreshRunView view = new CompetitorRefreshRunView();
        if (task != null) {
            view.setTaskId(task.getId());
            view.setTaskStatus(task.getStatus() == null ? null : task.getStatus().name());
            view.setProgressPercent(task.getProgressPercent());
            view.setMessage(task.getMessage());
            view.setErrorCode(task.getErrorCode());
        }
        if (run != null) {
            view.setRunId(run.getId());
            view.setWatchProductId(run.getWatchProductId());
            view.setRunStatus(run.getStatus());
            view.setErrorCode(view.getErrorCode() == null ? run.getErrorCode() : view.getErrorCode());
            view.setErrorMessage(run.getErrorMessage());
            view.setKeywordTotal(nullToZero(run.getKeywordTotal()));
            view.setKeywordSuccess(nullToZero(run.getKeywordSuccess()));
            view.setKeywordFailed(nullToZero(run.getKeywordFailed()));
        }
        return view;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getKeywordTotal() { return keywordTotal; }
    public void setKeywordTotal(Integer keywordTotal) { this.keywordTotal = keywordTotal; }
    public Integer getKeywordSuccess() { return keywordSuccess; }
    public void setKeywordSuccess(Integer keywordSuccess) { this.keywordSuccess = keywordSuccess; }
    public Integer getKeywordFailed() { return keywordFailed; }
    public void setKeywordFailed(Integer keywordFailed) { this.keywordFailed = keywordFailed; }
}
