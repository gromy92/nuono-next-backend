package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Locked runtime task identity for one report import transaction. */
public class ReportApplyTaskRow {
    private Long taskId;
    private OperationCode operationCode;
    private String scopeKey;
    private String businessWindowKey;
    private Long fenceEpoch;
    private String state;
    private String leaseOwner;
    private LocalDateTime leaseUntil;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { this.taskId = value; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode value) { this.operationCode = value; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { this.scopeKey = value; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public void setBusinessWindowKey(String value) { this.businessWindowKey = value; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long value) { this.fenceEpoch = value; }
    public String getState() { return state; }
    public void setState(String value) { this.state = value; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String value) { this.leaseOwner = value; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime value) { this.leaseUntil = value; }
}
