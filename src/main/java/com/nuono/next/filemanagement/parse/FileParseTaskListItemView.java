package com.nuono.next.filemanagement.parse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FileParseTaskListItemView {

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
    private String dataScopeType;
    private String dataScopeKey;
    private Long documentGroupId;
    private Long parentTaskId;
    private Integer iterationNo;
    private Long resultId;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime nextRunAt;
    private Integer totalCount = 0;
    private Integer pendingCount = 0;
    private Integer needsFixCount = 0;
    private Integer hardErrorCount = 0;
    private Integer conflictCount = 0;
    private Integer deleteSuspectedCount = 0;
    private Integer confirmedCount = 0;
    private Integer rejectedCount = 0;
    private Integer keepOldCount = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private FileParseAvailableActions availableActions;
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
        this.iterationNo = iterationNo == null ? 1 : iterationNo;
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

    public LocalDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(LocalDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount == null ? 0 : totalCount;
    }

    public Integer getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(Integer pendingCount) {
        this.pendingCount = pendingCount == null ? 0 : pendingCount;
    }

    public Integer getNeedsFixCount() {
        return needsFixCount;
    }

    public void setNeedsFixCount(Integer needsFixCount) {
        this.needsFixCount = needsFixCount == null ? 0 : needsFixCount;
    }

    public Integer getHardErrorCount() {
        return hardErrorCount;
    }

    public void setHardErrorCount(Integer hardErrorCount) {
        this.hardErrorCount = hardErrorCount == null ? 0 : hardErrorCount;
    }

    public Integer getConflictCount() {
        return conflictCount;
    }

    public void setConflictCount(Integer conflictCount) {
        this.conflictCount = conflictCount == null ? 0 : conflictCount;
    }

    public Integer getDeleteSuspectedCount() {
        return deleteSuspectedCount;
    }

    public void setDeleteSuspectedCount(Integer deleteSuspectedCount) {
        this.deleteSuspectedCount = deleteSuspectedCount == null ? 0 : deleteSuspectedCount;
    }

    public Integer getConfirmedCount() {
        return confirmedCount;
    }

    public void setConfirmedCount(Integer confirmedCount) {
        this.confirmedCount = confirmedCount == null ? 0 : confirmedCount;
    }

    public Integer getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(Integer rejectedCount) {
        this.rejectedCount = rejectedCount == null ? 0 : rejectedCount;
    }

    public Integer getKeepOldCount() {
        return keepOldCount;
    }

    public void setKeepOldCount(Integer keepOldCount) {
        this.keepOldCount = keepOldCount == null ? 0 : keepOldCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public FileParseAvailableActions getAvailableActions() {
        return availableActions;
    }

    public void setAvailableActions(FileParseAvailableActions availableActions) {
        this.availableActions = availableActions;
    }

    public List<FileParseTaskInputView> getInputItems() {
        return inputItems;
    }

    public void setInputItems(List<FileParseTaskInputView> inputItems) {
        this.inputItems = inputItems == null ? new ArrayList<>() : inputItems;
    }
}
