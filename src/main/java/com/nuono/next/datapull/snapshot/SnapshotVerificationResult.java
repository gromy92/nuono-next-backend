package com.nuono.next.datapull.snapshot;

import java.util.OptionalInt;

/** Result of one idempotent pass-two page transaction. */
public final class SnapshotVerificationResult {
    public enum Status { ACCEPTED, REPLAYED, COMPLETE, REJECTED }

    private final Status status;
    private final Integer nextPage;
    private final String sanitizedCode;

    private SnapshotVerificationResult(Status status, Integer nextPage, String code) {
        this.status = status;
        this.nextPage = nextPage;
        this.sanitizedCode = code;
    }

    public static SnapshotVerificationResult accepted(int nextPage) {
        return new SnapshotVerificationResult(Status.ACCEPTED, nextPage, "SNAPSHOT_VERIFY_PAGE");
    }

    public static SnapshotVerificationResult replayed(Integer nextPage) {
        return new SnapshotVerificationResult(
                nextPage == null ? Status.COMPLETE : Status.REPLAYED,
                nextPage,
                "SNAPSHOT_VERIFY_REPLAY"
        );
    }

    public static SnapshotVerificationResult complete() {
        return new SnapshotVerificationResult(Status.COMPLETE, null, "SNAPSHOT_VERIFY_COMPLETE");
    }

    public static SnapshotVerificationResult rejected(String code) {
        return new SnapshotVerificationResult(Status.REJECTED, null, code);
    }

    public boolean isAccepted() { return status != Status.REJECTED; }
    public boolean isComplete() { return status == Status.COMPLETE; }
    public Status getStatus() { return status; }
    public String getSanitizedCode() { return sanitizedCode; }
    public OptionalInt getNextPage() {
        return nextPage == null ? OptionalInt.empty() : OptionalInt.of(nextPage);
    }
}
