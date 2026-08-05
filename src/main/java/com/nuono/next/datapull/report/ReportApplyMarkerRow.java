package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;

/** Durable evidence that one report task already committed its complete artifact. */
public class ReportApplyMarkerRow {
    private Long taskId;
    private OperationCode operationCode;
    private String scopeKey;
    private String businessWindowKey;
    private Long appliedFenceEpoch;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { this.taskId = value; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode value) { this.operationCode = value; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { this.scopeKey = value; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public void setBusinessWindowKey(String value) { this.businessWindowKey = value; }
    public Long getAppliedFenceEpoch() { return appliedFenceEpoch; }
    public void setAppliedFenceEpoch(Long value) { this.appliedFenceEpoch = value; }
}
