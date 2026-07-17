package com.nuono.next.noonauth;

import java.time.LocalDateTime;
import java.util.List;

public interface NoonAuthRecoveryRepository {

    Long coalesceActiveRecovery(NoonAuthIdentityRecoveryRecord recovery);

    Long coalesceSuccessorRecovery(NoonAuthIdentityRecoveryRecord recovery);

    NoonAuthIdentityRecoveryRecord selectRecovery(Long recoveryId);

    NoonAuthIdentityRecoveryRecord selectRecoveryForUpdate(Long recoveryId);

    NoonAuthIdentityRecoveryRecord selectActiveRecovery(String identityKey);

    NoonAuthIdentityRecoveryRecord selectActiveRecoveryForUpdate(String identityKey);

    NoonAuthIdentityRecoveryRecord selectWaitingSuccessorForUpdate(String identityKey);

    List<NoonAuthIdentityRecoveryRecord> listDueRecoveries(LocalDateTime now, int limit);

    List<String> listUndrainedIdentityKeysExcept(String identityKey);

    int promoteReadySuccessors(LocalDateTime coalesceUntil, LocalDateTime now);

    int drainDisabledRecoveries(LocalDateTime now);

    int drainIdentityRecoveries(String identityKey, LocalDateTime now);

    boolean tryClaimRecovery(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String leaseOwner,
            String leaseToken,
            LocalDateTime leaseUntil,
            LocalDateTime now
    );

    boolean renewLease(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            LocalDateTime leaseUntil,
            LocalDateTime now
    );

    boolean transitionRecovery(
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
    );

    boolean completeRecoveryIfDrained(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            String failureCode,
            String diagnosticSummary,
            LocalDateTime completedAt,
            LocalDateTime successorCoalesceUntil,
            LocalDateTime now
    );

    boolean releaseManualHoldOnConfigChange(
            String identityKey,
            String expectedConfigFingerprint,
            String newConfigFingerprint,
            LocalDateTime nextAttemptAt,
            LocalDateTime now
    );

    int releaseChangedManualHolds(
            String identityKey,
            String newConfigFingerprint,
            LocalDateTime nextAttemptAt,
            LocalDateTime now
    );

    int releaseTerminalProjectHoldsOnConfigChange(
            String identityKey,
            String newConfigFingerprint,
            LocalDateTime now
    );

    boolean recordSendIntent(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            LocalDateTime sendIntentAt,
            LocalDateTime now
    );

    boolean recordMailboxCorrelation(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            Long expectedVersion,
            String expectedLeaseToken,
            String mailUidHash,
            String messageIdHash,
            LocalDateTime now
    );

    int countIdentitySendsSince(String identityKey, LocalDateTime since);

    Long rebaseProjectBindingEpoch(
            Long recoveryId,
            Long ownerUserId,
            String projectCode,
            String identityKey,
            String bindingFingerprint,
            String configFingerprint,
            LocalDateTime coalesceUntil,
            LocalDateTime cooldownAt,
            LocalDateTime now
    );

    boolean cancelEmptyRecoveryAfterRejectedEnqueue(Long recoveryId, LocalDateTime now);

    void upsertProjectAuthRequired(
            Long ownerUserId,
            String projectCode,
            String identityKey,
            Long recoveryId,
            String bindingFingerprint,
            String configFingerprint,
            String failureCode,
            Long sourceTaskId,
            LocalDateTime now
    );

    String selectProjectBindingFingerprint(Long ownerUserId, String projectCode);

    NoonProjectAuthStateRecord selectProjectAuthState(Long ownerUserId, String projectCode);

    NoonProjectAuthStateRecord selectProjectAuthStateForUpdate(Long ownerUserId, String projectCode);

    boolean markProjectRecovering(
            Long ownerUserId,
            String projectCode,
            Long recoveryId,
            Long expectedAuthVersion,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            LocalDateTime now
    );

    boolean markProjectRecoveryFailed(
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
    );

    boolean persistRecoveredProjectCookieCas(
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
    );

    boolean requeueBlockedTaskAfterRecoveryCas(
            Long taskId,
            Long recoveryId,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            LocalDateTime now
    );

    Long coalesceRecoveryItem(NoonAuthRecoveryItemRecord item);

    NoonAuthRecoveryItemRecord selectProjectRecoveryItem(
            Long recoveryId,
            Long ownerUserId,
            String projectCode
    );

    List<NoonAuthRecoveryItemRecord> listPendingItems(Long recoveryId, int limit);

    List<NoonAuthRecoveryItemRecord> listRecoveryItems(Long recoveryId);

    boolean hasPendingItems(Long recoveryId);

    boolean failBlockedTaskAfterRecovery(
            Long taskId,
            Long recoveryId,
            NoonAuthRecoveryStatus expectedRecoveryStatus,
            Long expectedRecoveryVersion,
            String expectedLeaseToken,
            String failureCode,
            String diagnosticSummary,
            LocalDateTime now
    );

    boolean transitionRecoveryItem(
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
    );

    int transitionProjectItems(
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
    );
}
