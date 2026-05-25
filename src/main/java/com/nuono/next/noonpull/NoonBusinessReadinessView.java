package com.nuono.next.noonpull;

public class NoonBusinessReadinessView {
    private NoonPullDataDomain dataDomain;
    private String qualityState;
    private boolean metricsAllowed;
    private String taskStatus;
    private String failureType;
    private String sourceBatchId;

    public NoonPullDataDomain getDataDomain() {
        return dataDomain;
    }

    public void setDataDomain(NoonPullDataDomain dataDomain) {
        this.dataDomain = dataDomain;
    }

    public String getQualityState() {
        return qualityState;
    }

    public void setQualityState(String qualityState) {
        this.qualityState = qualityState;
    }

    public boolean isMetricsAllowed() {
        return metricsAllowed;
    }

    public void setMetricsAllowed(boolean metricsAllowed) {
        this.metricsAllowed = metricsAllowed;
    }

    public boolean hasConfirmedZeroMetrics() {
        return false;
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

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }
}
