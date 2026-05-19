package com.nuono.next.product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProductPublishTaskView {

    private Long taskId;
    private String status;
    private String message;
    private List<String> changedDomains = new ArrayList<>();
    private Integer retryCount;
    private Integer verifyAttemptCount;
    private LocalDateTime nextRunAt;
    private LocalDateTime finishedAt;
    private Long pollAfterMillis;
    private ProductMasterWorkbenchView workbench;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getChangedDomains() {
        return changedDomains;
    }

    public void setChangedDomains(List<String> changedDomains) {
        this.changedDomains = changedDomains;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getVerifyAttemptCount() {
        return verifyAttemptCount;
    }

    public void setVerifyAttemptCount(Integer verifyAttemptCount) {
        this.verifyAttemptCount = verifyAttemptCount;
    }

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getPollAfterMillis() {
        return pollAfterMillis;
    }

    public void setPollAfterMillis(Long pollAfterMillis) {
        this.pollAfterMillis = pollAfterMillis;
    }

    public ProductMasterWorkbenchView getWorkbench() {
        return workbench;
    }

    public void setWorkbench(ProductMasterWorkbenchView workbench) {
        this.workbench = workbench;
    }
}
