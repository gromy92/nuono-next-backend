package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("local-db")
public class MyBatisNoonAuthRecoveryRepository implements NoonAuthRecoveryRepository {
    private final NoonAuthRecoveryMapper mapper;

    public MyBatisNoonAuthRecoveryRepository(NoonAuthRecoveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long coalesceActiveRecovery(NoonAuthIdentityRecoveryRecord recovery) {
        mapper.coalesceActiveRecovery(recovery);
        return recovery.getId();
    }

    @Override
    public Long coalesceSuccessorRecovery(NoonAuthIdentityRecoveryRecord recovery) {
        mapper.coalesceSuccessorRecovery(recovery);
        return recovery.getId();
    }

    @Override
    public NoonAuthIdentityRecoveryRecord selectRecovery(Long recoveryId) {
        return mapper.selectRecovery(recoveryId);
    }

    @Override
    public NoonAuthIdentityRecoveryRecord selectRecoveryForUpdate(Long recoveryId) {
        return mapper.selectRecoveryForUpdate(recoveryId);
    }

    @Override
    public NoonAuthIdentityRecoveryRecord selectActiveRecovery(String identityKey) {
        return mapper.selectActiveRecovery(identityKey);
    }

    @Override
    public NoonAuthIdentityRecoveryRecord selectActiveRecoveryForUpdate(String identityKey) {
        return mapper.selectActiveRecoveryForUpdate(identityKey);
    }

    @Override
    public NoonAuthIdentityRecoveryRecord selectWaitingSuccessorForUpdate(String identityKey) {
        return mapper.selectWaitingSuccessorForUpdate(identityKey);
    }

    @Override
    public List<NoonAuthIdentityRecoveryRecord> listDueRecoveries(LocalDateTime now, int limit) {
        return mapper.listDueRecoveries(now, limit);
    }

    @Override
    public List<String> listUndrainedIdentityKeysExcept(String identityKey) {
        return mapper.listUndrainedIdentityKeysExcept(identityKey);
    }

    @Override
    public int promoteReadySuccessors(LocalDateTime coalesceUntil, LocalDateTime now) {
        return mapper.promoteReadySuccessors(coalesceUntil, now);
    }

    @Override
    @Transactional
    public int drainDisabledRecoveries(LocalDateTime now) {
        return drainRecoveries(
                null,
                "FEATURE_DISABLED",
                "automatic auth recovery disabled; original pull task requeued",
                now
        );
    }

    @Override
    @Transactional
    public int drainIdentityRecoveries(String identityKey, LocalDateTime now) {
        return drainRecoveries(
                identityKey,
                "IDENTITY_CONFIG_MISMATCH",
                "configured shared email identity changed; original pull task requeued",
                now
        );
    }

    private int drainRecoveries(
            String identityKey,
            String failureCode,
            String diagnosticSummary,
            LocalDateTime now
    ) {
        int cancelled = mapper.cancelRecoveriesForDrain(
                identityKey,
                failureCode,
                diagnosticSummary,
                now
        );
        mapper.skipItemsForDrainedRecoveries(
                identityKey,
                failureCode,
                diagnosticSummary,
                now
        );
        mapper.requeueTasksForDrainedRecoveries(identityKey, diagnosticSummary, now);
        mapper.releaseProjectsForDrainedRecoveries(identityKey, failureCode, now);
        return cancelled;
    }

    @Override
    public boolean tryClaimRecovery(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String leaseOwner,
            String leaseToken,
            LocalDateTime leaseUntil,
            LocalDateTime now
    ) {
        return mapper.tryClaimRecovery(
                recoveryId,
                expectedStatus,
                expectedVersion,
                leaseOwner,
                leaseToken,
                leaseUntil,
                now
        ) == 1;
    }

    @Override
    public boolean renewLease(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            LocalDateTime leaseUntil,
            LocalDateTime now
    ) {
        return mapper.renewLease(
                recoveryId,
                expectedStatus,
                expectedVersion,
                expectedLeaseToken,
                leaseUntil,
                now
        ) == 1;
    }

    @Override
    public boolean transitionRecovery(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            NoonAuthRecoveryStatus targetStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            LocalDateTime nextAttemptAt,
            String failureCode,
            String diagnosticSummary,
            LocalDateTime completedAt,
            boolean releaseLease,
            LocalDateTime now
    ) {
        return mapper.transitionRecovery(
                recoveryId,
                expectedStatus,
                targetStatus,
                expectedVersion,
                expectedLeaseToken,
                nextAttemptAt,
                failureCode,
                diagnosticSummary,
                completedAt,
                releaseLease,
                now
        ) == 1;
    }

    @Override
    @Transactional
    public boolean completeRecoveryIfDrained(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            String failureCode,
            String diagnosticSummary,
            LocalDateTime completedAt,
            LocalDateTime successorCoalesceUntil,
            LocalDateTime now
    ) {
        int completed = mapper.completeRecoveryIfDrained(
                recoveryId,
                expectedStatus,
                expectedVersion,
                expectedLeaseToken,
                failureCode,
                diagnosticSummary,
                completedAt,
                now
        );
        if (completed != 1) {
            return false;
        }
        mapper.promoteSuccessorForPredecessor(recoveryId, successorCoalesceUntil, now);
        return true;
    }

    @Override
    @Transactional
    public boolean releaseManualHoldOnConfigChange(
            String identityKey,
            String expectedConfigFingerprint,
            String newConfigFingerprint,
            LocalDateTime nextAttemptAt,
            LocalDateTime now
    ) {
        NoonAuthIdentityRecoveryRecord active = mapper.selectActiveRecoveryForUpdate(identityKey);
        if (active == null || active.getId() == null) {
            return false;
        }
        int released = mapper.releaseManualHoldOnConfigChange(
                identityKey,
                expectedConfigFingerprint,
                newConfigFingerprint,
                nextAttemptAt,
                now
        );
        if (released != 1) {
            return false;
        }
        mapper.releaseProjectManualHolds(active.getId(), newConfigFingerprint, now);
        mapper.reopenFailedRecoveryItems(active.getId(), now);
        return true;
    }

    @Override
    @Transactional
    public int releaseChangedManualHolds(
            String identityKey,
            String newConfigFingerprint,
            LocalDateTime nextAttemptAt,
            LocalDateTime now
    ) {
        NoonAuthIdentityRecoveryRecord active = mapper.selectActiveRecoveryForUpdate(identityKey);
        if (active == null || active.getId() == null) {
            return 0;
        }
        int released = mapper.releaseChangedManualHolds(
                identityKey,
                newConfigFingerprint,
                nextAttemptAt,
                now
        );
        if (released == 1) {
            mapper.releaseProjectManualHolds(active.getId(), newConfigFingerprint, now);
            mapper.reopenFailedRecoveryItems(active.getId(), now);
        }
        return released;
    }

    @Override
    @Transactional
    public int releaseTerminalProjectHoldsOnConfigChange(
            String identityKey,
            String newConfigFingerprint,
            LocalDateTime now
    ) {
        int requeued = mapper.requeueTerminalBlockedTasksOnConfigChange(
                identityKey,
                newConfigFingerprint,
                now
        );
        int staled = mapper.staleTerminalItemsOnConfigChange(
                identityKey,
                newConfigFingerprint,
                now
        );
        if (staled != requeued) {
            throw new IllegalStateException(
                    "Noon auth config change did not reconcile terminal blocked tasks."
            );
        }
        return mapper.releaseTerminalProjectHoldsOnConfigChange(
                identityKey,
                newConfigFingerprint,
                now
        );
    }

    @Override
    @Transactional
    public boolean recordSendIntent(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            LocalDateTime sendIntentAt,
            LocalDateTime now
    ) {
        int recorded = mapper.recordSendIntent(
                recoveryId,
                expectedStatus,
                expectedVersion,
                expectedLeaseToken,
                sendIntentAt,
                now
        );
        if (recorded != 1) {
            return false;
        }
        if (mapper.insertIdentitySendLedger(recoveryId, sendIntentAt, now) != 1) {
            throw new IllegalStateException("Noon auth send ledger did not record the fenced send intent.");
        }
        return true;
    }

    @Override
    public boolean recordMailboxCorrelation(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            String mailUidHash,
            String messageIdHash,
            LocalDateTime now
    ) {
        return mapper.recordMailboxCorrelation(
                recoveryId,
                expectedStatus,
                expectedVersion,
                expectedLeaseToken,
                mailUidHash,
                messageIdHash,
                now
        ) == 1;
    }

    @Override
    public LocalDateTime selectLatestIdentitySendAt(String identityKey) {
        return mapper.selectLatestIdentitySendAt(identityKey);
    }

    @Override
    @Transactional
    public Long rebaseProjectBindingEpoch(
            Long recoveryId,
            Long ownerUserId,
            String projectCode,
            String identityKey,
            String bindingFingerprint,
            String configFingerprint,
            LocalDateTime coalesceUntil,
            LocalDateTime cooldownAt,
            LocalDateTime now
    ) {
        NoonAuthIdentityRecoveryRecord target = mapper.selectRecoveryForUpdate(recoveryId);
        requireBindingEpochTarget(target, recoveryId, identityKey);

        NoonProjectAuthStateRecord state = mapper.selectProjectAuthStateForUpdate(ownerUserId, projectCode);
        NoonAuthRecoveryItemRecord sourceLessItem = mapper.selectSourceLessProjectRecoveryItemForUpdate(
                recoveryId,
                ownerUserId,
                projectCode
        );
        if (bindingEpochAlreadyQueued(
                state,
                sourceLessItem,
                recoveryId,
                identityKey,
                bindingFingerprint,
                configFingerprint
        )) {
            return state.getAuthVersion();
        }

        int recoveryRebased;
        if (target.getStatus() == NoonAuthRecoveryStatus.WAITING_PREDECESSOR) {
            recoveryRebased = mapper.rebaseWaitingSuccessorForBindingEpoch(
                    recoveryId,
                    target.getVersionNo(),
                    configFingerprint,
                    now
            );
        } else {
            recoveryRebased = mapper.rebaseActiveRecoveryForBindingEpoch(
                    recoveryId,
                    target.getStatus(),
                    target.getVersionNo(),
                    configFingerprint,
                    coalesceUntil,
                    cooldownAt,
                    now
            );
        }
        if (recoveryRebased != 1) {
            throw new IllegalStateException("Noon auth recovery binding epoch lost its recovery fence.");
        }

        Long oldRecoveryId = state == null ? null : state.getActiveRecoveryId();
        if (mapper.rebaseProjectAuthStateForBindingEpoch(
                ownerUserId,
                projectCode,
                identityKey,
                recoveryId,
                bindingFingerprint,
                configFingerprint,
                now
        ) <= 0) {
            throw new IllegalStateException("Noon auth recovery binding epoch did not update project state.");
        }
        NoonProjectAuthStateRecord rebased = mapper.selectProjectAuthStateForUpdate(ownerUserId, projectCode);
        if (rebased == null
                || rebased.getAuthVersion() == null
                || rebased.getStatus() != NoonProjectAuthStatus.REAUTH_REQUIRED
                || !recoveryId.equals(rebased.getActiveRecoveryId())
                || !identityKey.equals(rebased.getIdentityKey())
                || !bindingFingerprint.equals(rebased.getBindingFingerprint())
                || !configFingerprint.equals(rebased.getConfigFingerprint())) {
            throw new IllegalStateException("Noon auth recovery binding epoch did not persist its project fence.");
        }

        mapper.reopenProjectItemsForBindingEpoch(
                recoveryId,
                ownerUserId,
                projectCode,
                rebased.getAuthVersion(),
                now
        );
        if (oldRecoveryId != null && !oldRecoveryId.equals(recoveryId)) {
            int requeued = mapper.requeueTerminalBlockedProjectTasksForBindingEpoch(
                    oldRecoveryId,
                    ownerUserId,
                    projectCode,
                    now
            );
            int staled = mapper.staleTerminalBlockedProjectItemsForBindingEpoch(
                    oldRecoveryId,
                    ownerUserId,
                    projectCode,
                    now
            );
            if (staled != requeued) {
                throw new IllegalStateException(
                        "Noon auth recovery binding epoch did not reconcile terminal blocked tasks."
                );
            }
        }
        return rebased.getAuthVersion();
    }

    @Override
    public boolean cancelEmptyRecoveryAfterRejectedEnqueue(Long recoveryId, LocalDateTime now) {
        return mapper.cancelEmptyRecoveryAfterRejectedEnqueue(recoveryId, now) == 1;
    }

    private void requireBindingEpochTarget(
            NoonAuthIdentityRecoveryRecord target,
            Long recoveryId,
            String identityKey
    ) {
        if (target == null
                || target.getId() == null
                || !recoveryId.equals(target.getId())
                || target.getStatus() == null
                || target.getStatus().isTerminal()
                || target.getVersionNo() == null
                || !identityKey.equals(target.getIdentityKey())
                || (target.getStatus() == NoonAuthRecoveryStatus.WAITING_PREDECESSOR
                && target.getPredecessorRecoveryId() == null)) {
            throw new IllegalStateException("Noon auth recovery binding epoch requires a live target recovery.");
        }
    }

    private boolean bindingEpochAlreadyQueued(
            NoonProjectAuthStateRecord state,
            NoonAuthRecoveryItemRecord sourceLessItem,
            Long recoveryId,
            String identityKey,
            String bindingFingerprint,
            String configFingerprint
    ) {
        return state != null
                && state.getStatus() != null
                && state.getStatus().blocksProviderCalls()
                && recoveryId.equals(state.getActiveRecoveryId())
                && Objects.equals(identityKey, state.getIdentityKey())
                && state.getAuthVersion() != null
                && Objects.equals(bindingFingerprint, state.getBindingFingerprint())
                && Objects.equals(configFingerprint, state.getConfigFingerprint())
                && sourceLessItem != null
                && sourceLessItem.getSourceTaskId() == null
                && sourceLessItem.getStatus() == NoonAuthRecoveryItemStatus.PENDING
                && state.getAuthVersion().equals(sourceLessItem.getExpectedAuthVersion());
    }

    @Override
    public void upsertProjectAuthRequired(
            Long ownerUserId,
            String projectCode,
            String identityKey,
            Long recoveryId,
            String bindingFingerprint,
            String configFingerprint,
            String failureCode,
            Long sourceTaskId,
            LocalDateTime now
    ) {
        mapper.upsertProjectAuthRequired(
                ownerUserId,
                projectCode,
                identityKey,
                recoveryId,
                bindingFingerprint,
                configFingerprint,
                failureCode,
                sourceTaskId,
                now
        );
    }

    @Override
    public String selectProjectBindingFingerprint(Long ownerUserId, String projectCode) {
        return mapper.selectProjectBindingFingerprint(ownerUserId, projectCode);
    }

    @Override
    public NoonProjectAuthStateRecord selectProjectAuthState(Long ownerUserId, String projectCode) {
        return mapper.selectProjectAuthState(ownerUserId, projectCode);
    }

    @Override
    public NoonProjectAuthStateRecord selectProjectAuthStateForUpdate(Long ownerUserId, String projectCode) {
        return mapper.selectProjectAuthStateForUpdate(ownerUserId, projectCode);
    }

    @Override
    public boolean markProjectRecovering(
            Long ownerUserId,
            String projectCode,
            Long recoveryId,
            Long expectedAuthVersion,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            LocalDateTime now
    ) {
        return mapper.markProjectRecovering(
                ownerUserId,
                projectCode,
                recoveryId,
                expectedAuthVersion,
                expectedRecoveryStatus,
                expectedRecoveryVersion,
                expectedLeaseToken,
                now
        ) == 1;
    }

    @Override
    public boolean markProjectRecoveryFailed(
            Long ownerUserId,
            String projectCode,
            Long recoveryId,
            Long expectedAuthVersion,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            NoonProjectAuthStatus targetStatus,
            String failureCode,
            String manualHoldReason,
            LocalDateTime now
    ) {
        return mapper.markProjectRecoveryFailed(
                ownerUserId,
                projectCode,
                recoveryId,
                expectedAuthVersion,
                expectedRecoveryStatus,
                expectedRecoveryVersion,
                expectedLeaseToken,
                targetStatus,
                failureCode,
                manualHoldReason,
                now
        ) == 1;
    }

    @Override
    public boolean persistRecoveredProjectCookieCas(
            Long ownerUserId,
            String projectCode,
            Long recoveryId,
            Long expectedAuthVersion,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            String cookie,
            Long updatedBy,
            LocalDateTime now
    ) {
        return mapper.persistRecoveredProjectCookieCas(
                ownerUserId,
                projectCode,
                recoveryId,
                expectedAuthVersion,
                expectedRecoveryStatus,
                expectedRecoveryVersion,
                expectedLeaseToken,
                cookie,
                updatedBy,
                now
        ) > 0;
    }

    @Override
    public boolean requeueBlockedTaskAfterRecoveryCas(
            Long taskId,
            Long recoveryId,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            LocalDateTime now
    ) {
        return mapper.requeueBlockedTaskAfterRecoveryCas(
                taskId,
                recoveryId,
                expectedRecoveryStatus,
                expectedRecoveryVersion,
                expectedLeaseToken,
                now
        ) == 1;
    }

    @Override
    public Long coalesceRecoveryItem(NoonAuthRecoveryItemRecord item) {
        mapper.coalesceRecoveryItem(item);
        return item.getId();
    }

    @Override
    public NoonAuthRecoveryItemRecord selectProjectRecoveryItem(
            Long recoveryId,
            Long ownerUserId,
            String projectCode
    ) {
        return mapper.selectProjectRecoveryItem(recoveryId, ownerUserId, projectCode);
    }

    @Override
    public List<NoonAuthRecoveryItemRecord> listPendingItems(Long recoveryId, int limit) {
        return mapper.listPendingItems(recoveryId, limit);
    }

    @Override
    public List<NoonAuthRecoveryItemRecord> listRecoveryItems(Long recoveryId) {
        return mapper.listRecoveryItems(recoveryId);
    }

    @Override
    public boolean hasPendingItems(Long recoveryId) {
        return mapper.countPendingItems(recoveryId) > 0;
    }

    @Override
    public boolean failBlockedTaskAfterRecovery(
            Long taskId,
            Long recoveryId,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            String failureCode,
            String diagnosticSummary,
            LocalDateTime now
    ) {
        return mapper.failBlockedTaskAfterRecovery(
                taskId,
                recoveryId,
                expectedRecoveryStatus,
                expectedRecoveryVersion,
                expectedLeaseToken,
                failureCode,
                diagnosticSummary,
                now
        ) == 1;
    }

    @Override
    public boolean transitionRecoveryItem(
            Long itemId,
            Long recoveryId,
            NoonAuthRecoveryItemStatus expectedStatus,
            NoonAuthRecoveryItemStatus targetStatus,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            String failureCode,
            String diagnosticSummary,
            LocalDateTime recoveredAt,
            LocalDateTime now
    ) {
        return mapper.transitionRecoveryItem(
                itemId,
                recoveryId,
                expectedStatus,
                targetStatus,
                expectedRecoveryStatus,
                expectedRecoveryVersion,
                expectedLeaseToken,
                failureCode,
                diagnosticSummary,
                recoveredAt,
                now
        ) == 1;
    }

    @Override
    public int transitionProjectItems(
            Long recoveryId,
            Long ownerUserId,
            String projectCode,
            Long expectedAuthVersion,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            NoonAuthRecoveryItemStatus targetStatus,
            String failureCode,
            String diagnosticSummary,
            LocalDateTime recoveredAt,
            LocalDateTime now
    ) {
        return mapper.transitionProjectItems(
                recoveryId,
                ownerUserId,
                projectCode,
                expectedAuthVersion,
                expectedRecoveryStatus,
                expectedRecoveryVersion,
                expectedLeaseToken,
                targetStatus,
                failureCode,
                diagnosticSummary,
                recoveredAt,
                now
        );
    }
}
