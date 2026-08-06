package com.nuono.next.datapull.report;

/** Encrypts signed download locations and exposes only restart-safe opaque references. */
public interface ReportDownloadLocatorVault {
    String store(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String rawLocator
    );

    String resolve(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String locatorReference
    );
}
