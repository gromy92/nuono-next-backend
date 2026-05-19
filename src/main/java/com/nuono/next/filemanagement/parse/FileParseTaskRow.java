package com.nuono.next.filemanagement.parse;

import java.time.LocalDateTime;

public class FileParseTaskRow {

    private Long id;
    private String taskNo;
    private String documentTitle;
    private Long targetPlanId;
    private Long standardVersionId;
    private String dataScopeType;
    private String dataScopeKey;
    private String status;
    private Long baseVersionId;
    private Long documentGroupId;
    private Long parentTaskId;
    private Integer iterationNo;
    private Long currentResultId;
    private String failureCode;
    private String failureMessage;
    private Integer parseAttemptCount;
    private LocalDateTime nextRunAt;
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public void setTargetPlanId(Long targetPlanId) {
        this.targetPlanId = targetPlanId;
    }

    public Long getStandardVersionId() {
        return standardVersionId;
    }

    public void setStandardVersionId(Long standardVersionId) {
        this.standardVersionId = standardVersionId;
    }

    public String getDataScopeType() {
        return dataScopeType;
    }

    public void setDataScopeType(String dataScopeType) {
        this.dataScopeType = dataScopeType;
    }

    public String getDataScopeKey() {
        return dataScopeKey;
    }

    public void setDataScopeKey(String dataScopeKey) {
        this.dataScopeKey = dataScopeKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getBaseVersionId() {
        return baseVersionId;
    }

    public void setBaseVersionId(Long baseVersionId) {
        this.baseVersionId = baseVersionId;
    }

    public Long getDocumentGroupId() {
        return documentGroupId;
    }

    public void setDocumentGroupId(Long documentGroupId) {
        this.documentGroupId = documentGroupId;
    }

    public Long getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(Long parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public Integer getIterationNo() {
        return iterationNo;
    }

    public void setIterationNo(Integer iterationNo) {
        this.iterationNo = iterationNo;
    }

    public Long getCurrentResultId() {
        return currentResultId;
    }

    public void setCurrentResultId(Long currentResultId) {
        this.currentResultId = currentResultId;
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

    public Integer getParseAttemptCount() {
        return parseAttemptCount;
    }

    public void setParseAttemptCount(Integer parseAttemptCount) {
        this.parseAttemptCount = parseAttemptCount;
    }

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
