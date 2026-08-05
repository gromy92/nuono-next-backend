package com.nuono.next.datapull.snapshot;

import java.util.OptionalInt;

/** Durable technical cursor for fetch-one-page or apply-one-transaction advances. */
public final class SnapshotCheckpoint {
    public enum Phase {
        FETCH,
        VERIFY,
        COMPARE,
        APPLY,
        RESET
    }

    private final Phase phase;
    private final int nextPage;
    private final Integer knownLastPage;
    private final int consecutiveRetryAttempt;

    private SnapshotCheckpoint(
            Phase phase,
            int nextPage,
            Integer knownLastPage,
            int consecutiveRetryAttempt
    ) {
        if (nextPage < 1) {
            throw new IllegalArgumentException("nextPage must be positive");
        }
        if (knownLastPage != null && knownLastPage < 1) {
            throw new IllegalArgumentException("knownLastPage must be positive");
        }
        if ((phase == Phase.FETCH || phase == Phase.VERIFY)
                && knownLastPage != null && nextPage > knownLastPage) {
            throw new IllegalArgumentException("page cursor must not be after knownLastPage");
        }
        if (phase == Phase.VERIFY && knownLastPage == null) {
            throw new IllegalArgumentException("VERIFY requires a known last page");
        }
        if (phase == Phase.COMPARE
                && (knownLastPage == null || nextPage != knownLastPage + 1)) {
            throw new IllegalArgumentException("COMPARE must follow a known last page");
        }
        if (phase == Phase.APPLY
                && (knownLastPage == null || nextPage != knownLastPage + 1)) {
            throw new IllegalArgumentException("APPLY must point immediately after a known last page");
        }
        if (phase == Phase.RESET
                && (nextPage != 1 || knownLastPage != null || consecutiveRetryAttempt != 0)) {
            throw new IllegalArgumentException("RESET must not carry a fetch cursor or retry attempt");
        }
        if (consecutiveRetryAttempt < 0) {
            throw new IllegalArgumentException("consecutiveRetryAttempt must not be negative");
        }
        this.phase = phase;
        this.nextPage = nextPage;
        this.knownLastPage = knownLastPage;
        this.consecutiveRetryAttempt = consecutiveRetryAttempt;
    }

    public static SnapshotCheckpoint initial() {
        return fetch(1, null);
    }

    public static SnapshotCheckpoint fetch(int nextPage, Integer knownLastPage) {
        return fetch(nextPage, knownLastPage, 0);
    }

    static SnapshotCheckpoint fetch(
            int nextPage,
            Integer knownLastPage,
            int consecutiveRetryAttempt
    ) {
        return new SnapshotCheckpoint(
                Phase.FETCH,
                nextPage,
                knownLastPage,
                consecutiveRetryAttempt
        );
    }

    public static SnapshotCheckpoint apply(int knownLastPage) {
        return apply(knownLastPage, 0);
    }

    public static SnapshotCheckpoint verify(int nextPage, int knownLastPage) {
        return verify(nextPage, knownLastPage, 0);
    }

    static SnapshotCheckpoint verify(
            int nextPage,
            int knownLastPage,
            int consecutiveRetryAttempt
    ) {
        return new SnapshotCheckpoint(
                Phase.VERIFY, nextPage, knownLastPage, consecutiveRetryAttempt
        );
    }

    public static SnapshotCheckpoint compare(int knownLastPage) {
        return new SnapshotCheckpoint(Phase.COMPARE, knownLastPage + 1, knownLastPage, 0);
    }

    public static SnapshotCheckpoint resetting() {
        return new SnapshotCheckpoint(Phase.RESET, 1, null, 0);
    }

    SnapshotCheckpoint nextRetryAttempt() {
        if (phase == Phase.RESET) {
            throw new IllegalStateException("RESET does not call a provider");
        }
        if (consecutiveRetryAttempt == Integer.MAX_VALUE) {
            throw new IllegalStateException("snapshot retry attempt cannot advance");
        }
        if (phase == Phase.FETCH) {
            return fetch(nextPage, knownLastPage, consecutiveRetryAttempt + 1);
        }
        if (phase == Phase.VERIFY) {
            return verify(nextPage, knownLastPage, consecutiveRetryAttempt + 1);
        }
        if (phase == Phase.APPLY) {
            return apply(knownLastPage, consecutiveRetryAttempt + 1);
        }
        throw new IllegalStateException("local snapshot phases do not call a provider");
    }

    static SnapshotCheckpoint apply(int knownLastPage, int consecutiveRetryAttempt) {
        return new SnapshotCheckpoint(
                Phase.APPLY,
                knownLastPage + 1,
                knownLastPage,
                consecutiveRetryAttempt
        );
    }

    public Phase getPhase() {
        return phase;
    }

    public int getNextPage() {
        return nextPage;
    }

    public OptionalInt getKnownLastPage() {
        return knownLastPage == null ? OptionalInt.empty() : OptionalInt.of(knownLastPage);
    }

    public int getConsecutiveRetryAttempt() {
        return consecutiveRetryAttempt;
    }
}
