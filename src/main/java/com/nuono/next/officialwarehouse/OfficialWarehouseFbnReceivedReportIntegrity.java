package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.isComplete;

import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import org.springframework.util.StringUtils;

final class OfficialWarehouseFbnReceivedReportIntegrity {
    private OfficialWarehouseFbnReceivedReportIntegrity() {
    }

    static String validatedDownloadUrl(ExportStatus status, String requestedExportCode) {
        if (status == null) {
            throw retryProvider("fbn received export status is missing");
        }
        String providerExportCode = status.providerExportCode;
        if (!StringUtils.hasText(providerExportCode)
                || !requestedExportCode.equals(providerExportCode.trim())) {
            throw retryProvider("fbn received export code does not match the requested export");
        }
        if (!isComplete(status.status)) {
            throw retryReport("fbn received export is not complete");
        }
        requirePositiveProviderRows(status.totalRows);
        if (!StringUtils.hasText(status.downloadUrl)) {
            throw retryReport("fbn received export download URL is missing");
        }
        return status.downloadUrl.trim();
    }

    static void validateSourceRows(Integer providerTotalRows, int sourceDataRowCount) {
        requirePositiveProviderRows(providerTotalRows);
        if (sourceDataRowCount <= 0) {
            throw retryReport("fbn received report contains no source data rows");
        }
        if (providerTotalRows.intValue() != sourceDataRowCount) {
            throw retryProvider("fbn received provider and source row counts do not match");
        }
    }

    private static void requirePositiveProviderRows(Integer providerTotalRows) {
        if (providerTotalRows == null) {
            throw retryProvider("fbn received provider total rows are missing");
        }
        if (providerTotalRows <= 0) {
            throw retryReport("fbn received provider total rows are empty");
        }
    }

    private static IllegalArgumentException retryReport(String reason) {
        return new IllegalArgumentException("report not ready: " + reason);
    }

    private static IllegalArgumentException retryProvider(String reason) {
        return new IllegalArgumentException("provider unavailable: " + reason);
    }
}
