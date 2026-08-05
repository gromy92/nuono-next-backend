package com.nuono.next.datapull.snapshot;

/** Result of one bounded, local-only pass-one/pass-two multiset comparison. */
public final class SnapshotComparisonResult {
    public enum Status { MORE_WORK, VERIFIED, REJECTED }

    private final Status status;
    private final String sanitizedCode;

    private SnapshotComparisonResult(Status status, String code) {
        this.status = status;
        this.sanitizedCode = code;
    }

    public static SnapshotComparisonResult moreWork() {
        return new SnapshotComparisonResult(Status.MORE_WORK, "SNAPSHOT_COMPARE_MORE_WORK");
    }

    public static SnapshotComparisonResult verified() {
        return new SnapshotComparisonResult(Status.VERIFIED, "SNAPSHOT_COMPARE_VERIFIED");
    }

    public static SnapshotComparisonResult rejected(String code) {
        return new SnapshotComparisonResult(Status.REJECTED, code);
    }

    public boolean isAccepted() { return status != Status.REJECTED; }
    public boolean isVerified() { return status == Status.VERIFIED; }
    public Status getStatus() { return status; }
    public String getSanitizedCode() { return sanitizedCode; }
}
