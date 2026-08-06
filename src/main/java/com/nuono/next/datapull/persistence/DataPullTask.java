package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;

/** Persisted technical state and immutable scope snapshot for one DP business window. */
public class DataPullTask {

    private Long id;
    private OperationCode operationCode;
    private String providerChannel;
    private Long ownerUserId;
    private Long logicalStoreId;
    private String accountKey;
    private String egressKey;
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private String scopeKey;
    private String scopeBindingId;
    private String scopePayloadType;
    private String scopePayloadSha256;
    private String scopePayload;
    private LocalDateTime scopeBindingEffectiveFromUtc;
    private LocalDateTime scheduleSlot;
    private String businessWindowKey;
    private TaskState state;
    private String stepCode;
    private String remoteHandle;
    private String checkpoint;
    private LocalDateTime retryNotBefore;
    private Integer attempt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Long fenceEpoch;
    private Long version;
    private String sanitizedFailureCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;

    public static DataPullTask queued(
            Long id,
            OperationCode operationCode,
            String providerChannel,
            Long ownerUserId,
            Long logicalStoreId,
            String accountKey,
            String egressKey,
            String projectCode,
            String storeCode,
            String siteCode,
            String scopeKey,
            LocalDateTime scheduleSlot,
            String businessWindowKey,
            String stepCode,
            LocalDateTime now
    ) {
        DataPullTask task = new DataPullTask();
        task.id = id;
        task.operationCode = operationCode;
        task.providerChannel = providerChannel;
        task.ownerUserId = ownerUserId;
        task.logicalStoreId = logicalStoreId;
        task.accountKey = accountKey;
        task.egressKey = egressKey;
        task.projectCode = projectCode;
        task.storeCode = storeCode;
        task.siteCode = siteCode;
        task.scopeKey = scopeKey;
        task.scheduleSlot = scheduleSlot;
        task.businessWindowKey = businessWindowKey;
        task.state = TaskState.QUEUED;
        task.stepCode = stepCode;
        task.attempt = 0;
        task.fenceEpoch = 0L;
        task.version = 0L;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    DataPullTask copy() {
        DataPullTask copy = new DataPullTask();
        copy.id = id;
        copy.operationCode = operationCode;
        copy.providerChannel = providerChannel;
        copy.ownerUserId = ownerUserId;
        copy.logicalStoreId = logicalStoreId;
        copy.accountKey = accountKey;
        copy.egressKey = egressKey;
        copy.projectCode = projectCode;
        copy.storeCode = storeCode;
        copy.siteCode = siteCode;
        copy.scopeKey = scopeKey;
        copy.scopeBindingId = scopeBindingId;
        copy.scopePayloadType = scopePayloadType;
        copy.scopePayloadSha256 = scopePayloadSha256;
        copy.scopePayload = scopePayload;
        copy.scopeBindingEffectiveFromUtc = scopeBindingEffectiveFromUtc;
        copy.scheduleSlot = scheduleSlot;
        copy.businessWindowKey = businessWindowKey;
        copy.state = state;
        copy.stepCode = stepCode;
        copy.remoteHandle = remoteHandle;
        copy.checkpoint = checkpoint;
        copy.retryNotBefore = retryNotBefore;
        copy.attempt = attempt;
        copy.leaseOwner = leaseOwner;
        copy.leaseUntil = leaseUntil;
        copy.fenceEpoch = fenceEpoch;
        copy.version = version;
        copy.sanitizedFailureCode = sanitizedFailureCode;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        copy.finishedAt = finishedAt;
        return copy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode operationCode) { this.operationCode = operationCode; }
    public String getProviderChannel() { return providerChannel; }
    public void setProviderChannel(String providerChannel) { this.providerChannel = providerChannel; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public void setLogicalStoreId(Long logicalStoreId) { this.logicalStoreId = logicalStoreId; }
    public String getAccountKey() { return accountKey; }
    public void setAccountKey(String accountKey) { this.accountKey = accountKey; }
    public String getEgressKey() { return egressKey; }
    public void setEgressKey(String egressKey) { this.egressKey = egressKey; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getScopeBindingId() { return scopeBindingId; }
    public void setScopeBindingId(String value) { scopeBindingId = value; }
    public String getScopePayloadType() { return scopePayloadType; }
    public void setScopePayloadType(String value) { scopePayloadType = value; }
    public String getScopePayloadSha256() { return scopePayloadSha256; }
    public void setScopePayloadSha256(String value) { scopePayloadSha256 = value; }
    public String getScopePayload() { return scopePayload; }
    public void setScopePayload(String value) { scopePayload = value; }
    public LocalDateTime getScopeBindingEffectiveFromUtc() {
        return scopeBindingEffectiveFromUtc;
    }
    public void setScopeBindingEffectiveFromUtc(LocalDateTime value) {
        scopeBindingEffectiveFromUtc = value;
    }
    public LocalDateTime getScheduleSlot() { return scheduleSlot; }
    public void setScheduleSlot(LocalDateTime scheduleSlot) { this.scheduleSlot = scheduleSlot; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public void setBusinessWindowKey(String businessWindowKey) { this.businessWindowKey = businessWindowKey; }
    public TaskState getState() { return state; }
    public void setState(TaskState state) { this.state = state; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public String getRemoteHandle() { return remoteHandle; }
    public void setRemoteHandle(String remoteHandle) { this.remoteHandle = remoteHandle; }
    public String getCheckpoint() { return checkpoint; }
    public void setCheckpoint(String checkpoint) { this.checkpoint = checkpoint; }
    public LocalDateTime getRetryNotBefore() { return retryNotBefore; }
    public void setRetryNotBefore(LocalDateTime retryNotBefore) { this.retryNotBefore = retryNotBefore; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public Long getFenceEpoch() { return fenceEpoch; }
    public void setFenceEpoch(Long fenceEpoch) { this.fenceEpoch = fenceEpoch; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getSanitizedFailureCode() { return sanitizedFailureCode; }
    public void setSanitizedFailureCode(String sanitizedFailureCode) {
        this.sanitizedFailureCode = sanitizedFailureCode;
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
