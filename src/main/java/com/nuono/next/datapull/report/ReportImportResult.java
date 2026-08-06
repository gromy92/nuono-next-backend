package com.nuono.next.datapull.report;

/** Transactional result of validating and importing one complete report artifact. */
public final class ReportImportResult {

    public enum Status {
        IN_PROGRESS,
        APPLIED,
        AWAITING_AUTHORITATIVE_EMPTY_PROOF,
        STALE_FENCE,
        CONTRACT_ERROR
    }

    private final Status status;
    private final String sanitizedCode;

    private ReportImportResult(Status status, String sanitizedCode) {
        this.status = status;
        this.sanitizedCode = sanitizedCode;
    }

    public static ReportImportResult applied() {
        return new ReportImportResult(Status.APPLIED, "REPORT_APPLIED");
    }

    public static ReportImportResult inProgress() {
        return new ReportImportResult(Status.IN_PROGRESS, "REPORT_APPLY_IN_PROGRESS");
    }

    public static ReportImportResult awaitingAuthoritativeEmptyProof() {
        return new ReportImportResult(
                Status.AWAITING_AUTHORITATIVE_EMPTY_PROOF,
                "REPORT_EMPTY_PROOF_REQUIRED"
        );
    }

    public static ReportImportResult staleFence() {
        return new ReportImportResult(Status.STALE_FENCE, "REPORT_APPLY_STALE_FENCE");
    }

    public static ReportImportResult contractError(String sanitizedCode) {
        return new ReportImportResult(
                Status.CONTRACT_ERROR,
                ReportContract.requireSafeCode(sanitizedCode, "sanitizedCode")
        );
    }

    public Status getStatus() {
        return status;
    }

    public String getSanitizedCode() {
        return sanitizedCode;
    }
}
