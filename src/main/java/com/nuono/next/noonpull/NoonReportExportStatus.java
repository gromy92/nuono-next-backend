package com.nuono.next.noonpull;

public class NoonReportExportStatus {
    private final String status;
    private final String downloadUrl;
    private final String message;

    private NoonReportExportStatus(String status, String downloadUrl, String message) {
        this.status = status;
        this.downloadUrl = downloadUrl;
        this.message = message;
    }

    public static NoonReportExportStatus ready(String downloadUrl) {
        return new NoonReportExportStatus("READY", downloadUrl, null);
    }

    public static NoonReportExportStatus pending() {
        return new NoonReportExportStatus("PENDING", null, null);
    }

    public static NoonReportExportStatus failed(String message) {
        return new NoonReportExportStatus("FAILED", null, message);
    }

    public String getStatus() {
        return status;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getMessage() {
        return message;
    }

    public boolean isReady() {
        return "READY".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
