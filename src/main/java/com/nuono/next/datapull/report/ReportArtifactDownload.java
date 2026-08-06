package com.nuono.next.datapull.report;

import com.nuono.next.noon.NoonBinaryDownloadSink;

/** Restart-safe artifact sink whose chunks are durable before {@link #accept} returns. */
public interface ReportArtifactDownload extends NoonBinaryDownloadSink {

    default boolean isComplete() {
        return false;
    }

    DownloadedReportArtifact completedArtifact();
}
