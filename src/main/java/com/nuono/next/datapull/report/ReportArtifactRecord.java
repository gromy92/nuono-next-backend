package com.nuono.next.datapull.report;

import java.time.LocalDateTime;

/** Persistence row for one restart-safe, content-verified report file. */
public final class ReportArtifactRecord {
    private String artifactKey;
    private long taskId;
    private String stableRequestKey;
    private String remoteHandle;
    private String contentSha256;
    private String databaseContentSha256;
    private long contentLength;
    private String downloadState;
    private int persistedChunkCount;
    private long downloadFenceEpoch;
    private long downloadedByteCount;
    private int downloadedChunkCount;
    private String resumableSha256State;
    private Long expectedContentLength;
    private String sourceValidator;
    private byte[] contentBytes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getArtifactKey() { return artifactKey; }
    public void setArtifactKey(String artifactKey) { this.artifactKey = artifactKey; }
    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }
    public String getStableRequestKey() { return stableRequestKey; }
    public void setStableRequestKey(String value) { this.stableRequestKey = value; }
    public String getRemoteHandle() { return remoteHandle; }
    public void setRemoteHandle(String remoteHandle) { this.remoteHandle = remoteHandle; }
    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String value) { this.contentSha256 = value; }
    public String getDatabaseContentSha256() { return databaseContentSha256; }
    public void setDatabaseContentSha256(String value) { this.databaseContentSha256 = value; }
    public long getContentLength() { return contentLength; }
    public void setContentLength(long contentLength) { this.contentLength = contentLength; }
    public String getDownloadState() { return downloadState; }
    public void setDownloadState(String value) { this.downloadState = value; }
    public int getPersistedChunkCount() { return persistedChunkCount; }
    public void setPersistedChunkCount(int value) { this.persistedChunkCount = value; }
    public long getDownloadFenceEpoch() { return downloadFenceEpoch; }
    public void setDownloadFenceEpoch(long value) { this.downloadFenceEpoch = value; }
    public long getDownloadedByteCount() { return downloadedByteCount; }
    public void setDownloadedByteCount(long value) { this.downloadedByteCount = value; }
    public int getDownloadedChunkCount() { return downloadedChunkCount; }
    public void setDownloadedChunkCount(int value) { this.downloadedChunkCount = value; }
    public String getResumableSha256State() { return resumableSha256State; }
    public void setResumableSha256State(String value) { this.resumableSha256State = value; }
    public Long getExpectedContentLength() { return expectedContentLength; }
    public void setExpectedContentLength(Long value) { this.expectedContentLength = value; }
    public String getSourceValidator() { return sourceValidator; }
    public void setSourceValidator(String value) { this.sourceValidator = value; }
    public byte[] getContentBytes() { return contentBytes == null ? null : contentBytes.clone(); }
    public void setContentBytes(byte[] value) { this.contentBytes = value == null ? null : value.clone(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
