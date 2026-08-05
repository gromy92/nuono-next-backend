package com.nuono.next.datapull.schedule;

import java.time.LocalDateTime;
import java.util.Objects;

/** One staged scope whose immutable admission and anchor have been verified. */
public final class ScheduleAnchorStageUpdate {
    private final String scopeKey;
    private final LocalDateTime reconcileAfterUtc;

    public ScheduleAnchorStageUpdate(String scopeKey, LocalDateTime reconcileAfterUtc) {
        this.scopeKey = Objects.requireNonNull(scopeKey, "scopeKey");
        this.reconcileAfterUtc = Objects.requireNonNull(reconcileAfterUtc, "reconcileAfterUtc");
    }

    public String getScopeKey() { return scopeKey; }
    public LocalDateTime getReconcileAfterUtc() { return reconcileAfterUtc; }
}
