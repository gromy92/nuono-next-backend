package com.nuono.next.datapull.report;

import java.util.Objects;

/** Verified artifact bytes plus the source export identity bound at download time. */
public final class StoredReportArtifact {
    private final String remoteHandle;
    private final byte[] content;

    public StoredReportArtifact(String remoteHandle, byte[] content) {
        this.remoteHandle = ReportContract.requireIdentity(remoteHandle, "remoteHandle");
        this.content = Objects.requireNonNull(content, "content").clone();
    }

    public String getRemoteHandle() { return remoteHandle; }
    public byte[] getContent() { return content.clone(); }
}
