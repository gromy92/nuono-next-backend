package com.nuono.next.datapull.snapshot;

import java.util.OptionalInt;
import java.util.regex.Pattern;

/** Outcome of one fenced, idempotent staging attempt. */
public final class SnapshotStageResult {
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");
    public enum Status {
        STAGED,
        IDEMPOTENT_REPLAY,
        REJECTED
    }

    private final Status status;
    private final String sanitizedCode;
    private final Integer nextPage;
    private final Integer knownLastPage;

    private SnapshotStageResult(
            Status status,
            String sanitizedCode,
            Integer nextPage,
            Integer knownLastPage
    ) {
        this.status = status;
        this.sanitizedCode = sanitizedCode;
        this.nextPage = nextPage;
        this.knownLastPage = knownLastPage;
    }

    public static SnapshotStageResult staged(Integer nextPage, Integer knownLastPage) {
        return accepted(Status.STAGED, "SNAPSHOT_PAGE_STAGED", nextPage, knownLastPage);
    }

    public static SnapshotStageResult idempotentReplay(Integer nextPage, Integer knownLastPage) {
        return accepted(
                Status.IDEMPOTENT_REPLAY,
                "SNAPSHOT_PAGE_ALREADY_STAGED",
                nextPage,
                knownLastPage
        );
    }

    public static SnapshotStageResult rejected(String sanitizedCode) {
        return new SnapshotStageResult(Status.REJECTED, requireCode(sanitizedCode), null, null);
    }

    private static SnapshotStageResult accepted(
            Status status,
            String code,
            Integer nextPage,
            Integer knownLastPage
    ) {
        if (nextPage != null && nextPage < 1) {
            throw new IllegalArgumentException("nextPage must be positive");
        }
        if (knownLastPage != null && knownLastPage < 1) {
            throw new IllegalArgumentException("knownLastPage must be positive");
        }
        return new SnapshotStageResult(status, code, nextPage, knownLastPage);
    }

    private static String requireCode(String code) {
        if (code == null
                || code.length() > 80
                || !code.equals(code.trim())
                || !SAFE_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("sanitizedCode must be a safe identity of at most 80 characters");
        }
        return code;
    }

    public Status getStatus() {
        return status;
    }

    public String getSanitizedCode() {
        return sanitizedCode;
    }

    public OptionalInt getNextPage() {
        return nextPage == null ? OptionalInt.empty() : OptionalInt.of(nextPage);
    }

    public OptionalInt getKnownLastPage() {
        return knownLastPage == null ? OptionalInt.empty() : OptionalInt.of(knownLastPage);
    }

    public boolean isAccepted() {
        return status != Status.REJECTED;
    }

    public boolean reachedKnownLastPage(int stagedPageNo) {
        return knownLastPage != null && knownLastPage == stagedPageNo;
    }
}
