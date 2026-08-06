package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.Objects;

/** Secret-free context for exactly one snapshot page read. */
public final class SnapshotPageRequest {
    private final long taskId;
    private final long fenceEpoch;
    private final OperationCode operationCode;
    private final String providerChannel;
    private final long ownerUserId;
    private final Long logicalStoreId;
    private final String accountKey;
    private final String egressKey;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final String scopeKey;
    private final String businessWindowKey;
    private final int pageNo;

    private SnapshotPageRequest(DataPullTask task, int pageNo) {
        this.taskId = requirePositive(task.getId(), "task.id");
        this.fenceEpoch = requirePositive(task.getFenceEpoch(), "task.fenceEpoch");
        this.operationCode = Objects.requireNonNull(task.getOperationCode(), "task.operationCode");
        this.providerChannel = requireIdentity(task.getProviderChannel(), "task.providerChannel");
        this.ownerUserId = requirePositive(task.getOwnerUserId(), "task.ownerUserId");
        this.logicalStoreId = optionalPositive(task.getLogicalStoreId(), "task.logicalStoreId");
        this.accountKey = requireIdentity(task.getAccountKey(), "task.accountKey");
        this.egressKey = optionalIdentity(task.getEgressKey(), "task.egressKey");
        this.projectCode = optionalIdentity(task.getProjectCode(), "task.projectCode");
        this.storeCode = optionalIdentity(task.getStoreCode(), "task.storeCode");
        this.siteCode = optionalIdentity(task.getSiteCode(), "task.siteCode");
        this.scopeKey = requireIdentity(task.getScopeKey(), "task.scopeKey");
        this.businessWindowKey = requireIdentity(task.getBusinessWindowKey(), "task.businessWindowKey");
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be positive");
        }
        this.pageNo = pageNo;
    }

    public static SnapshotPageRequest from(DataPullTask task, int pageNo) {
        return new SnapshotPageRequest(Objects.requireNonNull(task, "task"), pageNo);
    }

    private static long requirePositive(Long value, String name) {
        Long nonNull = Objects.requireNonNull(value, name);
        if (nonNull < 1L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return nonNull;
    }

    private static Long optionalPositive(Long value, String name) {
        return value == null ? null : requirePositive(value, name);
    }

    private static String requireIdentity(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(name + " must be a non-blank stable identity");
        }
        return nonNull;
    }

    private static String optionalIdentity(String value, String name) {
        return value == null ? null : requireIdentity(value, name);
    }

    public long getTaskId() {
        return taskId;
    }

    public long getFenceEpoch() {
        return fenceEpoch;
    }

    public OperationCode getOperationCode() {
        return operationCode;
    }

    public String getProviderChannel() {
        return providerChannel;
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public String getEgressKey() {
        return egressKey;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public String getBusinessWindowKey() {
        return businessWindowKey;
    }

    public int getPageNo() {
        return pageNo;
    }
}
