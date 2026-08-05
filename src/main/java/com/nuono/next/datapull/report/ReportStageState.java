package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.OperationCode;

/** Durable cursor and accounting state for one immutable complete report artifact. */
public class ReportStageState {
    private Long taskId;
    private OperationCode operationCode;
    private String artifactKey;
    private String artifactSha256;
    private Long activeFenceEpoch;
    private String state;
    private String headerJson;
    private Long nextByteOffset;
    private Long declaredRowCount;
    private Long sourceRowCount;
    private Long acceptedRowCount;
    private Long businessSkippedRowCount;
    private Long identitySkippedRowCount;
    private Long applyRowCursor;
    private Long appliedRowCount;
    private Long appliedWarningCount;
    private Long factContainerId;
    private String poisonCode;
    private Long versionNo;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode operationCode) { this.operationCode = operationCode; }
    public String getArtifactKey() { return artifactKey; }
    public void setArtifactKey(String artifactKey) { this.artifactKey = artifactKey; }
    public String getArtifactSha256() { return artifactSha256; }
    public void setArtifactSha256(String artifactSha256) { this.artifactSha256 = artifactSha256; }
    public Long getActiveFenceEpoch() { return activeFenceEpoch; }
    public void setActiveFenceEpoch(Long activeFenceEpoch) { this.activeFenceEpoch = activeFenceEpoch; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getHeaderJson() { return headerJson; }
    public void setHeaderJson(String headerJson) { this.headerJson = headerJson; }
    public Long getNextByteOffset() { return nextByteOffset; }
    public void setNextByteOffset(Long nextByteOffset) { this.nextByteOffset = nextByteOffset; }
    public Long getDeclaredRowCount() { return declaredRowCount; }
    public void setDeclaredRowCount(Long declaredRowCount) { this.declaredRowCount = declaredRowCount; }
    public Long getSourceRowCount() { return sourceRowCount; }
    public void setSourceRowCount(Long sourceRowCount) { this.sourceRowCount = sourceRowCount; }
    public Long getAcceptedRowCount() { return acceptedRowCount; }
    public void setAcceptedRowCount(Long acceptedRowCount) { this.acceptedRowCount = acceptedRowCount; }
    public Long getBusinessSkippedRowCount() { return businessSkippedRowCount; }
    public void setBusinessSkippedRowCount(Long businessSkippedRowCount) { this.businessSkippedRowCount = businessSkippedRowCount; }
    public Long getIdentitySkippedRowCount() { return identitySkippedRowCount; }
    public void setIdentitySkippedRowCount(Long identitySkippedRowCount) { this.identitySkippedRowCount = identitySkippedRowCount; }
    public Long getApplyRowCursor() { return applyRowCursor; }
    public void setApplyRowCursor(Long applyRowCursor) { this.applyRowCursor = applyRowCursor; }
    public Long getAppliedRowCount() { return appliedRowCount; }
    public void setAppliedRowCount(Long appliedRowCount) { this.appliedRowCount = appliedRowCount; }
    public Long getAppliedWarningCount() { return appliedWarningCount; }
    public void setAppliedWarningCount(Long appliedWarningCount) { this.appliedWarningCount = appliedWarningCount; }
    public Long getFactContainerId() { return factContainerId; }
    public void setFactContainerId(Long factContainerId) { this.factContainerId = factContainerId; }
    public String getPoisonCode() { return poisonCode; }
    public void setPoisonCode(String poisonCode) { this.poisonCode = poisonCode; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
}
