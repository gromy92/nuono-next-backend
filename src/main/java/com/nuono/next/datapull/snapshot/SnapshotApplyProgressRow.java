package com.nuono.next.datapull.snapshot;

/** Locked durable cursor for bounded complete-snapshot preparation. */
public final class SnapshotApplyProgressRow {
    private Long taskId;
    private Long activeFenceEpoch;
    private Integer cursorPageNo;
    private Integer cursorItemOrdinal;
    private Long preparedItemCount;
    private Long absenceUnsafeItemCount;
    private Long effectiveItemCount;
    private String targetRefType;
    private Long targetRefId;
    private SnapshotCarryMode carryMode;
    private Long carrySourceTaskId;
    private Long carrySourceHeadVersion;
    private String carryCursorIdentity;
    private String state;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Long getActiveFenceEpoch() { return activeFenceEpoch; }
    public void setActiveFenceEpoch(Long value) { activeFenceEpoch = value; }
    public Integer getCursorPageNo() { return cursorPageNo; }
    public void setCursorPageNo(Integer value) { cursorPageNo = value; }
    public Integer getCursorItemOrdinal() { return cursorItemOrdinal; }
    public void setCursorItemOrdinal(Integer value) { cursorItemOrdinal = value; }
    public Long getPreparedItemCount() { return preparedItemCount; }
    public void setPreparedItemCount(Long value) { preparedItemCount = value; }
    public Long getAbsenceUnsafeItemCount() { return absenceUnsafeItemCount; }
    public void setAbsenceUnsafeItemCount(Long value) { absenceUnsafeItemCount = value; }
    public Long getEffectiveItemCount() { return effectiveItemCount; }
    public void setEffectiveItemCount(Long value) { effectiveItemCount = value; }
    public String getTargetRefType() { return targetRefType; }
    public void setTargetRefType(String value) { targetRefType = value; }
    public Long getTargetRefId() { return targetRefId; }
    public void setTargetRefId(Long value) { targetRefId = value; }
    public SnapshotCarryMode getCarryMode() { return carryMode; }
    public void setCarryMode(SnapshotCarryMode value) { carryMode = value; }
    public Long getCarrySourceTaskId() { return carrySourceTaskId; }
    public void setCarrySourceTaskId(Long value) { carrySourceTaskId = value; }
    public Long getCarrySourceHeadVersion() { return carrySourceHeadVersion; }
    public void setCarrySourceHeadVersion(Long value) { carrySourceHeadVersion = value; }
    public String getCarryCursorIdentity() { return carryCursorIdentity; }
    public void setCarryCursorIdentity(String value) { carryCursorIdentity = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
}
