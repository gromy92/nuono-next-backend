package com.nuono.next.noonpull;

public class NoonReportProcessResult {
    public enum Code {
        SUCCEEDED,
        SUCCEEDED_WITH_BUSINESS_SKIPS,
        EMPTY_REPORT,
        EMPTY_REPORT_PENDING_CONFIRMATION,
        REPORT_NOT_READY,
        MISSING_COLUMNS,
        MAPPING_FAILED,
        PARTIAL_SUCCESS
    }

    private final Code code;
    private final int importedCount;
    private final int exceptionCount;
    private final String diagnosticMessage;

    private NoonReportProcessResult(Code code, int importedCount, int exceptionCount) {
        this(code, importedCount, exceptionCount, null);
    }

    private NoonReportProcessResult(Code code, int importedCount, int exceptionCount, String diagnosticMessage) {
        this.code = code;
        this.importedCount = importedCount;
        this.exceptionCount = exceptionCount;
        this.diagnosticMessage = diagnosticMessage;
    }

    public static NoonReportProcessResult succeeded(int importedCount, int exceptionCount) {
        if (exceptionCount != 0) {
            throw new IllegalArgumentException(
                    "Use succeededWithBusinessSkips for deterministic row skips"
            );
        }
        return new NoonReportProcessResult(Code.SUCCEEDED, importedCount, exceptionCount);
    }

    public static NoonReportProcessResult succeededWithBusinessSkips(
            int importedCount,
            int businessSkipCount
    ) {
        if (importedCount < 0) {
            throw new IllegalArgumentException("importedCount cannot be negative");
        }
        if (businessSkipCount <= 0) {
            throw new IllegalArgumentException("businessSkipCount must be positive");
        }
        return new NoonReportProcessResult(
                Code.SUCCEEDED_WITH_BUSINESS_SKIPS,
                importedCount,
                businessSkipCount
        );
    }

    public static NoonReportProcessResult emptyReport() {
        return new NoonReportProcessResult(Code.EMPTY_REPORT, 0, 0);
    }

    public static NoonReportProcessResult emptyReport(String diagnosticMessage) {
        return new NoonReportProcessResult(Code.EMPTY_REPORT, 0, 0, diagnosticMessage);
    }

    public static NoonReportProcessResult emptyReportPendingConfirmation() {
        return new NoonReportProcessResult(Code.EMPTY_REPORT_PENDING_CONFIRMATION, 0, 0);
    }

    public static NoonReportProcessResult emptyReportPendingConfirmation(String diagnosticMessage) {
        return new NoonReportProcessResult(Code.EMPTY_REPORT_PENDING_CONFIRMATION, 0, 0, diagnosticMessage);
    }

    public static NoonReportProcessResult reportNotReady() {
        return new NoonReportProcessResult(Code.REPORT_NOT_READY, 0, 0);
    }

    public static NoonReportProcessResult missingColumns() {
        return new NoonReportProcessResult(Code.MISSING_COLUMNS, 0, 0);
    }

    public static NoonReportProcessResult missingColumns(String diagnosticMessage) {
        return new NoonReportProcessResult(Code.MISSING_COLUMNS, 0, 0, diagnosticMessage);
    }

    public static NoonReportProcessResult mappingFailed(int exceptionCount) {
        return new NoonReportProcessResult(Code.MAPPING_FAILED, 0, exceptionCount);
    }

    public static NoonReportProcessResult mappingFailed(int exceptionCount, String diagnosticMessage) {
        return new NoonReportProcessResult(Code.MAPPING_FAILED, 0, exceptionCount, diagnosticMessage);
    }

    public static NoonReportProcessResult partialSuccess(int importedCount, int exceptionCount) {
        return new NoonReportProcessResult(Code.PARTIAL_SUCCESS, importedCount, exceptionCount);
    }

    public Code getCode() {
        return code;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public int getExceptionCount() {
        return exceptionCount;
    }

    public String getDiagnosticMessage() {
        return diagnosticMessage;
    }
}
