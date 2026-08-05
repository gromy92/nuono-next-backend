package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Immutable create intent derived only from the persisted task scope and business window. */
public final class ExportReportIntent {

    private static final String KEY_VERSION = "dpr-v1-";

    private final OperationCode operationCode;
    private final long taskId;
    private final long fenceEpoch;
    private final String leaseOwner;
    private final String providerChannel;
    private final long ownerUserId;
    private final Long logicalStoreId;
    private final String accountKey;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final String scopeKey;
    private final String businessWindowKey;
    private final String stableRequestKey;

    private ExportReportIntent(DataPullTask task) {
        Long persistedTaskId = Objects.requireNonNull(task.getId(), "taskId");
        Long persistedFence = Objects.requireNonNull(task.getFenceEpoch(), "fenceEpoch");
        if (persistedTaskId < 1L || persistedFence < 1L) {
            throw new IllegalArgumentException("report task and fence identities must be positive");
        }
        this.taskId = persistedTaskId;
        this.fenceEpoch = persistedFence;
        this.leaseOwner = ReportContract.requireIdentity(task.getLeaseOwner(), "leaseOwner");
        this.operationCode = Objects.requireNonNull(task.getOperationCode(), "operationCode");
        this.providerChannel = ReportContract.requireIdentity(
                task.getProviderChannel(),
                "providerChannel"
        );
        Long owner = Objects.requireNonNull(task.getOwnerUserId(), "ownerUserId");
        if (owner <= 0L) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        if (task.getLogicalStoreId() != null && task.getLogicalStoreId() <= 0L) {
            throw new IllegalArgumentException("logicalStoreId must be positive when present");
        }
        this.ownerUserId = owner;
        this.logicalStoreId = task.getLogicalStoreId();
        this.accountKey = ReportContract.requireIdentity(task.getAccountKey(), "accountKey");
        this.projectCode = ReportContract.optionalIdentity(task.getProjectCode(), "projectCode");
        this.storeCode = ReportContract.optionalIdentity(task.getStoreCode(), "storeCode");
        this.siteCode = ReportContract.optionalIdentity(task.getSiteCode(), "siteCode");
        this.scopeKey = ReportContract.requireIdentity(task.getScopeKey(), "scopeKey");
        this.businessWindowKey = ReportContract.requireIdentity(
                task.getBusinessWindowKey(),
                "businessWindowKey"
        );
        this.stableRequestKey = stableKey();
    }

    public static ExportReportIntent from(ExecutionContext context) {
        return new ExportReportIntent(Objects.requireNonNull(context, "context").getTask());
    }

    private String stableKey() {
        String material = part(operationCode.name())
                + part(providerChannel)
                + part(String.valueOf(ownerUserId))
                + part(accountKey)
                + part(projectCode)
                + part(storeCode)
                + part(siteCode)
                + part(scopeKey)
                + part(businessWindowKey);
        return KEY_VERSION + hex(sha256(material.getBytes(StandardCharsets.UTF_8)));
    }

    private static String part(String value) {
        String normalized = value == null ? "<NULL>" : value;
        return normalized.length() + ":" + normalized + "|";
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 must be available", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    public OperationCode getOperationCode() { return operationCode; }
    public long getTaskId() { return taskId; }
    public long getFenceEpoch() { return fenceEpoch; }
    public String getLeaseOwner() { return leaseOwner; }
    public String getProviderChannel() { return providerChannel; }
    public long getOwnerUserId() { return ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public String getAccountKey() { return accountKey; }
    public String getProjectCode() { return projectCode; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getScopeKey() { return scopeKey; }
    public String getBusinessWindowKey() { return businessWindowKey; }
    public String getStableRequestKey() { return stableRequestKey; }
}
