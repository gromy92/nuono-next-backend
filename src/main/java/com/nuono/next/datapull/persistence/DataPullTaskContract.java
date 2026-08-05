package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

final class DataPullTaskContract {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}");
    private static final int PROVIDER_CHANNEL_MAX_LENGTH = 64;
    private static final int ACCOUNT_KEY_MAX_LENGTH = 160;
    private static final int EGRESS_KEY_MAX_LENGTH = 160;
    private static final int SCOPE_KEY_MAX_LENGTH = 96;
    private static final int BUSINESS_WINDOW_KEY_MAX_LENGTH = 160;
    private static final int STEP_CODE_MAX_LENGTH = 80;

    private DataPullTaskContract() {
    }

    static void requireEnqueueable(DataPullTask task) {
        DataPullTask nonNull = Objects.requireNonNull(task, "task");
        if (nonNull.getId() == null || nonNull.getId() <= 0L) {
            throw new IllegalArgumentException("task id must be positive");
        }
        Objects.requireNonNull(nonNull.getOperationCode(), "operationCode");
        requireIdentity(nonNull.getProviderChannel(), "providerChannel", PROVIDER_CHANNEL_MAX_LENGTH);
        requirePositive(nonNull.getOwnerUserId(), "ownerUserId");
        optionalPositive(nonNull.getLogicalStoreId(), "logicalStoreId");
        requireIdentity(nonNull.getAccountKey(), "accountKey", ACCOUNT_KEY_MAX_LENGTH);
        optionalIdentity(nonNull.getEgressKey(), "egressKey", EGRESS_KEY_MAX_LENGTH);
        optionalIdentity(nonNull.getProjectCode(), "projectCode", 100);
        optionalIdentity(nonNull.getStoreCode(), "storeCode", 100);
        optionalIdentity(nonNull.getSiteCode(), "siteCode", 20);
        requireIdentity(nonNull.getScopeKey(), "scopeKey", SCOPE_KEY_MAX_LENGTH);
        Objects.requireNonNull(nonNull.getScheduleSlot(), "scheduleSlot");
        requireIdentity(nonNull.getBusinessWindowKey(), "businessWindowKey", BUSINESS_WINDOW_KEY_MAX_LENGTH);
        requireIdentity(nonNull.getStepCode(), "stepCode", STEP_CODE_MAX_LENGTH);
        Objects.requireNonNull(nonNull.getCreatedAt(), "createdAt");
        Objects.requireNonNull(nonNull.getUpdatedAt(), "updatedAt");
        if (nonNull.getState() != TaskState.QUEUED
                || !Integer.valueOf(0).equals(nonNull.getAttempt())
                || !Long.valueOf(0L).equals(nonNull.getFenceEpoch())
                || !Long.valueOf(0L).equals(nonNull.getVersion())
                || nonNull.getLeaseOwner() != null
                || nonNull.getLeaseUntil() != null
                || nonNull.getRetryNotBefore() != null
                || nonNull.getFinishedAt() != null) {
            throw new IllegalArgumentException("new tasks must start as an unfenced QUEUED task");
        }
    }

    static void requireSameImmutablePayload(DataPullTask existing, DataPullTask requested) {
        if (existing.getOperationCode() != requested.getOperationCode()
                || !existing.getProviderChannel().equals(requested.getProviderChannel())
                || !Objects.equals(existing.getOwnerUserId(), requested.getOwnerUserId())
                || !Objects.equals(existing.getLogicalStoreId(), requested.getLogicalStoreId())
                || !existing.getAccountKey().equals(requested.getAccountKey())
                || !Objects.equals(existing.getEgressKey(), requested.getEgressKey())
                || !Objects.equals(existing.getProjectCode(), requested.getProjectCode())
                || !Objects.equals(existing.getStoreCode(), requested.getStoreCode())
                || !Objects.equals(existing.getSiteCode(), requested.getSiteCode())
                || !existing.getScopeKey().equals(requested.getScopeKey())
                || !existing.getScheduleSlot().equals(requested.getScheduleSlot())
                || !existing.getBusinessWindowKey().equals(requested.getBusinessWindowKey())
                || bindingConflicts(existing, requested)) {
            throw new IllegalStateException("stable task key resolved to a conflicting immutable payload");
        }
    }

    static void requirePersistedScopeSnapshot(DataPullTask task) {
        DataPullTask value = Objects.requireNonNull(task, "task");
        boolean required = value.getOperationCode() == com.nuono.next.datapull.runtime.OperationCode.DP08A
                || value.getOperationCode() == com.nuono.next.datapull.runtime.OperationCode.DP08B;
        boolean absent = value.getScopeBindingId() == null
                && value.getScopePayloadType() == null
                && value.getScopePayloadSha256() == null
                && value.getScopePayload() == null
                && value.getScopeBindingEffectiveFromUtc() == null;
        if (!required) {
            if (!absent) {
                throw new IllegalStateException("non-DP08 task carries an unexpected scope snapshot");
            }
            return;
        }
        if (absent) {
            throw new IllegalStateException("DP task is missing its immutable scope snapshot");
        }
        DataPullTaskScopeSnapshot.requirePayload(
                value, value.getOperationCode(), value.getScopePayloadType()
        );
    }

    private static boolean bindingConflicts(DataPullTask existing, DataPullTask requested) {
        if (requested.getScopeBindingId() == null
                && requested.getScopePayloadType() == null
                && requested.getScopePayloadSha256() == null
                && requested.getScopePayload() == null
                && requested.getScopeBindingEffectiveFromUtc() == null) {
            return false;
        }
        return !Objects.equals(existing.getScopeBindingId(), requested.getScopeBindingId())
                || !Objects.equals(existing.getScopePayloadType(), requested.getScopePayloadType())
                || !Objects.equals(existing.getScopePayloadSha256(), requested.getScopePayloadSha256())
                || !Objects.equals(existing.getScopePayload(), requested.getScopePayload())
                || !Objects.equals(
                        existing.getScopeBindingEffectiveFromUtc(),
                        requested.getScopeBindingEffectiveFromUtc()
                );
    }

    static String stableKey(DataPullTask task) {
        return lengthPrefixed(task.getOperationCode().name())
                + lengthPrefixed(task.getScopeKey())
                + lengthPrefixed(task.getBusinessWindowKey());
    }

    static boolean isStrictlyNeverStarted(DataPullTask task) {
        return task != null
                && task.getState() == TaskState.QUEUED
                && Long.valueOf(0L).equals(task.getFenceEpoch())
                && task.getCheckpoint() == null
                && task.getRemoteHandle() == null
                && task.getLeaseOwner() == null
                && task.getLeaseUntil() == null;
    }

    static String requireIdentity(String value, String name) {
        return DataPullTaskTransition.requireIdentity(value, name);
    }

    static String requireIdentity(String value, String name, int maxLength) {
        String identity = requireIdentity(value, name);
        if (identity.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds its persistence column");
        }
        return identity;
    }

    private static void optionalIdentity(String value, String name) {
        if (value != null) {
            requireIdentity(value, name);
        }
    }

    private static void optionalIdentity(String value, String name, int maxLength) {
        if (value != null) {
            requireIdentity(value, name, maxLength);
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void optionalPositive(Long value, String name) {
        if (value != null) {
            requirePositive(value, name);
        }
    }

    static String optionalSanitizedCode(String value) {
        if (value != null && !SAFE_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("sanitizedFailureCode must be a safe identifier");
        }
        return value;
    }

    static void requireClaimRequest(
            long taskId,
            long expectedVersion,
            String leaseOwner,
            LocalDateTime leaseUntil,
            LocalDateTime now
    ) {
        if (taskId <= 0L || expectedVersion < 0L) {
            throw new IllegalArgumentException("task id and expected version must be valid");
        }
        requireIdentity(leaseOwner, "leaseOwner");
        LocalDateTime nonNullNow = Objects.requireNonNull(now, "now");
        if (Objects.requireNonNull(leaseUntil, "leaseUntil").compareTo(nonNullNow) <= 0) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
    }

    static void requireHeartbeatRequest(
            long taskId,
            long expectedFenceEpoch,
            long expectedVersion,
            String leaseOwner,
            LocalDateTime leaseUntil,
            LocalDateTime now
    ) {
        requireClaimRequest(taskId, expectedVersion, leaseOwner, leaseUntil, now);
        if (expectedFenceEpoch <= 0L) {
            throw new IllegalArgumentException("expectedFenceEpoch must identify a claimed epoch");
        }
    }

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value + "|";
    }
}
