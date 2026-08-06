package com.nuono.next.datapull.snapshot;

/** Strict, versioned codec with no provider payload or business-readiness state. */
public final class SnapshotCheckpointCodec {
    private static final String VERSION = "v2";

    public String encode(SnapshotCheckpoint checkpoint) {
        String lastPage = checkpoint.getKnownLastPage().isPresent()
                ? String.valueOf(checkpoint.getKnownLastPage().getAsInt())
                : "-";
        return VERSION
                + "|"
                + checkpoint.getPhase().name()
                + "|"
                + checkpoint.getNextPage()
                + "|"
                + lastPage
                + "|"
                + checkpoint.getConsecutiveRetryAttempt();
    }

    public SnapshotCheckpoint decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return SnapshotCheckpoint.initial();
        }
        if (!encoded.equals(encoded.trim())) {
            throw new IllegalArgumentException("snapshot checkpoint must not contain outer whitespace");
        }
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 5 || !(VERSION.equals(parts[0]) || "v1".equals(parts[0]))) {
            throw new IllegalArgumentException("unsupported snapshot checkpoint");
        }
        SnapshotCheckpoint.Phase phase;
        int nextPage;
        Integer lastPage;
        int retryAttempt;
        try {
            phase = SnapshotCheckpoint.Phase.valueOf(parts[1]);
            nextPage = Integer.parseInt(parts[2]);
            lastPage = "-".equals(parts[3]) ? null : Integer.valueOf(parts[3]);
            retryAttempt = Integer.parseInt(parts[4]);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid snapshot checkpoint", invalid);
        }
        if ("v1".equals(parts[0])
                && phase != SnapshotCheckpoint.Phase.FETCH
                && phase != SnapshotCheckpoint.Phase.APPLY
                && phase != SnapshotCheckpoint.Phase.RESET) {
            throw new IllegalArgumentException("unsupported v1 snapshot checkpoint phase");
        }
        switch (phase) {
            case FETCH:
                return SnapshotCheckpoint.fetch(nextPage, lastPage, retryAttempt);
            case VERIFY:
                return requireVerify(nextPage, lastPage, retryAttempt);
            case COMPARE:
                return requireCompare(nextPage, lastPage, retryAttempt);
            case APPLY:
                return requireApply(nextPage, lastPage, retryAttempt);
            case RESET:
                if (nextPage != 1 || lastPage != null || retryAttempt != 0) {
                    throw new IllegalArgumentException("invalid RESET snapshot checkpoint");
                }
                return SnapshotCheckpoint.resetting();
            default:
                throw new IllegalArgumentException("unsupported snapshot checkpoint phase");
        }
    }

    private SnapshotCheckpoint requireVerify(
            int nextPage,
            Integer lastPage,
            int retryAttempt
    ) {
        if (lastPage == null) {
            throw new IllegalArgumentException("invalid VERIFY snapshot checkpoint");
        }
        return SnapshotCheckpoint.verify(nextPage, lastPage, retryAttempt);
    }

    private SnapshotCheckpoint requireCompare(
            int nextPage,
            Integer lastPage,
            int retryAttempt
    ) {
        if (lastPage == null || nextPage != lastPage + 1 || retryAttempt != 0) {
            throw new IllegalArgumentException("invalid COMPARE snapshot checkpoint");
        }
        return SnapshotCheckpoint.compare(lastPage);
    }

    private SnapshotCheckpoint requireApply(int nextPage, Integer lastPage, int retryAttempt) {
        if (lastPage == null || nextPage != lastPage + 1) {
            throw new IllegalArgumentException("invalid APPLY snapshot checkpoint");
        }
        return SnapshotCheckpoint.apply(lastPage, retryAttempt);
    }
}
