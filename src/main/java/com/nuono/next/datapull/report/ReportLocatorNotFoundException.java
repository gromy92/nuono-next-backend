package com.nuono.next.datapull.report;

/** Signals a missing opaque reference so the state machine can safely re-poll the export. */
public final class ReportLocatorNotFoundException extends RuntimeException {
    public ReportLocatorNotFoundException() {
        super("REPORT_DOWNLOAD_LOCATOR_NOT_FOUND");
    }
}
