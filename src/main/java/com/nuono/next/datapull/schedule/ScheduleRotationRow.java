package com.nuono.next.datapull.schedule;

/** Locked singleton row for persistent operation round-robin. */
public class ScheduleRotationRow {
    private Integer nextOperationOrdinal;
    private Long version;

    public Integer getNextOperationOrdinal() { return nextOperationOrdinal; }
    public void setNextOperationOrdinal(Integer value) { nextOperationOrdinal = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
