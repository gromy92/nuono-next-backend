package com.nuono.next.noonpull;

import java.util.Locale;
import org.springframework.util.StringUtils;

public class NoonReportExportStatus {
    private final String status;
    private final String downloadUrl;
    private final Integer totalRows;
    private final String providerExportId;
    private final String message;

    private NoonReportExportStatus(
            String status,
            String downloadUrl,
            Integer totalRows,
            String providerExportId,
            String message
    ) {
        this.status = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "PENDING";
        this.downloadUrl = normalize(downloadUrl);
        if (totalRows != null && totalRows < 0) {
            throw new IllegalArgumentException("report totalRows must not be negative");
        }
        this.totalRows = totalRows;
        this.providerExportId = normalize(providerExportId);
        this.message = message;
    }

    public static NoonReportExportStatus ready(String downloadUrl) {
        return ready(downloadUrl, null);
    }

    public static NoonReportExportStatus ready(String downloadUrl, Integer totalRows) {
        return new NoonReportExportStatus("READY", downloadUrl, totalRows, null, null);
    }

    public static NoonReportExportStatus readyForProviderExport(
            String providerExportId,
            String downloadUrl,
            Integer totalRows
    ) {
        if (!StringUtils.hasText(providerExportId)) {
            throw new IllegalArgumentException("providerExportId is required");
        }
        return new NoonReportExportStatus(
                "READY",
                downloadUrl,
                totalRows,
                providerExportId,
                null
        );
    }

    public static NoonReportExportStatus pending() {
        return pending("PENDING");
    }

    public static NoonReportExportStatus pending(String status) {
        return new NoonReportExportStatus(status, null, null, null, null);
    }

    public static NoonReportExportStatus failed(String message) {
        return new NoonReportExportStatus("FAILED", null, null, null, message);
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

    public String getProviderExportId() {
        return providerExportId;
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
