package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Immutable evidence that one complete snapshot task already committed its facts. */
public class SnapshotApplyMarkerRow {
    private Long taskId;
    private OperationCode operationCode;
    private String scopeKey;
    private String businessWindowKey;
    private Long appliedFenceEpoch;
    private SnapshotCollectionAuthority.Kind authorityKind;
    private String authorityTokenSha256;
    private LocalDateTime snapshotAsOfUtc;
    private Long declaredCollectionCount;
    private Long sourceItemCount;
    private Long appliedItemCount;
    private Long identitySkippedItemCount;
    private Long businessSkippedItemCount;
    private Integer lastPage;
    private Long effectiveItemCount;
    private SnapshotCarryMode carryMode;
    private Long carriedFromTaskId;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode operationCode) { this.operationCode = operationCode; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public void setBusinessWindowKey(String value) { this.businessWindowKey = value; }
    public Long getAppliedFenceEpoch() { return appliedFenceEpoch; }
    public void setAppliedFenceEpoch(Long value) { this.appliedFenceEpoch = value; }
    public SnapshotCollectionAuthority.Kind getAuthorityKind() { return authorityKind; }
    public void setAuthorityKind(SnapshotCollectionAuthority.Kind value) { authorityKind = value; }
    public String getAuthorityTokenSha256() { return authorityTokenSha256; }
    public void setAuthorityTokenSha256(String value) { authorityTokenSha256 = value; }
    public LocalDateTime getSnapshotAsOfUtc() { return snapshotAsOfUtc; }
    public void setSnapshotAsOfUtc(LocalDateTime value) { snapshotAsOfUtc = value; }
    public Long getDeclaredCollectionCount() { return declaredCollectionCount; }
    public void setDeclaredCollectionCount(Long value) { declaredCollectionCount = value; }
    public Long getSourceItemCount() { return sourceItemCount; }
    public void setSourceItemCount(Long value) { sourceItemCount = value; }
    public Long getAppliedItemCount() { return appliedItemCount; }
    public void setAppliedItemCount(Long value) { appliedItemCount = value; }
    public Long getIdentitySkippedItemCount() { return identitySkippedItemCount; }
    public void setIdentitySkippedItemCount(Long value) { identitySkippedItemCount = value; }
    public Long getBusinessSkippedItemCount() { return businessSkippedItemCount; }
    public void setBusinessSkippedItemCount(Long value) { businessSkippedItemCount = value; }
    public Integer getLastPage() { return lastPage; }
    public void setLastPage(Integer value) { lastPage = value; }
    public Long getEffectiveItemCount() { return effectiveItemCount; }
    public void setEffectiveItemCount(Long value) { effectiveItemCount = value; }
    public SnapshotCarryMode getCarryMode() { return carryMode; }
    public void setCarryMode(SnapshotCarryMode value) { carryMode = value; }
    public Long getCarriedFromTaskId() { return carriedFromTaskId; }
    public void setCarriedFromTaskId(Long value) { carriedFromTaskId = value; }
}
