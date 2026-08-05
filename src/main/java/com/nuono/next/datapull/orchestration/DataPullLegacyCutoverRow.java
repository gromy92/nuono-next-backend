package com.nuono.next.datapull.orchestration;

/** Aggregate for one independently drained legacy DP execution surface. */
public final class DataPullLegacyCutoverRow {
    private String recordKind;
    private Long activeCount;
    private Long supersedableSnapshotCount;

    public String getRecordKind() { return recordKind; }
    public void setRecordKind(String value) { recordKind = value; }
    public Long getActiveCount() { return activeCount; }
    public void setActiveCount(Long value) { activeCount = value; }
    public Long getSupersedableSnapshotCount() { return supersedableSnapshotCount; }
    public void setSupersedableSnapshotCount(Long value) {
        supersedableSnapshotCount = value;
    }
}
