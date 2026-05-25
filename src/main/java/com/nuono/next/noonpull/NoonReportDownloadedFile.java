package com.nuono.next.noonpull;

public class NoonReportDownloadedFile {
    private final NoonReportPullRequest request;
    private final String exportId;
    private final String sourceBatchId;
    private final String digestSha256;
    private final byte[] content;

    public NoonReportDownloadedFile(
            NoonReportPullRequest request,
            String exportId,
            String sourceBatchId,
            String digestSha256,
            byte[] content
    ) {
        this.request = request;
        this.exportId = exportId;
        this.sourceBatchId = sourceBatchId;
        this.digestSha256 = digestSha256;
        this.content = content == null ? new byte[0] : content.clone();
    }

    public NoonReportPullRequest getRequest() {
        return request;
    }

    public String getExportId() {
        return exportId;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public String getDigestSha256() {
        return digestSha256;
    }

    public byte[] getContent() {
        return content.clone();
    }
}
