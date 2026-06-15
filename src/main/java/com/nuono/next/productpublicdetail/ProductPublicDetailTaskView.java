package com.nuono.next.productpublicdetail;

import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.LocalDateTime;

public class ProductPublicDetailTaskView {
    private Long id;
    private String taskType;
    private String naturalKey;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private OperationalTaskStatus status;
    private Integer progressPercent;
    private String message;
    private String errorCode;
    private String resultJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;

    public static ProductPublicDetailTaskView from(OperationalTask task) {
        if (task == null) {
            return null;
        }
        ProductPublicDetailTaskView view = new ProductPublicDetailTaskView();
        view.id = task.getId();
        view.taskType = task.getTaskType();
        view.naturalKey = task.getNaturalKey();
        view.ownerUserId = task.getOwnerUserId();
        view.storeCode = task.getStoreCode();
        view.siteCode = task.getSiteCode();
        view.status = task.getStatus();
        view.progressPercent = task.getProgressPercent();
        view.message = task.getMessage();
        view.errorCode = task.getErrorCode();
        view.resultJson = task.getResultJson();
        view.startedAt = task.getStartedAt();
        view.finishedAt = task.getFinishedAt();
        view.updatedAt = task.getUpdatedAt();
        return view;
    }

    public Long getId() { return id; }
    public String getTaskType() { return taskType; }
    public String getNaturalKey() { return naturalKey; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public OperationalTaskStatus getStatus() { return status; }
    public Integer getProgressPercent() { return progressPercent; }
    public String getMessage() { return message; }
    public String getErrorCode() { return errorCode; }
    public String getResultJson() { return resultJson; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
