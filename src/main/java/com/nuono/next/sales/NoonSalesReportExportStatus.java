package com.nuono.next.sales;

import java.util.Locale;
import org.springframework.util.StringUtils;

public class NoonSalesReportExportStatus {

    private final String exportCode;
    private final String status;
    private final String downloadUrl;
    private final int totalRows;
    private final String message;

    private NoonSalesReportExportStatus(
            String exportCode,
            String status,
            String downloadUrl,
            int totalRows,
            String message
    ) {
        this.exportCode = normalize(exportCode);
        this.status = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "PENDING";
        this.downloadUrl = normalize(downloadUrl);
        this.totalRows = Math.max(0, totalRows);
        this.message = normalize(message);
    }

    public static NoonSalesReportExportStatus pending(String exportCode, String status) {
        return new NoonSalesReportExportStatus(exportCode, status, null, 0, null);
    }

    public static NoonSalesReportExportStatus complete(String exportCode, String downloadUrl, int totalRows) {
        return new NoonSalesReportExportStatus(exportCode, "COMPLETE", downloadUrl, totalRows, null);
    }

    public static NoonSalesReportExportStatus failed(String exportCode, String status, String message) {
        return new NoonSalesReportExportStatus(exportCode, status, null, 0, message);
    }

    public String getExportCode() {
        return exportCode;
    }

    public String getStatus() {
        return status;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public String getMessage() {
        return message;
    }

    public boolean isComplete() {
        return "COMPLETE".equals(status) || "COMPLETED".equals(status);
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
