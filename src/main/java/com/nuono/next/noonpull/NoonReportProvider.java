package com.nuono.next.noonpull;

public interface NoonReportProvider {
    String createExport(NoonReportPullRequest request);

    NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId);

    byte[] download(NoonReportPullRequest request, String downloadUrl);
}
