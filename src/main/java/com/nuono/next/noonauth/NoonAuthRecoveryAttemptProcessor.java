package com.nuono.next.noonauth;

import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.excludedMessageHashes;
import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.isInterruptedAttempt;
import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.projectKey;
import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.uniqueTargets;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand.LeaseLostException;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/** Executes one claimed shared-identity recovery while the worker owns the lease fence. */
final class NoonAuthRecoveryAttemptProcessor {
    private static final int ALL_PENDING_ITEMS = Integer.MAX_VALUE;
    private final NoonAuthRecoveryRepository repository;
    private final NoonAuthRecoveryProperties properties;
    private final NoonAuthRecoveryGateway gateway;
    private final NoonAuthTransientOrchestrator transientOrchestrator;
    private final NoonAuthRecoveryProjectOutcomeHandler projectOutcomeHandler;

    NoonAuthRecoveryAttemptProcessor(
            NoonAuthRecoveryRepository repository,
            NoonAuthRecoveryProperties properties,
            NoonAuthRecoveryGateway gateway,
            NoonAuthTransientOrchestrator transientOrchestrator,
            NoonAuthRecoveryProjectOutcomeHandler projectOutcomeHandler
    ) {
        this.repository = repository;
        this.properties = properties;
        this.gateway = gateway;
        this.transientOrchestrator = transientOrchestrator;
        this.projectOutcomeHandler = projectOutcomeHandler;
    }

