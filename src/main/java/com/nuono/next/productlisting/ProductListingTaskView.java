package com.nuono.next.productlisting;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProductListingTaskView {

    private Long taskId;
    private String taskNo;
    private Long draftId;
    private Long ownerUserId;
    private String storeCode;
    private String mode;
    private String status;
    private List<ProductListingValidationIssue> validationIssues = new ArrayList<>();
    private String failureCode;
    private String failureMessage;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ProductListingValidationIssue> getValidationIssues() {
        return validationIssues;
    }

    public void setValidationIssues(List<ProductListingValidationIssue> validationIssues) {
        this.validationIssues = validationIssues == null ? new ArrayList<>() : validationIssues;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
