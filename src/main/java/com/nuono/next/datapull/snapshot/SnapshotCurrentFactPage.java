package com.nuono.next.datapull.snapshot;

import java.util.List;

/** Bounded read page from one atomically sealed DP-owned snapshot generation. */
public final class SnapshotCurrentFactPage<T> {
    private final long taskId;
    private final boolean retireMissing;
    private final List<SnapshotApplyItem<T>> items;

    SnapshotCurrentFactPage(long taskId, boolean retireMissing, List<SnapshotApplyItem<T>> items) {
        this.taskId = taskId;
        this.retireMissing = retireMissing;
        this.items = List.copyOf(items);
    }

    public long getTaskId() { return taskId; }
    public boolean isRetireMissing() { return retireMissing; }
    public List<SnapshotApplyItem<T>> getItems() { return items; }
}
