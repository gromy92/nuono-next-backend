package com.nuono.next.noonpull;

public class NoonProductListInitializationResult {
    private NoonPullTaskStatus taskStatus;
    private String sourceBatchId;
    private String failureType;
    private int acceptedProductCount;

    public NoonPullTaskStatus getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(NoonPullTaskStatus taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public int getAcceptedProductCount() {
        return acceptedProductCount;
    }

    public void setAcceptedProductCount(int acceptedProductCount) {
        this.acceptedProductCount = acceptedProductCount;
    }
}
