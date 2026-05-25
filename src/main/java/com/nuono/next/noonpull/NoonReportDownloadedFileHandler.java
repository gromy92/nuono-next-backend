package com.nuono.next.noonpull;

@FunctionalInterface
public interface NoonReportDownloadedFileHandler {
    NoonReportProcessResult handle(NoonReportDownloadedFile file);
}
