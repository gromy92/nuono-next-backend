package com.nuono.next.noonpull;

public class NoonProductDetailBaselineSyncResult {
    private int attemptedCount;
    private int succeededCount;
    private int failedCount;
    private int skippedReadyCount;
    private int totalProductCount;
    private int completedCount;
    private int remainingCount;
    private String nextResumePosition;
    private String diagnosticSummary;
    private String failureMessage;

    public int getAttemptedCount() {
        return attemptedCount;
    }

    public void setAttemptedCount(int attemptedCount) {
        this.attemptedCount = Math.max(attemptedCount, 0);
    }

    public int getSucceededCount() {
        return succeededCount;
    }

    public void setSucceededCount(int succeededCount) {
        this.succeededCount = Math.max(succeededCount, 0);
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = Math.max(failedCount, 0);
    }

    public int getSkippedReadyCount() {
        return skippedReadyCount;
    }

    public void setSkippedReadyCount(int skippedReadyCount) {
        this.skippedReadyCount = Math.max(skippedReadyCount, 0);
    }

    public int getTotalProductCount() {
        return totalProductCount;
    }

    public void setTotalProductCount(int totalProductCount) {
        this.totalProductCount = Math.max(totalProductCount, 0);
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(int completedCount) {
        this.completedCount = Math.max(completedCount, 0);
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(int remainingCount) {
        this.remainingCount = Math.max(remainingCount, 0);
    }

    public String getNextResumePosition() {
        return nextResumePosition;
    }

    public void setNextResumePosition(String nextResumePosition) {
        this.nextResumePosition = nextResumePosition;
    }

    public String getDiagnosticSummary() {
        return diagnosticSummary;
    }

    public void setDiagnosticSummary(String diagnosticSummary) {
        this.diagnosticSummary = diagnosticSummary;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public boolean isSucceeded() {
        return remainingCount == 0 && failedCount == 0 && (succeededCount > 0 || skippedReadyCount > 0);
    }

    public boolean isPartial() {
        return succeededCount > 0 && (remainingCount > 0 || failedCount > 0);
    }
}
