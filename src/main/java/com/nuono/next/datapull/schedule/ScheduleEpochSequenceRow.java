package com.nuono.next.datapull.schedule;

/** Locked monotonic schedule-epoch identity state for one operation. */
public final class ScheduleEpochSequenceRow {
    private Long lastEpochNo;
    private Long version;

    public Long getLastEpochNo() { return lastEpochNo; }
    public void setLastEpochNo(Long value) { lastEpochNo = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
