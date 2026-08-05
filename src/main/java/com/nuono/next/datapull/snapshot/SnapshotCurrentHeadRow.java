package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Atomic current-generation pointer for one complete-snapshot scope. */
public final class SnapshotCurrentHeadRow {
    private OperationCode operationCode;
    private String scopeKey;
    private Long taskId;
    private String businessWindowKey;
    private LocalDateTime scheduleSlot;
    private Boolean retireMissing;
    private Long versionNo;

    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode value) { operationCode = value; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public void setBusinessWindowKey(String value) { businessWindowKey = value; }
    public LocalDateTime getScheduleSlot() { return scheduleSlot; }
    public void setScheduleSlot(LocalDateTime value) { scheduleSlot = value; }
    public Boolean getRetireMissing() { return retireMissing; }
    public void setRetireMissing(Boolean value) { retireMissing = value; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long value) { versionNo = value; }
}
