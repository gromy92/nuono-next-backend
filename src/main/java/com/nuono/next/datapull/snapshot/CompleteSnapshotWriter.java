package com.nuono.next.datapull.snapshot;

/**
 * Atomic complete-snapshot replacement Seam.
 *
 * <p>An Implementation prepares at most one bounded chunk per call, then seals current visibility
 * and the task marker in one short local transaction. Replaying after an unknown commit result
 * returns {@link ReplaceResult#ALREADY_APPLIED}, never replaces twice.</p>
 */
@FunctionalInterface
public interface CompleteSnapshotWriter<T> {
    ReplaceResult replace(CompleteSnapshot<T> snapshot);

    enum ReplaceResult {
        APPLIED,
        ALREADY_APPLIED,
        MORE_WORK,
        STALE_FENCE
    }
}
