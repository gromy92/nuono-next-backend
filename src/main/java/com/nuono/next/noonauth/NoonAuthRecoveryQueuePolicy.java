package com.nuono.next.noonauth;

import com.nuono.next.noonpull.NoonPullTaskRecord;
import java.util.Objects;
import org.springframework.util.StringUtils;

final class NoonAuthRecoveryQueuePolicy {
    private NoonAuthRecoveryQueuePolicy() {
    }

    static boolean sourceTaskPredatesCurrentAuth(
            NoonPullTaskRecord sourceTask,
            NoonProjectAuthStateRecord stateBeforeEnqueue
    ) {
        return sourceTask != null
                && sourceTask.getStartedAt() != null
                && stateBeforeEnqueue != null
                && stateBeforeEnqueue.getStatus() == NoonProjectAuthStatus.HEALTHY
                && stateBeforeEnqueue.getActiveRecoveryId() == null
                && stateBeforeEnqueue.getAuthVersion() != null
                && stateBeforeEnqueue.getAuthVersion() > 0L
                && stateBeforeEnqueue.getLastSuccessAt() != null
                && !stateBeforeEnqueue.getLastSuccessAt().isBefore(sourceTask.getStartedAt());
    }

    static NoonAuthRecoveryItemRecord resolveCommittedProjectJoin(
            NoonAuthRecoveryRepository repository,
            NoonProjectAuthStateRecord state,
            NoonAuthIdentityRecoveryRecord activeRecovery,
            Long ownerUserId,
            String projectCode
    ) {
        if (state == null
                || state.getStatus() != NoonProjectAuthStatus.HEALTHY
                || state.getActiveRecoveryId() != null
                || state.getAuthVersion() == null
                || activeRecovery == null
                || activeRecovery.getId() == null) {
            return null;
        }
        NoonAuthRecoveryItemRecord existing = repository.selectProjectRecoveryItem(
                activeRecovery.getId(),
                ownerUserId,
                projectCode
        );
        if (existing == null
                || existing.getExpectedAuthVersion() == null
                || state.getAuthVersion() <= existing.getExpectedAuthVersion()) {
            return null;
        }
        return existing;
    }

    static boolean keepsManualHold(
            NoonAuthRecoveryRepository repository,
            NoonProjectAuthStateRecord state,
            String bindingFingerprint,
            String configFingerprint
    ) {
        if (state == null || state.getStatus() != NoonProjectAuthStatus.MANUAL_HOLD) {
            return false;
        }
        if (!StringUtils.hasText(state.getBindingFingerprint())
                || !StringUtils.hasText(state.getConfigFingerprint())) {
            return true;
        }
        boolean bindingChanged = !Objects.equals(state.getBindingFingerprint(), bindingFingerprint);
        boolean configChanged = !Objects.equals(state.getConfigFingerprint(), configFingerprint);
        if (!bindingChanged && !configChanged) {
            return true;
        }
        if (state.getActiveRecoveryId() == null) {
            return false;
        }
        NoonAuthIdentityRecoveryRecord bound = repository.selectRecovery(state.getActiveRecoveryId());
        return bound != null
                && bound.getStatus() == NoonAuthRecoveryStatus.MANUAL_HOLD
                && !configChanged;
    }

    static Long resolveProjectBoundRecovery(
            NoonProjectAuthStateRecord state,
            NoonAuthIdentityRecoveryRecord activeRecovery,
            NoonAuthIdentityRecoveryRecord waitingSuccessor,
            String identityKey
    ) {
        if (state == null
                || state.getStatus() == null
                || !state.getStatus().blocksProviderCalls()
                || state.getActiveRecoveryId() == null
                || !identityKey.equals(state.getIdentityKey())) {
            return null;
        }
        if (state.getActiveRecoveryId().equals(activeRecovery.getId())) {
            return activeRecovery.getId();
        }
        if (waitingSuccessor == null
                || !state.getActiveRecoveryId().equals(waitingSuccessor.getId())
                || waitingSuccessor.getStatus() != NoonAuthRecoveryStatus.WAITING_PREDECESSOR
                || !identityKey.equals(waitingSuccessor.getIdentityKey())
                || !activeRecovery.getId().equals(waitingSuccessor.getPredecessorRecoveryId())) {
            return null;
        }
        return waitingSuccessor.getId();
    }

    static boolean keepsManualHoldWithLockedRecoveries(
            NoonProjectAuthStateRecord state,
            String bindingFingerprint,
            String configFingerprint,
            NoonAuthIdentityRecoveryRecord activeRecovery,
            NoonAuthIdentityRecoveryRecord waitingSuccessor
    ) {
        if (state == null || state.getStatus() != NoonProjectAuthStatus.MANUAL_HOLD) {
            return false;
        }
        if (!StringUtils.hasText(state.getBindingFingerprint())
                || !StringUtils.hasText(state.getConfigFingerprint())) {
            return true;
        }
        boolean bindingChanged = !Objects.equals(state.getBindingFingerprint(), bindingFingerprint);
        boolean configChanged = !Objects.equals(state.getConfigFingerprint(), configFingerprint);
        if (!bindingChanged && !configChanged) {
            return true;
        }
        if (configChanged || state.getActiveRecoveryId() == null) {
            return false;
        }
        if (activeRecovery != null && state.getActiveRecoveryId().equals(activeRecovery.getId())) {
            return activeRecovery.getStatus() == NoonAuthRecoveryStatus.MANUAL_HOLD;
        }
        return waitingSuccessor != null
                && state.getActiveRecoveryId().equals(waitingSuccessor.getId())
                && waitingSuccessor.getStatus() == NoonAuthRecoveryStatus.MANUAL_HOLD;
    }
}
