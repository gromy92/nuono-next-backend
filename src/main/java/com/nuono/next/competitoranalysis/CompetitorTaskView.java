package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import java.time.LocalDateTime;

public class CompetitorTaskView {
    private Long taskId;
    private String taskType;
    private String naturalKey;
    private String status;
    private Integer progressPercent;
    private String message;
    private String resultJson;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;

    public static CompetitorTaskView from(OperationalTask task) {
        CompetitorTaskView view = new CompetitorTaskView();
        if (task == null) {
            return view;
        }
        view.setTaskId(task.getId());
        view.setTaskType(task.getTaskType());
        view.setNaturalKey(task.getNaturalKey());
        view.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        view.setProgressPercent(task.getProgressPercent());
        view.setMessage(task.getMessage());
        view.setResultJson(task.getResultJson());
        view.setErrorCode(task.getErrorCode());
        view.setStartedAt(task.getStartedAt());
        view.setFinishedAt(task.getFinishedAt());
        view.setUpdatedAt(task.getUpdatedAt());
        return view;
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getNaturalKey() { return naturalKey; }
    public void setNaturalKey(String naturalKey) { this.naturalKey = naturalKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
