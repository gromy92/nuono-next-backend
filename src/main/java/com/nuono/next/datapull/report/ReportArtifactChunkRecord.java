package com.nuono.next.datapull.report;

import java.time.LocalDateTime;

/** Persistence row for one fixed-size report artifact chunk. */
public final class ReportArtifactChunkRecord {
    private String artifactKey;
    private int chunkNo;
    private long byteOffset;
    private int contentLength;
    private String contentSha256;
    private byte[] contentBytes;
    private LocalDateTime createdAt;

    public String getArtifactKey() { return artifactKey; }
    public void setArtifactKey(String value) { artifactKey = value; }
    public int getChunkNo() { return chunkNo; }
    public void setChunkNo(int value) { chunkNo = value; }
    public long getByteOffset() { return byteOffset; }
    public void setByteOffset(long value) { byteOffset = value; }
    public int getContentLength() { return contentLength; }
    public void setContentLength(int value) { contentLength = value; }
    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String value) { contentSha256 = value; }
    public byte[] getContentBytes() { return contentBytes == null ? null : contentBytes.clone(); }
    public void setContentBytes(byte[] value) {
        contentBytes = value == null ? null : value.clone();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
