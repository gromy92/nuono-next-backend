package com.nuono.next.datapull.schedule;

import java.time.LocalDateTime;

/** Batch projection of the latest durable task slot for one scope. */
public class ScheduleLatestSlotRow {
    private String scopeKey;
    private LocalDateTime latestScheduleSlot;

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public LocalDateTime getLatestScheduleSlot() { return latestScheduleSlot; }
    public void setLatestScheduleSlot(LocalDateTime value) { latestScheduleSlot = value; }
}
