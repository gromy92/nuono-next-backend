package com.nuono.next.filemanagement.parse;

public class FileParseValidationIssueView {

    private Long id;
    private Long taskId;
    private Long resultId;
    private Long resultItemId;
    private Long sourceRowId;
    private Long aiChunkId;
    private String issueType;
    private String severity;
    private String fieldKey;
    private String message;
    private String detailsJson;
    private String resolvedStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public Long getResultItemId() {
        return resultItemId;
    }

    public void setResultItemId(Long resultItemId) {
        this.resultItemId = resultItemId;
    }

    public Long getSourceRowId() {
        return sourceRowId;
    }

    public void setSourceRowId(Long sourceRowId) {
        this.sourceRowId = sourceRowId;
    }

    public Long getAiChunkId() {
        return aiChunkId;
    }

    public void setAiChunkId(Long aiChunkId) {
        this.aiChunkId = aiChunkId;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public String getResolvedStatus() {
        return resolvedStatus;
    }

    public void setResolvedStatus(String resolvedStatus) {
        this.resolvedStatus = resolvedStatus;
    }
}
