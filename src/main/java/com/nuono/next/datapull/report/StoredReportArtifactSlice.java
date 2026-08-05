package com.nuono.next.datapull.report;

import java.util.Objects;

/** One verified, bounded byte range from a durable report artifact. */
public final class StoredReportArtifactSlice {
    private final String remoteHandle;
    private final long startByteOffset;
    private final long contentLength;
    private final byte[] content;

    public StoredReportArtifactSlice(
            String remoteHandle,
            long startByteOffset,
            long contentLength,
            byte[] content
    ) {
        this.remoteHandle = ReportContract.requireIdentity(remoteHandle, "remoteHandle");
        if (startByteOffset < 0L || contentLength < 0L || startByteOffset > contentLength) {
            throw new IllegalArgumentException("report artifact slice bounds are invalid");
        }
        this.content = Objects.requireNonNull(content, "content").clone();
        if (this.content.length > contentLength - startByteOffset) {
            throw new IllegalArgumentException("report artifact slice exceeds its container");
        }
        this.startByteOffset = startByteOffset;
        this.contentLength = contentLength;
    }

    public String getRemoteHandle() { return remoteHandle; }
    public long getStartByteOffset() { return startByteOffset; }
    public long getContentLength() { return contentLength; }
    public byte[] getContent() { return content.clone(); }

    public boolean isEndOfArtifact() {
        return startByteOffset + content.length == contentLength;
    }
}
