package com.nuono.next.datapull.report;

import java.time.LocalDateTime;

/** Encrypted locator row; raw signed URLs never enter task state or application logs. */
public final class ReportDownloadLocatorRecord {
    private String locatorReference;
    private long taskId;
    private String stableRequestKey;
    private String remoteHandleSha256;
    private byte[] initializationVector;
    private byte[] encryptedLocator;
    private LocalDateTime createdAt;

    public String getLocatorReference() { return locatorReference; }
    public void setLocatorReference(String value) { this.locatorReference = value; }
    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }
    public String getStableRequestKey() { return stableRequestKey; }
    public void setStableRequestKey(String value) { this.stableRequestKey = value; }
    public String getRemoteHandleSha256() { return remoteHandleSha256; }
    public void setRemoteHandleSha256(String value) { this.remoteHandleSha256 = value; }
    public byte[] getInitializationVector() {
        return initializationVector == null ? null : initializationVector.clone();
    }
    public void setInitializationVector(byte[] value) {
        this.initializationVector = value == null ? null : value.clone();
    }
    public byte[] getEncryptedLocator() {
        return encryptedLocator == null ? null : encryptedLocator.clone();
    }
    public void setEncryptedLocator(byte[] value) {
        this.encryptedLocator = value == null ? null : value.clone();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
