package com.nuono.next.datapull.snapshot;

/** Copies one bounded slice from the prior fully materialized generation into the new one. */
@FunctionalInterface
public interface SnapshotCarryForward {
    SnapshotCarryForwardResult carry(
            long sourceTaskId,
            SnapshotCarryMode mode,
            String afterStableIdentity,
            int limit
    );
}
