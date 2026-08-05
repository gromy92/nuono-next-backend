package com.nuono.next.datapull.snapshot;

/** Result of one fenced, bounded snapshot-stage reset step. */
public enum SnapshotStageClearResult {
    CLEARED,
    MORE_WORK,
    STALE_FENCE,
    APPLY_ALREADY_STARTED
}
