package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Locked task identity used by the atomic snapshot fact-apply guard. */
public class SnapshotApplyTaskRow {
    private Long taskId;
    private OperationCode operationCode;
    private String scopeKey;
    private String businessWindowKey;
    private Long fenceEpoch;
    private String state;
    private String leaseOwner;
    private LocalDateTime leaseUntil;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode operationCode) { this.operationCode = operationCode; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public void setBusinessWindowKey(String value) { this.businessWindowKey = value; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long fenceEpoch) { this.fenceEpoch = fenceEpoch; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
}
