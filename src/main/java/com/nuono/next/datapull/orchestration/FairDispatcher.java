package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.persistence.DataPullUnstartedClaimRelease;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Claims due tasks round-robin across provider/account/operation/scope buckets. */
public final class FairDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(FairDispatcher.class);

    private static final int MINIMUM_SCAN = 64;
    private static final int SCAN_MULTIPLIER = 16;
    private static final int MAXIMUM_SCAN = 10_000;

    private final DataPullTaskStore store;
    private final BackoffHoldGate backoffHoldGate;
    private final EmergencyClaimHoldStore emergencyClaimHolds;
    private final Deque<DataPullTask> pendingRotation = new ArrayDeque<>();
    private CandidateCursor scanCursor;

    public FairDispatcher(
            DataPullTaskStore store,
            BackoffHoldGate backoffHoldGate,
            EmergencyClaimHoldStore emergencyClaimHolds
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.backoffHoldGate = Objects.requireNonNull(backoffHoldGate, "backoffHoldGate");
        this.emergencyClaimHolds = Objects.requireNonNull(
                emergencyClaimHolds,
                "emergencyClaimHolds"
        );
    }

    public synchronized List<DataPullTask> dispatchDue(
            LocalDateTime nowUtc,
            int maximumClaims,
            Duration leaseDuration,
            DataPullRuntimeLeaderLease leaderLease
    ) {
        LocalDateTime now = Objects.requireNonNull(nowUtc, "nowUtc");
        if (maximumClaims <= 0) {
            throw new IllegalArgumentException("maximumClaims must be positive");
        }
        DataPullRuntimeLeaderLease leadership = Objects.requireNonNull(
                leaderLease,
                "leaderLease"
        );
        String leaseOwner = leadership.getOwner();
        Duration duration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }

        DataPullAdvanceDeadline.requireRemaining();
        EmergencyClaimHoldSnapshot emergencyHolds = emergencyClaimHolds.activeAt(now);
        if (emergencyHolds.blocksAllClaims()) {
            return List.of();
        }
        refillRotation(now, scanLimit(maximumClaims), emergencyHolds);

        LocalDateTime leaseUntil = now.plus(duration);
        List<DataPullTask> claimed = new ArrayList<>();
        while (claimed.size() < maximumClaims && !pendingRotation.isEmpty()) {
            if (deadlineExpired()) break;
            DataPullTask candidate = pendingRotation.removeFirst();
            try {
                if (isHeld(candidate, now, emergencyHolds)) {
                    continue;
                }
                store.claim(
                        candidate.getId(),
                        candidate.getVersion(),
                        leaseOwner,
                        leaseUntil,
                        now,
                        leadership
                ).ifPresent(claimed::add);
            } catch (RuntimeException claimFailure) {
                if (deadlineExpired()) break;
                LOGGER.warn(
                        "DP dispatcher could not claim one candidate taskId={} operation={} errorType={}",
                        candidate.getId(),
                        candidate.getOperationCode(),
                        claimFailure.getClass().getSimpleName()
                );
            }
        }
        return List.copyOf(claimed);
    }

    boolean releaseUnstartedClaim(DataPullTask claimed, java.time.Instant observedAt) {
        LocalDateTime now = LocalDateTime.ofInstant(
                Objects.requireNonNull(observedAt, "observedAt"),
                java.time.ZoneOffset.UTC
        );
        return store.releaseUnstartedClaim(DataPullUnstartedClaimRelease.from(claimed, now));
    }

    private boolean deadlineExpired() {
        DataPullAdvanceDeadline deadline = DataPullAdvanceDeadline.current();
        return deadline != null && deadline.isExpired();
    }

    private void refillRotation(
            LocalDateTime now,
            int targetCount,
            EmergencyClaimHoldSnapshot emergencyHolds
    ) {
        if (!pendingRotation.isEmpty()) {
            return;
        }
        List<DataPullTask> eligible = new ArrayList<>(targetCount);
        int scanned = 0;
        boolean wrapped = false;
        while (eligible.size() < targetCount && scanned < MAXIMUM_SCAN) {
            DataPullAdvanceDeadline.requireRemaining();
            int pageLimit = Math.min(MINIMUM_SCAN, MAXIMUM_SCAN - scanned);
            List<DataPullTask> page = store.dueCandidatesAfter(
                    now,
                    scanCursor == null ? null : scanCursor.scheduleSlot,
                    scanCursor == null ? null : scanCursor.taskId,
                    pageLimit
            );
            if (page.isEmpty()) {
                if (scanCursor != null && !wrapped) {
                    scanCursor = null;
                    wrapped = true;
                    continue;
                }
                break;
            }
            scanned += page.size();
            for (DataPullTask candidate : page) {
                DataPullAdvanceDeadline.requireRemaining();
                if (eligible.size() >= targetCount) {
                    break;
                }
                scanCursor = new CandidateCursor(candidate.getScheduleSlot(), candidate.getId());
                addIfEligible(eligible, candidate, now, emergencyHolds);
            }
            if (page.size() < pageLimit) {
                break;
            }
        }
        appendRoundRobin(eligible);
    }

    private void appendRoundRobin(List<DataPullTask> candidates) {
        Map<BucketKey, Deque<DataPullTask>> buckets = new LinkedHashMap<>();
        for (DataPullTask candidate : candidates) {
            buckets.computeIfAbsent(BucketKey.from(candidate), ignored -> new ArrayDeque<>())
                    .addLast(candidate);
        }
        boolean appended = true;
        while (appended) {
            DataPullAdvanceDeadline.requireRemaining();
            appended = false;
            for (Deque<DataPullTask> bucket : buckets.values()) {
                DataPullTask candidate = bucket.pollFirst();
                if (candidate != null) {
                    pendingRotation.addLast(candidate);
                    appended = true;
                }
            }
        }
    }

    private void addIfEligible(
            List<DataPullTask> eligible,
            DataPullTask candidate,
            LocalDateTime now,
            EmergencyClaimHoldSnapshot emergencyHolds
    ) {
        try {
            DataPullScope.fromTaskSnapshot(candidate);
            if (!isHeld(candidate, now, emergencyHolds)) {
                eligible.add(candidate);
            }
        } catch (RuntimeException invalidCandidate) {
            LOGGER.warn(
                    "DP dispatcher skipped one invalid candidate taskId={} operation={} errorType={}",
                    candidate == null ? null : candidate.getId(),
                    candidate == null ? null : candidate.getOperationCode(),
                    invalidCandidate.getClass().getSimpleName()
            );
        }
    }

    private boolean isHeld(
            DataPullTask task,
            LocalDateTime nowUtc,
            EmergencyClaimHoldSnapshot emergencyHolds
    ) {
        if (emergencyHolds.isClaimHeld(task.getOperationCode(), task.getScopeKey())) {
            return true;
        }
        DataPullBackoffIdentity identity = DataPullBackoffIdentity.from(task);
        if (backoffHoldGate.isHeld(RiskShareLevel.EXACT, identity, nowUtc)
                || backoffHoldGate.isHeld(RiskShareLevel.ACCOUNT, identity, nowUtc)) {
            return true;
        }
        return identity.getEgressKey() != null
                && backoffHoldGate.isHeld(RiskShareLevel.EXIT, identity, nowUtc);
    }

    private static int scanLimit(int maximumClaims) {
        long multiplied = (long) maximumClaims * SCAN_MULTIPLIER;
        return (int) Math.min(MAXIMUM_SCAN, Math.max(MINIMUM_SCAN, multiplied));
    }

    private static final class BucketKey {
        private final String providerChannel;
        private final String accountKey;
        private final OperationCode operationCode;
        private final String scopeKey;

        private BucketKey(
                String providerChannel,
                String accountKey,
                OperationCode operationCode,
                String scopeKey
        ) {
            this.providerChannel = providerChannel;
            this.accountKey = accountKey;
            this.operationCode = operationCode;
            this.scopeKey = scopeKey;
        }

        static BucketKey from(DataPullTask task) {
            return new BucketKey(
                    task.getProviderChannel(),
                    task.getAccountKey(),
                    task.getOperationCode(),
                    task.getScopeKey()
            );
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BucketKey)) {
                return false;
            }
            BucketKey that = (BucketKey) other;
            return providerChannel.equals(that.providerChannel)
                    && accountKey.equals(that.accountKey)
                    && operationCode == that.operationCode
                    && scopeKey.equals(that.scopeKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerChannel, accountKey, operationCode, scopeKey);
        }
    }

    private static final class CandidateCursor {
        private final LocalDateTime scheduleSlot;
        private final long taskId;

        private CandidateCursor(LocalDateTime scheduleSlot, long taskId) {
            this.scheduleSlot = Objects.requireNonNull(scheduleSlot, "scheduleSlot");
            if (taskId <= 0L) {
                throw new IllegalArgumentException("taskId must be positive");
            }
            this.taskId = taskId;
        }
    }
}
