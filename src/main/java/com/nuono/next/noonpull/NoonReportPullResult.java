package com.nuono.next.noonpull;

public class NoonReportPullResult {
    private NoonPullTaskStatus status;
    private String sourceBatchId;
    private String fileDigestSha256;
    private int importedCount;
    private int exceptionCount;

    public NoonPullTaskStatus getStatus() {
        return status;
    }

    public void setStatus(NoonPullTaskStatus status) {
        this.status = status;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public String getFileDigestSha256() {
        return fileDigestSha256;
    }

    public void setFileDigestSha256(String fileDigestSha256) {
        this.fileDigestSha256 = fileDigestSha256;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public int getExceptionCount() {
        return exceptionCount;
    }

    public void setExceptionCount(int exceptionCount) {
        this.exceptionCount = exceptionCount;
    }
}
