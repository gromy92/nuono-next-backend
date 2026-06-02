package com.nuono.next.sales;

public interface NoonSalesReportProvider {

    default NoonSalesReportPayload fetch(NoonSalesReportRequest request) {
        NoonSalesReportExportStatus created = createExport(request);
        NoonSalesReportExportStatus status = pollExport(request, created.getExportCode());
        if (!status.isComplete()) {
            throw new IllegalStateException("Noon sales report export is not ready: " + status.getStatus());
        }
        return download(request, status);
    }

    default NoonSalesReportExportStatus createExport(NoonSalesReportRequest request) {
        throw new IllegalStateException("Noon sales report provider is not configured for automatic sync.");
    }

    default NoonSalesReportExportStatus pollExport(NoonSalesReportRequest request, String exportCode) {
        throw new IllegalStateException("Noon sales report provider is not configured for automatic sync.");
    }

    default NoonSalesReportPayload download(NoonSalesReportRequest request, NoonSalesReportExportStatus status) {
        throw new IllegalStateException("Noon sales report provider is not configured for automatic sync.");
    }

    default int maxPollAttempts() {
        return 1;
    }

    default void waitBeforeNextPoll() {
    }
}
