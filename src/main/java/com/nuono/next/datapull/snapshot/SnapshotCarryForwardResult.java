package com.nuono.next.datapull.snapshot;

/** Exact outcome of one bounded effective-generation carry-forward slice. */
public final class SnapshotCarryForwardResult {
    private static final SnapshotCarryForwardResult COMPLETE =
            new SnapshotCarryForwardResult(null, 0);

    private final String lastStableIdentity;
    private final int materializedItemCount;

    private SnapshotCarryForwardResult(String lastStableIdentity, int materializedItemCount) {
        this.lastStableIdentity = lastStableIdentity;
        this.materializedItemCount = materializedItemCount;
    }

    public static SnapshotCarryForwardResult complete() {
        return COMPLETE;
    }

    public static SnapshotCarryForwardResult advanced(
            String lastStableIdentity,
            int materializedItemCount
    ) {
        if (lastStableIdentity == null || lastStableIdentity.isEmpty()
                || !lastStableIdentity.equals(lastStableIdentity.trim())
                || materializedItemCount < 1) {
            throw new IllegalArgumentException("snapshot carry result is invalid");
        }
        return new SnapshotCarryForwardResult(lastStableIdentity, materializedItemCount);
    }

    public boolean isComplete() { return lastStableIdentity == null; }
    public String getLastStableIdentity() { return lastStableIdentity; }
    public int getMaterializedItemCount() { return materializedItemCount; }
}