    void process(
            NoonAuthIdentityRecoveryRecord candidate,
            NoonAuthRecoveryWorker.ExecutionFence fence,
            NoonAuthRecoveryWorker worker
    ) {
        if (candidate.getStatus() == NoonAuthRecoveryStatus.COALESCING
                && !worker.transition(
                        fence, NoonAuthRecoveryStatus.AUTHENTICATING,
                        null, null, null, false
                )) {
            return;
        }
        List<NoonAuthRecoveryItemRecord> pending = repository.listPendingItems(
                candidate.getId(),
                ALL_PENDING_ITEMS
        );
        if (!worker.reconcileCommittedProjects(candidate, pending, fence)) {
            return;
        }
        pending = repository.listPendingItems(candidate.getId(), ALL_PENDING_ITEMS);
        if (pending.isEmpty()) {
            worker.complete(fence, null, "no pending auth recovery items");
            return;
        }

        List<NoonAuthRecoveryProjectTarget> allTargets = uniqueTargets(pending);
        NoonAuthTransientOrchestrator.Selection targetSelection =
                transientOrchestrator.selectDueTargets(allTargets);
        if (!targetSelection.unmappedTargets.isEmpty()) {
            if (!worker.holdUnmappedProjects(
                    candidate,
                    fence,
                    pending,
                    targetSelection.unmappedTargets
            )) {
                return;
            }
            Set<String> unmappedKeys = targetSelection.unmappedTargets.stream()
                    .map(NoonAuthRecoveryProjectTarget::key)
                    .collect(Collectors.toSet());
            pending = pending.stream()
                    .filter(item -> !unmappedKeys.contains(projectKey(item)))
                    .collect(Collectors.toList());
            allTargets = targetSelection.mappedTargets;
            if (allTargets.isEmpty()) {
                worker.complete(
                        fence,
                        "PROJECT_PARTIAL_FAILURE",
                        "all pending projects require logical-store mapping repair"
                );
                return;
            }
        }
        if (targetSelection.dueTargets.isEmpty()
                && targetSelection.nextBlockedUntil != null) {
            worker.cooldown(
                    fence,
                    "TRANSIENT_BACKOFF_ACTIVE",
                    "project transient backoff is active",
                    targetSelection.nextBlockedUntil
            );
            return;
        }

        boolean resumingCheckpoint = isInterruptedAttempt(candidate.getStatus())
                && gateway != null
                && gateway.canResume(candidate.getId());
        if (isInterruptedAttempt(candidate.getStatus()) && !resumingCheckpoint) {
            worker.holdInterruptedAttempt(
                    candidate,
                    fence,
                    pending,
                    allTargets,
                    targetSelection.logicalStoreIds
            );
            return;
        }
        if (gateway == null) {
            worker.failIdentityAndItems(
                    candidate,
                    fence,
                    pending,
                    NoonAuthRecoveryFailureCode.INTERNAL_FAILURE,
                    "auth recovery gateway is not configured"
            );
            return;
        }

        int sendsInBatch = safeInt(candidate.getSendAttemptCount());
        if (!resumingCheckpoint
                && sendsInBatch >= properties.getMaxSendAttemptsPerRecovery()) {
            worker.holdIdentityAndItems(
                    candidate,
                    fence,
                    pending,
                    transientOrchestrator.hasFailureForRecovery(
                            allTargets,
                            targetSelection.logicalStoreIds,
                            candidate.getId()
                    )
                            ? NoonAuthRecoveryFailureCode.PROJECT_TRANSIENT_RETRY_EXHAUSTED
                            : NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED,
                    "shared identity recovery exhausted its two OTP generations; no third generation is allowed"
            );
            return;
        }
        LocalDateTime now = worker.now();
        LocalDateTime latestSendAt = repository.selectLatestIdentitySendAt(candidate.getIdentityKey());
        LocalDateTime nextSendAt = latestSendAt == null
                ? null
                : latestSendAt.plus(properties.minSendInterval());
        if (!resumingCheckpoint && nextSendAt != null && now.isBefore(nextSendAt)) {
            worker.cooldown(
                    fence,
                    "MIN_SEND_INTERVAL",
                    "shared identity OTP minimum send interval is active",
                    nextSendAt
            );
            return;
        }

        if (!resumingCheckpoint
                && fence.status != NoonAuthRecoveryStatus.AUTHENTICATING
                && !worker.transition(
                        fence, NoonAuthRecoveryStatus.AUTHENTICATING,
                        null, null, null, false
                )) {
            return;
        }
        List<NoonAuthRecoveryProjectTarget> targets = targetSelection.dueTargets;
        now = worker.now();
        for (NoonAuthRecoveryProjectTarget target : targets) {
            if (!repository.markProjectRecovering(
                    target.getOwnerUserId(),
                    target.getProjectCode(),
                    candidate.getId(),
                    target.getExpectedAuthVersion(),
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    now
            )) {
                return;
            }
        }
        int generation = resumingCheckpoint
                ? Math.max(1, safeInt(candidate.getGenerationNo()))
                : safeInt(candidate.getGenerationNo()) + 1;
        AtomicBoolean sendIntentRecorded = new AtomicBoolean(false);

        if (!worker.renewFence(fence)) {
            return;
        }

        NoonAuthRecoveryAttemptResult attemptResult;
        try {
            attemptResult = gateway.attempt(new NoonAuthRecoveryAttemptCommand(
                    candidate.getId(),
                    generation,
                    now.atOffset(ZoneOffset.UTC).toInstant(),
                    excludedMessageHashes(candidate),
                    targets,
                    () -> worker.renewFence(fence),
                    () -> {
                        if (!worker.renewFence(fence)) {
                            return false;
                        }
                        LocalDateTime sendIntentAt = worker.now();
                        if (!repository.recordSendIntent(
                                fence.recoveryId,
                                fence.status,
                                fence.version,
                                fence.leaseToken,
                                sendIntentAt,
                                sendIntentAt
                        )) {
                            return false;
                        }
                        fence.version++;
                        sendIntentRecorded.set(true);
                        return true;
                    }
            ));
        } catch (LeaseLostException exception) {
            return;
        } catch (RuntimeException exception) {
            attemptResult = NoonAuthRecoveryAttemptResult.failed(
                    NoonAuthRecoveryFailureCode.SEND_RESULT_UNKNOWN,
                    null,
                    "auth attempt result unknown"
            );
        }

        if (StringUtils.hasText(attemptResult.getMessageKeyHash())) {
            LocalDateTime correlatedAt = worker.now();
            if (!repository.recordMailboxCorrelation(
                    fence.recoveryId,
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    null,
                    attemptResult.getMessageKeyHash(),
                    correlatedAt
            )) {
                return;
            }
            fence.version++;
        }

        if (!attemptResult.isIdentityAuthenticated()) {
            int sendAttemptCount = sendsInBatch + (sendIntentRecorded.get() ? 1 : 0);
            if (attemptResult.isTransientFailure()) {
                NoonAuthTransientOrchestrator.IdentityFailureOutcome outcome =
                        transientOrchestrator.recordIdentityFailure(
                        targets,
                        targetSelection.logicalStoreIds,
                        targetSelection.nextBlockedUntil,
                        worker.now().plus(properties.minResendDelay()),
                        attemptResult,
                        () -> worker.renewFence(fence) ? worker.backoffFence(fence) : null
                );
                if (outcome.recorded) {
                    worker.cooldown(
                            fence,
                            outcome.failureCode,
                            outcome.diagnostic,
                            outcome.nextBlockedUntil
                    );
                }
                return;
            }
            worker.handleIdentityFailure(candidate, fence, pending, attemptResult, sendAttemptCount);
            return;
        }
        projectOutcomeHandler.apply(
                worker,
                fence,
                candidate,
                pending,
                targets,
                targetSelection.logicalStoreIds,
                targetSelection.nextBlockedUntil,
                worker.now().plus(properties.minResendDelay()),
                attemptResult
        );
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
