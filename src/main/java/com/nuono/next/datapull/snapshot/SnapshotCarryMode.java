package com.nuono.next.datapull.snapshot;

/** How one effective snapshot generation preserves facts from the prior sealed generation. */
public enum SnapshotCarryMode {
    NONE,
    TARGETED,
    FULL
}
