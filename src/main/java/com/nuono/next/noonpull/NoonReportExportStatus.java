package com.nuono.next.noonpull;

import java.util.Locale;
import org.springframework.util.StringUtils;

public class NoonReportExportStatus {
    private final String status;
    private final String downloadUrl;
    private final Integer totalRows;
    private final String message;

    private NoonReportExportStatus(String status, String downloadUrl, Integer totalRows, String message) {
        this.status = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "PENDING";
        this.downloadUrl = normalize(downloadUrl);
        this.totalRows = totalRows == null ? null : Math.max(0, totalRows);
        this.message = message;
    }

    public static NoonReportExportStatus ready(String downloadUrl) {
        return ready(downloadUrl, null);
    }

    public static NoonReportExportStatus ready(String downloadUrl, Integer totalRows) {
        return new NoonReportExportStatus("READY", downloadUrl, totalRows, null);
    }

    public static NoonReportExportStatus pending() {
        return pending("PENDING");
    }

    public static NoonReportExportStatus pending(String status) {
        return new NoonReportExportStatus(status, null, null, null);
    }

    public static NoonReportExportStatus failed(String message) {
        return new NoonReportExportStatus("FAILED", null, null, message);
    }

    public String getStatus() {
        return status;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public String getMessage() {
        return message;
    }

    public boolean isReady() {
        return "READY".equals(status) || "COMPLETE".equals(status) || "COMPLETED".equals(status);
    }

    public boolean isPending() {
        return "PENDING".equals(status)
                || "RUNNING".equals(status)
                || "PROCESSING".equals(status)
                || "IN_PROGRESS".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status)
                || "FAILURE".equals(status)
                || "ERROR".equals(status)
                || "CANCELLED".equals(status)
                || "CANCELED".equals(status);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
