package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Immutable, completeness-proven batch passed to the atomic replacement Seam. */
public final class CompleteSnapshot<T> {
    private static final int LEASE_OWNER_MAX_LENGTH = 200;

    private final long taskId;
    private final long fenceEpoch;
    private final String leaseOwner;
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
    private final LocalDateTime scheduleSlot;
    private final int lastPage;
    private final List<T> items;
    private final long appliedItemCount;
    private final int skippedIdentityCount;
    private final long businessSkippedItemCount;
    private final long sourceItemCount;
    private final SnapshotCollectionAuthority authority;

    private CompleteSnapshot(DataPullTask task, SnapshotStageProof<T> proof) {
        this.taskId = Objects.requireNonNull(task.getId(), "task.id");
        this.fenceEpoch = Objects.requireNonNull(task.getFenceEpoch(), "task.fenceEpoch");
        this.leaseOwner = requireIdentity(task.getLeaseOwner(), "task.leaseOwner");
        this.operationCode = Objects.requireNonNull(task.getOperationCode(), "task.operationCode");
        this.providerChannel = Objects.requireNonNull(task.getProviderChannel(), "task.providerChannel");
        this.ownerUserId = Objects.requireNonNull(task.getOwnerUserId(), "task.ownerUserId");
        this.logicalStoreId = task.getLogicalStoreId();
        this.accountKey = Objects.requireNonNull(task.getAccountKey(), "task.accountKey");
        this.egressKey = task.getEgressKey();
        this.projectCode = task.getProjectCode();
        this.storeCode = task.getStoreCode();
        this.siteCode = task.getSiteCode();
        this.scopeKey = Objects.requireNonNull(task.getScopeKey(), "task.scopeKey");
        this.businessWindowKey = Objects.requireNonNull(
                task.getBusinessWindowKey(),
                "task.businessWindowKey"
        );
        this.scheduleSlot = Objects.requireNonNull(task.getScheduleSlot(), "task.scheduleSlot");
        if (!proof.isComplete()) {
            throw new IllegalArgumentException("snapshot proof must be complete");
        }
        this.lastPage = proof.getLastPage().orElseThrow();
        this.items = List.copyOf(proof.getItems());
        this.appliedItemCount = proof.getAppliedItemCount();
        this.skippedIdentityCount = proof.getSkippedIdentityCount();
        this.businessSkippedItemCount = proof.getBusinessSkippedItemCount();
        this.sourceItemCount = proof.getSourceItemCount();
        this.authority = proof.getAuthority().orElseThrow(
                () -> new IllegalArgumentException("snapshot proof authority is required")
        );
        if (authority.getDeclaredCollectionCount() != sourceItemCount) {
            throw new IllegalArgumentException("snapshot proof authority extent drift");
        }
    }

    private static String requireIdentity(String value, String name) {
        String identity = Objects.requireNonNull(value, name);
        if (identity.isEmpty() || !identity.equals(identity.trim())) {
            throw new IllegalArgumentException(name + " must be a non-blank stable identity");
        }
        if (identity.length() > LEASE_OWNER_MAX_LENGTH) {
            throw new IllegalArgumentException(name + " exceeds its persistence column");
        }
        return identity;
    }

    public static <T> CompleteSnapshot<T> from(DataPullTask task, SnapshotStageProof<T> proof) {
        return new CompleteSnapshot<>(
                Objects.requireNonNull(task, "task"),
                Objects.requireNonNull(proof, "proof")
        );
    }

    public long getTaskId() {
        return taskId;
    }

    public long getFenceEpoch() {
        return fenceEpoch;
    }

    public String getLeaseOwner() {
        return leaseOwner;
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

    public LocalDateTime getScheduleSlot() {
        return scheduleSlot;
    }

    public int getLastPage() {
        return lastPage;
    }

    public List<T> getItems() {
        return items;
    }

    public long getAppliedItemCount() {
        return appliedItemCount;
    }

    public int getSkippedIdentityCount() {
        return skippedIdentityCount;
    }

    public long getBusinessSkippedItemCount() {
        return businessSkippedItemCount;
    }

    public long getSourceItemCount() {
        return sourceItemCount;
    }

    public SnapshotCollectionAuthority getAuthority() {
        return authority;
    }
}
