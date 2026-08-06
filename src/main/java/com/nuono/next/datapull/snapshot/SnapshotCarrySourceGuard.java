package com.nuono.next.datapull.snapshot;

import java.util.Objects;

/** Validates the head version pinned as one materialized carry source. */
final class SnapshotCarrySourceGuard {
    void requireValid(CompleteSnapshot<?> snapshot, SnapshotCurrentHeadRow source) {
        if (source.getOperationCode() != snapshot.getOperationCode()
                || !Objects.equals(source.getScopeKey(), snapshot.getScopeKey())
                || source.getTaskId() == null || source.getTaskId() < 1L
                || source.getVersionNo() == null || source.getVersionNo() < 0L
                || source.getScheduleSlot() == null || source.getRetireMissing() == null) {
            throw new IllegalStateException("snapshot carry source head is invalid");
        }
    }

    boolean same(SnapshotApplyProgressRow progress, SnapshotCurrentHeadRow source) {
        return source != null
                && Objects.equals(source.getTaskId(), progress.getCarrySourceTaskId())
                && Objects.equals(source.getVersionNo(), progress.getCarrySourceHeadVersion());
    }

    boolean newer(SnapshotCurrentHeadRow head, CompleteSnapshot<?> snapshot) {
        int order = head.getScheduleSlot().compareTo(snapshot.getScheduleSlot());
        return order > 0 || (order == 0 && head.getTaskId() > snapshot.getTaskId());
    }
}
