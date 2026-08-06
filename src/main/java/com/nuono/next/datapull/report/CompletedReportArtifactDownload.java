package com.nuono.next.datapull.report;

/** Already-complete sink returned when another fenced attempt won before transport starts. */
final class CompletedReportArtifactDownload implements ReportArtifactDownload {
    private final DownloadedReportArtifact artifact;

    CompletedReportArtifactDownload(DownloadedReportArtifact artifact) {
        this.artifact = artifact;
    }

    @Override public int preferredChunkBytes() { return MyBatisReportArtifactStore.CHUNK_BYTES; }
    @Override public long maximumBytes() { return MyBatisReportArtifactStore.MAX_ARTIFACT_BYTES; }
    @Override public boolean isComplete() { return true; }
    @Override public long resumeByteOffset() { return artifact.getContentLength(); }
    @Override public void accept(byte[] bytes, int offset, int length) {
        throw completedFailure();
    }
    @Override public void complete() { throw completedFailure(); }
    @Override public DownloadedReportArtifact completedArtifact() { return artifact; }

    private ReportArtifactContractException completedFailure() {
        return new ReportArtifactContractException("REPORT_ARTIFACT_DOWNLOAD_ALREADY_COMPLETE");
    }
}
