package com.nuono.next.noonpull;

public class NoonProductInitializationStatusView {
    private String state;
    private String taskStatus;
    private String failureType;
    private String retryAction;
    private Boolean retryable;
    private Boolean requiresManualAction;
    private String sourceBatchId;
    private String diagnosticSummary;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getRetryAction() {
        return retryAction;
    }

    public void setRetryAction(String retryAction) {
        this.retryAction = retryAction;
    }

    public Boolean getRetryable() {
        return retryable;
    }

    public void setRetryable(Boolean retryable) {
        this.retryable = retryable;
    }

    public Boolean getRequiresManualAction() {
        return requiresManualAction;
    }

    public void setRequiresManualAction(Boolean requiresManualAction) {
        this.requiresManualAction = requiresManualAction;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public void setDiagnosticSummary(String diagnosticSummary) {
        this.diagnosticSummary = diagnosticSummary;
    }
}
