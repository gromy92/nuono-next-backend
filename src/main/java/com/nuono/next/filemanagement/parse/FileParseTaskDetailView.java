package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseTaskDetailView {

    private Long id;
    private String taskNo;
    private String documentTitle;
    private Long targetPlanId;
    private String targetPlanCode;
    private String targetPlanLabel;
    private String documentType;
    private String documentName;
    private String standardVersion;
    private String currentVersion;
    private String status;
    private Long resultId;
    private String failureCode;
    private String failureMessage;
    private java.time.LocalDateTime nextRunAt;
    private String dataScopeType;
    private String dataScopeKey;
    private Long documentGroupId;
    private Long parentTaskId;
    private Integer iterationNo;
    private String remark;
    private String message;
    private List<FileParseTaskInputView> inputItems = new ArrayList<>();

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

    public String getTargetPlanCode() {
        return targetPlanCode;
    }

    public void setTargetPlanCode(String targetPlanCode) {
        this.targetPlanCode = targetPlanCode;
    }

    public String getTargetPlanLabel() {
        return targetPlanLabel;
    }

    public void setTargetPlanLabel(String targetPlanLabel) {
        this.targetPlanLabel = targetPlanLabel;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getStandardVersion() {
        return standardVersion;
    }

    public void setStandardVersion(String standardVersion) {
        this.standardVersion = standardVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
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

    public java.time.LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(java.time.LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<FileParseTaskInputView> getInputItems() {
        return inputItems;
    }

    public void setInputItems(List<FileParseTaskInputView> inputItems) {
        this.inputItems = inputItems == null ? new ArrayList<>() : inputItems;
    }
}
