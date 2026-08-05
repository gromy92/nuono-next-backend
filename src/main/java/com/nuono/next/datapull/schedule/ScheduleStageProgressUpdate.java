package com.nuono.next.datapull.schedule;

import java.time.LocalDateTime;
import java.util.Objects;

/** Persistent per-scope exact-window cursor after one bounded schedule step. */
public final class ScheduleStageProgressUpdate {
    private final String scopeKey;
    private final LocalDateTime scheduleAfterUtc;
    private final String scheduleState;

    public ScheduleStageProgressUpdate(
            String scopeKey, LocalDateTime scheduleAfterUtc, String scheduleState
    ) {
        this.scopeKey = Objects.requireNonNull(scopeKey, "scopeKey");
        this.scheduleAfterUtc = Objects.requireNonNull(scheduleAfterUtc, "scheduleAfterUtc");
        if (!"RUNNING".equals(scheduleState) && !"COMPLETE".equals(scheduleState)) {
            throw new IllegalArgumentException("schedule stage state is invalid");
        }
        this.scheduleState = scheduleState;
    }

    public String getScopeKey() { return scopeKey; }
    public LocalDateTime getScheduleAfterUtc() { return scheduleAfterUtc; }
    public String getScheduleState() { return scheduleState; }
}
