package com.nuono.next.datapull.report;

import com.nuono.next.noon.NoonBinaryDownloadSink;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;

/** Streaming transport seam kept separate from the legacy FBN provider. */
@FunctionalInterface
public interface FbnReportDownloadTransport {
    void download(PullRequest request, String downloadUrl, NoonBinaryDownloadSink sink);
}
