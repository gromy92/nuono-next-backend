package com.nuono.next.intransit.autosync;

import java.util.Collections;
import java.util.List;

public class LogisticsAutoSyncTaskSummary {
    private Long accountId;
    private Long ownerUserId;
    private Long taskId;
    private String sourceSystem;
    private String status;
    private String failureCode;
    private String failureMessage;
    private int batchCount;
    private int packageCount;
    private int lineCount;
    private int nodeCount;
    private int previewIssueCount;
    private List<PreviewIssueSummary> previewIssues = Collections.emptyList();

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public int getBatchCount() { return batchCount; }
    public void setBatchCount(int batchCount) { this.batchCount = batchCount; }
    public int getPackageCount() { return packageCount; }
    public void setPackageCount(int packageCount) { this.packageCount = packageCount; }
    public int getLineCount() { return lineCount; }
    public void setLineCount(int lineCount) { this.lineCount = lineCount; }
    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
    public int getPreviewIssueCount() { return previewIssueCount; }
    public void setPreviewIssueCount(int previewIssueCount) { this.previewIssueCount = previewIssueCount; }
    public List<PreviewIssueSummary> getPreviewIssues() { return previewIssues; }
    public void setPreviewIssues(List<PreviewIssueSummary> previewIssues) {
        this.previewIssues = previewIssues == null ? Collections.emptyList() : previewIssues;
    }

    public static class PreviewIssueSummary {
        private String level;
        private String batchNo;
        private String boxNo;
        private String psku;
        private String field;
        private String message;

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getBoxNo() { return boxNo; }
        public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
        public String getPsku() { return psku; }
        public void setPsku(String psku) { this.psku = psku; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
