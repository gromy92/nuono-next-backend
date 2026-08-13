package com.nuono.next.noonauth;

import java.time.LocalDateTime;
import java.util.Objects;

final class NoonAuthRecoveryQueuePolicy {
    private NoonAuthRecoveryQueuePolicy() {
    }

    static boolean sourceTaskPredatesCurrentAuth(
            LocalDateTime sourceStartedAt,
            NoonProjectAuthStateRecord stateBeforeEnqueue
    ) {
        return sourceStartedAt != null
                && stateBeforeEnqueue != null
                && stateBeforeEnqueue.getStatus() == NoonProjectAuthStatus.HEALTHY
                && stateBeforeEnqueue.getActiveRecoveryId() == null
                && stateBeforeEnqueue.getAuthVersion() != null
                && stateBeforeEnqueue.getAuthVersion() > 0L
                && stateBeforeEnqueue.getLastSuccessAt() != null
                && !stateBeforeEnqueue.getLastSuccessAt().isBefore(sourceStartedAt);
    }

    static boolean startsFreshIdentityRenewal(
            NoonProjectAuthStateRecord state,
            Long sourceTaskId,
            boolean explicitBinding,
            String configFingerprint
    ) {
        if (explicitBinding
                || sourceTaskId == null
                || state == null
                || state.getStatus() != NoonProjectAuthStatus.MANUAL_HOLD) {
            return false;
        }
        if (!Objects.equals(state.getConfigFingerprint(), configFingerprint)) {
            return false;
        }
        return state.getLastFailureTaskId() == null
                || !sourceTaskId.equals(state.getLastFailureTaskId());
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

    static Long resolveProjectBoundRecovery(
            NoonProjectAuthStateRecord state,
            NoonAuthIdentityRecoveryRecord activeRecovery,
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
        return null;
    }
}
