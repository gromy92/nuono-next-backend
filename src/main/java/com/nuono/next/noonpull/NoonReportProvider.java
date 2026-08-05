package com.nuono.next.noonpull;

import com.nuono.next.noon.NoonBinaryDownloadSink;

public interface NoonReportProvider {
    String createExport(NoonReportPullRequest request);

    NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId);

    byte[] download(NoonReportPullRequest request, String downloadUrl);

    /** Runtime streaming seam; legacy providers retain the bounded compatibility fallback. */
    default void download(
            NoonReportPullRequest request,
            String downloadUrl,
            NoonBinaryDownloadSink sink
    ) {
        try {
            byte[] content = download(request, downloadUrl);
            sink.accept(content, 0, content.length);
            sink.complete();
        } catch (RuntimeException failure) {
            sink.abort(failure);
            throw failure;
        }
    }
}
