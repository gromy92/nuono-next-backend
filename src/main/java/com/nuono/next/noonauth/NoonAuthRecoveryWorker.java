package com.nuono.next.noonauth;

import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.excludedMessageHashes;
import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.isInterruptedAttempt;
import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.projectKey;
import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.safeLong;
import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.uniqueProjectItems;
import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.uniqueTargets;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand.LeaseLostException;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class NoonAuthRecoveryWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoonAuthRecoveryWorker.class);
    private static final int MAX_RECOVERIES_PER_TICK = 4;
    private static final int ALL_PENDING_ITEMS = Integer.MAX_VALUE;
    private final NoonAuthRecoveryRepository repository;
    private final NoonAuthRecoveryProperties properties;
    private final NoonAuthRecoveryGateway gateway;
    private final NoonAuthTransientOrchestrator transientOrchestrator;
    private final NoonAuthWaitingTaskCoordinator waitingTaskCoordinator;
    private final NoonAuthRecoveryProjectOutcomeHandler projectOutcomeHandler;
    private final Clock clock;
    private final String workerId;
    private final String configuredIdentityKey;
    private final String configuredFingerprint;

    @Autowired
    public NoonAuthRecoveryWorker(
            NoonAuthRecoveryRepository repository,
            NoonAuthRecoveryProperties properties,
            ObjectProvider<NoonAuthRecoveryGateway> gatewayProvider,
            NoonAuthTransientBackoffGuard transientBackoffGuard,
            @Value("${nuono.noon.auth.email-otp.email:}") String configuredEmail,
            @Value("${nuono.noon.auth.email-otp.mail-auth-code:}") String configuredMailboxAuthCode
    ) {
        this(
                repository,
                properties,
                gatewayProvider == null ? null : gatewayProvider.getIfAvailable(),
                transientBackoffGuard,
                Clock.systemUTC(),
                "noon-auth-recovery-" + UUID.randomUUID(),
                configuredEmail,
                configuredMailboxAuthCode
        );
    }

    NoonAuthRecoveryWorker(
            NoonAuthRecoveryRepository repository,
            NoonAuthRecoveryProperties properties,
            NoonAuthRecoveryGateway gateway,
            Clock clock,
            String workerId
    ) {
        this(
                repository,
                properties,
                gateway,
                NoonAuthTransientBackoffGuard.disabled(clock),
                clock,
                workerId,
                null,
                null
        );
    }

    NoonAuthRecoveryWorker(
            NoonAuthRecoveryRepository repository,
            NoonAuthRecoveryProperties properties,
            NoonAuthRecoveryGateway gateway,
            NoonAuthTransientBackoffGuard transientBackoffGuard,
            Clock clock,
            String workerId,
            String configuredEmail,
            String configuredMailboxAuthCode
    ) {
        this.repository = repository;
        this.properties = properties;
        this.gateway = gateway;
        NoonAuthTransientBackoffGuard resolvedTransientBackoffGuard = transientBackoffGuard == null
                ? NoonAuthTransientBackoffGuard.disabled(clock)
                : transientBackoffGuard;
        this.transientOrchestrator =
                new NoonAuthTransientOrchestrator(resolvedTransientBackoffGuard);
        this.waitingTaskCoordinator = new NoonAuthWaitingTaskCoordinator(repository);
        this.projectOutcomeHandler =
                new NoonAuthRecoveryProjectOutcomeHandler(
                        repository, transientOrchestrator, waitingTaskCoordinator
                );
        this.clock = clock;
        this.workerId = StringUtils.hasText(workerId) ? workerId : "noon-auth-recovery-worker";
        this.configuredIdentityKey = StringUtils.hasText(configuredEmail)
                ? NoonAuthIdentityKey.fromEmail(configuredEmail)
                : null;
        this.configuredFingerprint = StringUtils.hasText(configuredEmail)
                && StringUtils.hasText(configuredMailboxAuthCode)
                ? NoonAuthIdentityKey.configFingerprint(
                        configuredEmail,
                        configuredMailboxAuthCode,
                        properties.normalizedTrustedSenderDomains()
                )
                : null;
    }

    @Autowired(required = false)
    void setWaitingTaskHandlers(List<NoonAuthWaitingTaskHandler> handlers) {
        waitingTaskCoordinator.setHandlers(handlers);
    }
    NoonAuthRecoveryWorker(
            NoonAuthRecoveryRepository repository,
            NoonAuthRecoveryProperties properties,
            NoonAuthRecoveryGateway gateway,
            Clock clock,
            String workerId,
            String configuredEmail,
            String configuredMailboxAuthCode
    ) {
        this(
                repository,
                properties,
                gateway,
                NoonAuthTransientBackoffGuard.disabled(clock),
                clock,
                workerId,
                configuredEmail,
                configuredMailboxAuthCode
        );
    }

    public int runOnce() {
        validateEnabledConfiguration();
        if (!properties.isEnabled()) {
            repository.drainDisabledRecoveries(now());
            return 0;
        }
        LocalDateTime now = now();
        if (!StringUtils.hasText(configuredIdentityKey)) {
            repository.drainDisabledRecoveries(now);
            return 0;
        }
        for (String staleIdentityKey : repository.listUndrainedIdentityKeysExcept(configuredIdentityKey)) {
            if (StringUtils.hasText(staleIdentityKey)) {
                repository.drainIdentityRecoveries(staleIdentityKey, now);
            }
        }
        if (StringUtils.hasText(configuredFingerprint)) {
            repository.releaseTerminalProjectHoldsOnConfigChange(
                    configuredIdentityKey,
                    configuredFingerprint,
                    now
            );
        }
        reopenChangedManualHolds(now);
        repository.promoteReadySuccessors(now.plus(properties.coalesceDuration()), now);
        List<NoonAuthIdentityRecoveryRecord> candidates = repository.listDueRecoveries(
                now,
                MAX_RECOVERIES_PER_TICK
        );
        int claimed = 0;
        for (NoonAuthIdentityRecoveryRecord candidate : candidates) {
            if (candidate == null || candidate.getId() == null || candidate.getStatus() == null) {
                continue;
            }
            if (!StringUtils.hasText(configuredIdentityKey)
                    || !configuredIdentityKey.equals(candidate.getIdentityKey())) {
                repository.drainIdentityRecoveries(candidate.getIdentityKey(), now);
                continue;
            }
            String leaseToken = UUID.randomUUID().toString().replace("-", "");
            long expectedVersion = safeLong(candidate.getVersionNo());
            if (!repository.tryClaimRecovery(
                    candidate.getId(),
                    candidate.getStatus(),
                    expectedVersion,
                    workerId,
                    leaseToken,
                    now.plus(properties.leaseDuration()),
                    now
            )) {
                continue;
            }
            claimed++;
            ExecutionFence fence = new ExecutionFence(
                    candidate.getId(),
                    candidate.getStatus(),
                    expectedVersion + 1,
                    leaseToken
            );
            processClaimed(candidate, fence);
        }
        return claimed;
    }

    void validateEnabledConfiguration() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(configuredIdentityKey)) {
            throw new IllegalStateException("Noon auth recovery requires a configured shared mailbox email.");
        }
        if (!StringUtils.hasText(configuredFingerprint)) {
            throw new IllegalStateException("Noon auth recovery requires a configured mailbox credential.");
        }
        if (properties.normalizedTrustedSenderDomains().isEmpty()) {
            throw new IllegalStateException("Noon auth recovery requires trusted sender domains.");
        }
        if (gateway == null) {
            throw new IllegalStateException("Noon auth recovery gateway is not configured.");
        }
    }

    private void processClaimed(NoonAuthIdentityRecoveryRecord candidate, ExecutionFence fence) {
        if (candidate.getStatus() == NoonAuthRecoveryStatus.COALESCING
                && !transition(fence, NoonAuthRecoveryStatus.AUTHENTICATING, null, null, null, false)) {
            return;
        }
        List<NoonAuthRecoveryItemRecord> pending = repository.listPendingItems(
                candidate.getId(),
                ALL_PENDING_ITEMS
        );
        if (!reconcileCommittedProjects(candidate, pending, fence)) {
            return;
        }
        pending = repository.listPendingItems(candidate.getId(), ALL_PENDING_ITEMS);
        if (pending.isEmpty()) {
            complete(fence, null, "no pending auth recovery items");
            return;
        }

        List<NoonAuthRecoveryProjectTarget> allTargets = uniqueTargets(pending);
        NoonAuthTransientOrchestrator.Selection targetSelection =
                transientOrchestrator.selectDueTargets(allTargets);
        if (!targetSelection.unmappedTargets.isEmpty()) {
            if (!holdUnmappedProjects(
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
                complete(
                        fence,
                        "PROJECT_PARTIAL_FAILURE",
                        "all pending projects require logical-store mapping repair"
                );
                return;
            }
        }
        if (targetSelection.dueTargets.isEmpty()
                && targetSelection.nextBlockedUntil != null) {
            cooldown(
                    fence,
                    "TRANSIENT_BACKOFF_ACTIVE",
                    "project transient backoff is active",
                    targetSelection.nextBlockedUntil
            );
            return;
        }

        if (isInterruptedAttempt(candidate.getStatus())) {
            holdInterruptedAttempt(
                    candidate,
                    fence,
                    pending,
                    allTargets,
                    targetSelection.logicalStoreIds
            );
            return;
        }
        if (gateway == null) {
            failIdentityAndItems(
                    candidate,
                    fence,
                    pending,
                    NoonAuthRecoveryFailureCode.INTERNAL_FAILURE,
                    "auth recovery gateway is not configured"
            );
            return;
        }

        int sendsInBatch = safeInt(candidate.getSendAttemptCount());
        if (sendsInBatch >= properties.getMaxSendAttemptsPerRecovery()) {
            holdIdentityAndItems(
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
        LocalDateTime now = now();
        LocalDateTime latestSendAt = repository.selectLatestIdentitySendAt(candidate.getIdentityKey());
        LocalDateTime nextSendAt = latestSendAt == null
                ? null
                : latestSendAt.plus(properties.minSendInterval());
        if (nextSendAt != null && now.isBefore(nextSendAt)) {
            cooldown(
                    fence,
                    "MIN_SEND_INTERVAL",
                    "shared identity OTP minimum send interval is active",
                    nextSendAt
            );
            return;
        }

        if (fence.status != NoonAuthRecoveryStatus.AUTHENTICATING
                && !transition(fence, NoonAuthRecoveryStatus.AUTHENTICATING, null, null, null, false)) {
            return;
        }
        List<NoonAuthRecoveryProjectTarget> targets = targetSelection.dueTargets;
        now = now();
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
        int generation = safeInt(candidate.getGenerationNo()) + 1;
        AtomicBoolean sendIntentRecorded = new AtomicBoolean(false);

        if (!renewFence(fence)) {
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
                    () -> renewFence(fence),
                    () -> {
                        if (!renewFence(fence)) {
                            return false;
                        }
                        LocalDateTime sendIntentAt = now();
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
            LocalDateTime correlatedAt = now();
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
                        now().plus(properties.minResendDelay()),
                        attemptResult,
                        () -> renewFence(fence) ? backoffFence(fence) : null
                );
                if (outcome.recorded) {
                    cooldown(
                            fence,
                            outcome.failureCode,
                            outcome.diagnostic,
                            outcome.nextBlockedUntil
                    );
                }
                return;
            }
            handleIdentityFailure(candidate, fence, pending, attemptResult, sendAttemptCount);
            return;
        }
        projectOutcomeHandler.apply(
                this,
                fence,
                candidate,
                pending,
                targets,
                targetSelection.logicalStoreIds,
                targetSelection.nextBlockedUntil,
                now().plus(properties.minResendDelay()),
                attemptResult
        );
    }

    private void handleIdentityFailure(
            NoonAuthIdentityRecoveryRecord candidate,
            ExecutionFence fence,
            List<NoonAuthRecoveryItemRecord> pending,
            NoonAuthRecoveryAttemptResult result,
            int sendAttemptCount
    ) {
        NoonAuthRecoveryFailureCode code = result.getFailureCode() == null
                ? NoonAuthRecoveryFailureCode.INTERNAL_FAILURE : result.getFailureCode();
        if (code.isManualHold()) {
            holdIdentityAndItems(candidate, fence, pending, code, result.getSafeDiagnostic());
            return;
        }
        if (code == NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED) {
            if (sendAttemptCount < properties.getMaxSendAttemptsPerRecovery()) {
                cooldown(fence, code.name(), safeDiagnostic(result.getSafeDiagnostic()),
                        now().plus(properties.rateLimitRetryDelay()));
            } else {
                holdIdentityAndItems(candidate, fence, pending, code, result.getSafeDiagnostic());
            }
            return;
        }
        if (code == NoonAuthRecoveryFailureCode.MAILBOX_UNAVAILABLE) {
            cooldown(fence, code.name(), safeDiagnostic(result.getSafeDiagnostic()),
                    now().plus(properties.minResendDelay()));
            return;
        }
        if (code.isResendEligible() && sendAttemptCount < properties.getMaxSendAttemptsPerRecovery()) {
            cooldown(fence, code.name(), safeDiagnostic(result.getSafeDiagnostic()),
                    now().plus(properties.minResendDelay()));
            return;
        }
        if (sendAttemptCount >= properties.getMaxSendAttemptsPerRecovery()) {
            holdIdentityAndItems(candidate, fence, pending, code, result.getSafeDiagnostic());
            return;
        }
        failIdentityAndItems(candidate, fence, pending, code, result.getSafeDiagnostic());
    }

    private void holdInterruptedAttempt(
            NoonAuthIdentityRecoveryRecord candidate,
            ExecutionFence fence,
            List<NoonAuthRecoveryItemRecord> pending,
            List<NoonAuthRecoveryProjectTarget> targets,
            Map<String, Long> logicalStoreIds
    ) {
        if (safeInt(candidate.getSendAttemptCount()) < properties.getMaxSendAttemptsPerRecovery()) {
            cooldown(
                    fence,
                    NoonAuthRecoveryFailureCode.SEND_RESULT_UNKNOWN.name(),
                    "previous auth generation lost its lease or in-memory PKCE state; waiting before the sole remaining generation",
                    now().plus(properties.leaseDuration())
            );
            return;
        }
        NoonAuthRecoveryFailureCode code = transientOrchestrator.hasFailureForRecovery(
                targets,
                logicalStoreIds,
                candidate.getId()
        )
                ? NoonAuthRecoveryFailureCode.PROJECT_TRANSIENT_RETRY_EXHAUSTED
                : NoonAuthRecoveryFailureCode.SEND_RESULT_UNKNOWN;
        holdIdentityAndItems(
                candidate,
                fence,
                pending,
                code,
                "shared identity recovery exhausted its two OTP generations after an interrupted attempt"
        );
    }

    private void holdIdentityAndItems(
            NoonAuthIdentityRecoveryRecord candidate,
            ExecutionFence fence,
            List<NoonAuthRecoveryItemRecord> pending,
            NoonAuthRecoveryFailureCode code,
            String diagnostic
    ) {
        for (NoonAuthRecoveryItemRecord item : uniqueProjectItems(pending).values()) {
            if (!renewFence(fence)) {
                return;
            }
            LocalDateTime now = now();
            if (!repository.markProjectRecoveryFailed(
                    item.getOwnerUserId(),
                    item.getProjectCode(),
                    candidate.getId(),
                    item.getExpectedAuthVersion(),
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    NoonProjectAuthStatus.MANUAL_HOLD,
                    code.name(),
                    safeDiagnostic(diagnostic),
                    now
            )) {
                return;
            }
            if (!waitingTaskCoordinator.holdSourceTasks(
                    this,
                    pending,
                    NoonAuthRecoveryWorkerValues.target(item),
                    fence,
                    code.name(),
                    safeDiagnostic(diagnostic)
            )) {
                return;
            }
        }
        boolean held = transition(
                fence,
                NoonAuthRecoveryStatus.MANUAL_HOLD,
                null,
                code.name(),
                safeDiagnostic(diagnostic),
                true
        );
        if (held) {
            LOGGER.warn(
                    "Noon auth recovery entered manual hold. recoveryId={} code={}",
                    candidate.getId(),
                    code.name()
            );
        }
    }

    private void failIdentityAndItems(
            NoonAuthIdentityRecoveryRecord candidate,
            ExecutionFence fence,
            List<NoonAuthRecoveryItemRecord> pending,
            NoonAuthRecoveryFailureCode code,
            String diagnostic
    ) {
        Map<String, NoonAuthRecoveryItemRecord> projects = uniqueProjectItems(pending);
        for (NoonAuthRecoveryItemRecord item : projects.values()) {
            if (!renewFence(fence)) {
                return;
            }
            LocalDateTime now = now();
            if (!repository.markProjectRecoveryFailed(
                    item.getOwnerUserId(),
                    item.getProjectCode(),
                    candidate.getId(),
                    item.getExpectedAuthVersion(),
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    NoonProjectAuthStatus.MANUAL_HOLD,
                    code.name(),
                    safeDiagnostic(diagnostic),
                    now
            )) {
                return;
            }
            NoonAuthRecoveryProjectTarget target = NoonAuthRecoveryWorkerValues.target(item);
            if (!failSnapshotItemsTaskFirst(
                    pending,
                    target,
                    candidate.getId(),
                    fence,
                    code.name(),
                    safeDiagnostic(diagnostic),
                    now
            )) {
                return;
            }
        }
        NoonAuthRecoveryStatus target = code.isManualHold()
                ? NoonAuthRecoveryStatus.MANUAL_HOLD
                : NoonAuthRecoveryStatus.FAILED_FINAL;
        if (repository.hasPendingItems(candidate.getId())) {
            cooldown(
                    fence,
                    "PENDING_ITEMS_REMAIN",
                    "auth recovery failure cleanup deferred until all pending items are drained",
                    now()
            );
            return;
        }
        boolean transitioned = transition(
                fence,
                target,
                null,
                code.name(),
                safeDiagnostic(diagnostic),
                true
        );
        if (!transitioned
                && repository.hasPendingItems(candidate.getId())
                && renewFence(fence)) {
            cooldown(
                    fence,
                    "PENDING_ITEMS_REMAIN",
                    "auth recovery failure cleanup deferred until all pending items are drained",
                    now()
            );
        }
    }

    private boolean reconcileCommittedProjects(
            NoonAuthIdentityRecoveryRecord candidate,
            List<NoonAuthRecoveryItemRecord> pending,
            ExecutionFence fence
    ) {
        Map<String, NoonAuthRecoveryItemRecord> projects = uniqueProjectItems(pending);
        for (NoonAuthRecoveryItemRecord representative : projects.values()) {
            NoonProjectAuthStateRecord state = repository.selectProjectAuthState(
                    representative.getOwnerUserId(),
                    representative.getProjectCode()
            );
            if (state != null
                    && state.getStatus() == NoonProjectAuthStatus.MANUAL_HOLD
                    && candidate.getId().equals(state.getActiveRecoveryId())
                    && safeLong(state.getAuthVersion()) == safeLong(representative.getExpectedAuthVersion())) {
                String failureCode = StringUtils.hasText(state.getLastFailureCode())
                        ? state.getLastFailureCode()
                        : "PROJECT_RECOVERY_HELD";
                String diagnostic = StringUtils.hasText(state.getManualHoldReason())
                        ? state.getManualHoldReason()
                        : "project recovery is already held";
                NoonAuthRecoveryProjectTarget target =
                        NoonAuthRecoveryWorkerValues.target(representative);
                if (!failSnapshotItemsTaskFirst(
                        pending,
                        target,
                        candidate.getId(),
                        fence,
                        failureCode,
                        safeDiagnostic(diagnostic),
                        now()
                )) {
                    return false;
                }
                continue;
            }
            if (state == null
                    || state.getStatus() != NoonProjectAuthStatus.HEALTHY
                    || state.getActiveRecoveryId() != null
                    || safeLong(state.getAuthVersion()) <= safeLong(representative.getExpectedAuthVersion())) {
                continue;
            }
            NoonAuthRecoveryProjectTarget target = NoonAuthRecoveryWorkerValues.target(representative);
            try {
                Long logicalStoreId = transientOrchestrator.resolveLogicalStoreId(target);
                if (logicalStoreId != null
                        && !transientOrchestrator.recordSuccess(
                                logicalStoreId,
                                backoffFence(fence)
                        )) {
                    return false;
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to reconcile Noon auth transient backoff. recoveryId={} project={}",
                        candidate.getId(),
                        representative.getProjectCode(),
                        exception
                );
                return false;
            }
            Set<String> recovered = Collections.singleton(projectKey(representative));
            if (projectOutcomeHandler.recoverPullTasks(
                    this,
                    candidate.getId(),
                    pending,
                    recovered,
                    fence
            ) < 0) {
                return false;
            }
        }
        return true;
    }

    boolean renewFence(ExecutionFence fence) {
        LocalDateTime now = now();
        return repository.renewLease(
                fence.recoveryId,
                fence.status,
                fence.version,
                fence.leaseToken,
                now.plus(properties.leaseDuration()),
                now
        );
    }

    void complete(ExecutionFence fence, String failureCode, String diagnostic) {
        LocalDateTime now = now();
        boolean completed = repository.completeRecoveryIfDrained(
                fence.recoveryId,
                fence.status,
                fence.version,
                fence.leaseToken,
                failureCode,
                safeDiagnostic(diagnostic),
                now,
                now.plus(properties.coalesceDuration()),
                now
        );
        if (completed) {
            fence.status = NoonAuthRecoveryStatus.COMPLETED;
            fence.version++;
            return;
        }
        if (repository.hasPendingItems(fence.recoveryId)) {
            cooldown(
                    fence,
                    "PENDING_ITEMS_REMAIN",
                    "auth recovery completion deferred until all pending items are drained",
                    now
            );
        }
    }

    private void reopenChangedManualHolds(LocalDateTime now) {
        if (!StringUtils.hasText(configuredIdentityKey) || !StringUtils.hasText(configuredFingerprint)) {
            return;
        }
        repository.releaseEligibleManualHolds(
                configuredIdentityKey,
                configuredFingerprint,
                now.minus(properties.rateLimitRetryDelay()),
                now.plus(properties.minResendDelay()),
                now
        );
    }

    boolean failSnapshotItemsTaskFirst(
            List<NoonAuthRecoveryItemRecord> items,
            NoonAuthRecoveryProjectTarget target,
            Long recoveryId,
            ExecutionFence fence,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    ) {
        Set<Long> taskTerminalItemIds = new LinkedHashSet<>();
        if (!waitingTaskCoordinator.failSourceTasks(
                this,
                items,
                target,
                fence,
                failureCode,
                diagnostic,
                now,
                taskTerminalItemIds
        )) {
            return false;
        }
        for (NoonAuthRecoveryItemRecord item : items) {
            if (item == null
                    || item.getId() == null
                    || !target.key().equals(projectKey(item))
                    || !taskTerminalItemIds.contains(item.getId())) {
                continue;
            }
            if (!renewFence(fence)) {
                return false;
            }
            now = now();
            boolean transitioned = repository.transitionRecoveryItem(
                    item.getId(),
                    recoveryId,
                    NoonAuthRecoveryItemStatus.PENDING,
                    NoonAuthRecoveryItemStatus.FAILED,
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    failureCode,
                    safeDiagnostic(diagnostic),
                    null,
                    now
            );
            if (!transitioned && !renewFence(fence)) {
                return false;
            }
        }
        return true;
    }

    void cooldown(
            ExecutionFence fence,
            String failureCode,
            String diagnostic,
            LocalDateTime nextAttemptAt
    ) {
        transition(
                fence,
                NoonAuthRecoveryStatus.WAITING_COOLDOWN,
                nextAttemptAt,
                failureCode,
                diagnostic,
                true
        );
    }

    boolean transition(
            ExecutionFence fence,
            NoonAuthRecoveryStatus targetStatus,
            LocalDateTime nextAttemptAt,
            String failureCode,
            String diagnostic,
            boolean releaseLease
    ) {
        LocalDateTime now = now();
        boolean updated = repository.transitionRecovery(
                fence.recoveryId,
                fence.status,
                targetStatus,
                fence.version,
                fence.leaseToken,
                nextAttemptAt,
                failureCode,
                safeDiagnostic(diagnostic),
                targetStatus.isTerminal() ? now : null,
                releaseLease,
                now
        );
        if (updated) {
            fence.status = targetStatus;
            fence.version++;
        }
        return updated;
    }

    private boolean holdUnmappedProjects(
            NoonAuthIdentityRecoveryRecord candidate,
            ExecutionFence fence,
            List<NoonAuthRecoveryItemRecord> pending,
            List<NoonAuthRecoveryProjectTarget> unmappedTargets
    ) {
        String failureCode = "LOGICAL_STORE_MAPPING_MISSING";
        String diagnostic = "project has no canonical logical-store mapping";
        for (NoonAuthRecoveryProjectTarget target : unmappedTargets) {
            if (!renewFence(fence)) {
                return false;
            }
            LocalDateTime now = now();
            if (!repository.markProjectRecoveryFailed(
                    target.getOwnerUserId(),
                    target.getProjectCode(),
                    candidate.getId(),
                    target.getExpectedAuthVersion(),
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    NoonProjectAuthStatus.MANUAL_HOLD,
                    failureCode,
                    diagnostic,
                    now
            )) {
                return false;
            }
            if (!failSnapshotItemsTaskFirst(
                    pending,
                    target,
                    candidate.getId(),
                    fence,
                    failureCode,
                    diagnostic,
                    now
            )) {
                return false;
            }
        }
        return true;
    }

    NoonAuthTransientBackoffWriteFence backoffFence(ExecutionFence fence) {
        return new NoonAuthTransientBackoffWriteFence(
                fence.recoveryId,
                fence.status,
                fence.version,
                fence.leaseToken
        );
    }

    private String safeDiagnostic(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("(?i)(cookie|otp|token|password|secret)\\s*[=:]\\s*[^\\s;]+", "$1=[REDACTED]");
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    static final class ExecutionFence {
        final Long recoveryId;
        final String leaseToken;
        NoonAuthRecoveryStatus status;
        long version;

        private ExecutionFence(
                Long recoveryId,
                NoonAuthRecoveryStatus status,
                long version,
                String leaseToken
        ) {
            this.recoveryId = recoveryId;
            this.status = status;
            this.version = version;
            this.leaseToken = leaseToken;
        }
    }

}
