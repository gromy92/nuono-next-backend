package com.nuono.next.noonauth;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand.LeaseLostException;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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
    private final Clock clock;
    private final String workerId;
    private final String configuredIdentityKey;
    private final String configuredFingerprint;

    @Autowired
    public NoonAuthRecoveryWorker(
            NoonAuthRecoveryRepository repository,
            NoonAuthRecoveryProperties properties,
            ObjectProvider<NoonAuthRecoveryGateway> gatewayProvider,
            @Value("${nuono.noon.auth.email-otp.email:}") String configuredEmail,
            @Value("${nuono.noon.auth.email-otp.mail-auth-code:}") String configuredMailboxAuthCode
    ) {
        this(
                repository,
                properties,
                gatewayProvider == null ? null : gatewayProvider.getIfAvailable(),
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
        this(repository, properties, gateway, clock, workerId, null, null);
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
        this.repository = repository;
        this.properties = properties;
        this.gateway = gateway;
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

        if (isInterruptedAttempt(candidate.getStatus())) {
            holdInterruptedAttempt(
                    candidate,
                    fence,
                    pending
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
                    NoonAuthRecoveryFailureCode.OTP_INVALID_OR_EXPIRED,
                    "auth recovery exhausted its two generations"
            );
            return;
        }
        LocalDateTime now = now();
        int sendsInHour = repository.countIdentitySendsSince(candidate.getIdentityKey(), now.minusHours(1));
        int sendsInDay = repository.countIdentitySendsSince(candidate.getIdentityKey(), now.minusDays(1));
        if (sendsInDay >= properties.getMaxSendsPerDay()) {
            cooldown(fence, "DAILY_SEND_QUOTA", "shared identity daily OTP quota reached", now.plusDays(1));
            return;
        }
        if (sendsInHour >= properties.getMaxSendsPerHour()) {
            cooldown(fence, "HOURLY_SEND_QUOTA", "shared identity hourly OTP quota reached", now.plusHours(1));
            return;
        }

        if (fence.status != NoonAuthRecoveryStatus.AUTHENTICATING
                && !transition(fence, NoonAuthRecoveryStatus.AUTHENTICATING, null, null, null, false)) {
            return;
        }
        List<NoonAuthRecoveryProjectTarget> targets = uniqueTargets(pending);
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
            handleIdentityFailure(candidate, fence, pending, attemptResult, sendAttemptCount);
            return;
        }
        applyAuthenticatedProjects(candidate, fence, pending, targets, attemptResult);
    }

    private void applyAuthenticatedProjects(
            NoonAuthIdentityRecoveryRecord candidate,
            ExecutionFence fence,
            List<NoonAuthRecoveryItemRecord> pending,
            List<NoonAuthRecoveryProjectTarget> targets,
            NoonAuthRecoveryAttemptResult attemptResult
    ) {
        if (!transition(fence, NoonAuthRecoveryStatus.APPLYING_PROJECTS, null, null, null, false)) {
            return;
        }
        Map<String, NoonAuthRecoveryProjectResult> resultsByKey = attemptResult.getProjectResults().stream()
                .collect(Collectors.toMap(
                        result -> result.getTarget().key(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<String> recoveredKeys = new LinkedHashSet<>();
        int failedProjects = 0;
        for (NoonAuthRecoveryProjectTarget target : targets) {
            if (!renewFence(fence)) {
                return;
            }
            LocalDateTime now = now();
            NoonAuthRecoveryProjectResult result = resultsByKey.get(target.key());
            boolean providerRecovered = result != null
                    && result.isRecovered()
                    && StringUtils.hasText(result.getCookie());
            boolean persisted = providerRecovered
                    && repository.persistRecoveredProjectCookieCas(
                            target.getOwnerUserId(),
                            target.getProjectCode(),
                            candidate.getId(),
                            target.getExpectedAuthVersion(),
                            fence.status,
                            fence.version,
                            fence.leaseToken,
                            result.getCookie(),
                            target.getOwnerUserId(),
                            now
                    );
            if (persisted) {
                recoveredKeys.add(target.key());
                continue;
            }
            if (!renewFence(fence)) {
                return;
            }
            now = now();
            failedProjects++;
            String failureCode;
            String diagnostic;
            if (providerRecovered) {
                failureCode = "PROJECT_BINDING_CHANGED";
                diagnostic = "project binding changed while auth recovery was in progress";
            } else {
                failureCode = result == null ? "PROJECT_RESULT_MISSING" : result.getCode().name();
                diagnostic = result == null ? "provider returned no project result" : result.getSafeDiagnostic();
            }
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
                return;
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
                return;
            }
        }

        if (!transition(fence, NoonAuthRecoveryStatus.RECOVERING_PULLS, null, null, null, false)) {
            return;
        }
        int recoveredTasks = recoverPullTasks(candidate.getId(), pending, recoveredKeys, fence);
        if (recoveredTasks < 0) {
            return;
        }
        String failureCode = failedProjects == 0 ? null : "PROJECT_PARTIAL_FAILURE";
        complete(
                fence,
                failureCode,
                "projectsRecovered=" + recoveredKeys.size()
                        + "; projectsFailed=" + failedProjects
                        + "; tasksRecovered=" + recoveredTasks
        );
    }

    private int recoverPullTasks(
            Long recoveryId,
            List<NoonAuthRecoveryItemRecord> items,
            Set<String> recoveredKeys,
            ExecutionFence fence
    ) {
        int recoveredTasks = 0;
        for (NoonAuthRecoveryItemRecord item : items) {
            if (!recoveredKeys.contains(projectKey(item))) {
                continue;
            }
            if (!renewFence(fence)) {
                return -1;
            }
            LocalDateTime now = now();
            NoonAuthRecoveryItemStatus targetStatus = NoonAuthRecoveryItemStatus.RECOVERED;
            String failureCode = null;
            String diagnostic = "project cookie verified";
            if (item.getSourceTaskId() != null) {
                boolean resumed = repository.requeueBlockedTaskAfterRecoveryCas(
                        item.getSourceTaskId(),
                        recoveryId,
                        fence.status,
                        fence.version,
                        fence.leaseToken,
                        now
                );
                if (resumed) {
                    recoveredTasks++;
                } else {
                    if (!renewFence(fence)) {
                        return -1;
                    }
                    targetStatus = NoonAuthRecoveryItemStatus.STALE;
                    failureCode = "SOURCE_TASK_STALE";
                    diagnostic = "project recovered but source task is no longer auth-blocked";
                }
            }
            boolean transitioned = repository.transitionRecoveryItem(
                    item.getId(),
                    recoveryId,
                    NoonAuthRecoveryItemStatus.PENDING,
                    targetStatus,
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    failureCode,
                    diagnostic,
                    targetStatus == NoonAuthRecoveryItemStatus.RECOVERED ? now : null,
                    now
            );
            if (!transitioned && !renewFence(fence)) {
                return -1;
            }
        }
        return recoveredTasks;
    }

    private void handleIdentityFailure(
            NoonAuthIdentityRecoveryRecord candidate,
            ExecutionFence fence,
            List<NoonAuthRecoveryItemRecord> pending,
            NoonAuthRecoveryAttemptResult result,
            int sendAttemptCount
    ) {
        NoonAuthRecoveryFailureCode code = result.getFailureCode() == null
                ? NoonAuthRecoveryFailureCode.INTERNAL_FAILURE
                : result.getFailureCode();
        if (code.isManualHold()) {
            holdIdentityAndItems(candidate, fence, pending, code, result.getSafeDiagnostic());
            return;
        }
        if (code == NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED) {
            holdIdentityAndItems(candidate, fence, pending, code, result.getSafeDiagnostic());
            return;
        }
        if (code == NoonAuthRecoveryFailureCode.MAILBOX_UNAVAILABLE) {
            cooldown(
                    fence,
                    code.name(),
                    safeDiagnostic(result.getSafeDiagnostic()),
                    now().plus(properties.minResendDelay())
            );
            return;
        }
        if (code.isResendEligible() && sendAttemptCount < properties.getMaxSendAttemptsPerRecovery()) {
            cooldown(
                    fence,
                    code.name(),
                    safeDiagnostic(result.getSafeDiagnostic()),
                    now().plus(properties.minResendDelay())
            );
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
            List<NoonAuthRecoveryItemRecord> pending
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
        holdIdentityAndItems(
                candidate,
                fence,
                pending,
                NoonAuthRecoveryFailureCode.SEND_RESULT_UNKNOWN,
                "previous auth generation lost its lease or in-memory PKCE state"
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
            NoonAuthRecoveryProjectTarget target = new NoonAuthRecoveryProjectTarget(
                    item.getOwnerUserId(),
                    item.getProjectCode(),
                    item.getStoreCode(),
                    safeLong(item.getExpectedAuthVersion())
            );
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
                NoonAuthRecoveryProjectTarget target = new NoonAuthRecoveryProjectTarget(
                        representative.getOwnerUserId(),
                        representative.getProjectCode(),
                        representative.getStoreCode(),
                        safeLong(representative.getExpectedAuthVersion())
                );
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
            Set<String> recovered = Collections.singleton(projectKey(representative));
            if (recoverPullTasks(candidate.getId(), pending, recovered, fence) < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean renewFence(ExecutionFence fence) {
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

    private void complete(ExecutionFence fence, String failureCode, String diagnostic) {
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
        repository.releaseChangedManualHolds(
                configuredIdentityKey,
                configuredFingerprint,
                now.plus(properties.minResendDelay()),
                now
        );
    }

    private boolean failSnapshotItemsTaskFirst(
            List<NoonAuthRecoveryItemRecord> items,
            NoonAuthRecoveryProjectTarget target,
            Long recoveryId,
            ExecutionFence fence,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    ) {
        Set<Long> taskTerminalItemIds = new LinkedHashSet<>();
        if (!failSourceTasks(
                items,
                target,
                recoveryId,
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

    private boolean failSourceTasks(
            List<NoonAuthRecoveryItemRecord> items,
            NoonAuthRecoveryProjectTarget target,
            Long recoveryId,
            ExecutionFence fence,
            String failureCode,
            String diagnostic,
            LocalDateTime now,
            Set<Long> taskTerminalItemIds
    ) {
        for (NoonAuthRecoveryItemRecord item : items) {
            if (item == null || item.getId() == null || !target.key().equals(projectKey(item))) {
                continue;
            }
            if (item.getSourceTaskId() == null) {
                taskTerminalItemIds.add(item.getId());
                continue;
            }
            if (!renewFence(fence)) {
                return false;
            }
            now = now();
            boolean failed = repository.failBlockedTaskAfterRecovery(
                    item.getSourceTaskId(),
                    recoveryId,
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    failureCode,
                    safeDiagnostic(diagnostic),
                    now
            );
            if (!failed && !renewFence(fence)) {
                return false;
            }
            // Always attempt the per-item terminal CAS after a live recovery-fence check.
            // Its SQL guard refuses to hide an item while its source task is still BLOCKED_AUTH
            // for this recovery, but lets already-finished/stale source tasks drain safely.
            taskTerminalItemIds.add(item.getId());
        }
        return true;
    }

    private void cooldown(
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

    private boolean transition(
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

    private List<NoonAuthRecoveryProjectTarget> uniqueTargets(List<NoonAuthRecoveryItemRecord> items) {
        List<NoonAuthRecoveryProjectTarget> targets = new ArrayList<>();
        for (NoonAuthRecoveryItemRecord item : uniqueProjectItems(items).values()) {
            targets.add(new NoonAuthRecoveryProjectTarget(
                    item.getOwnerUserId(),
                    item.getProjectCode(),
                    item.getStoreCode(),
                    safeLong(item.getExpectedAuthVersion())
            ));
        }
        return targets;
    }

    private Map<String, NoonAuthRecoveryItemRecord> uniqueProjectItems(List<NoonAuthRecoveryItemRecord> items) {
        Map<String, NoonAuthRecoveryItemRecord> projects = new LinkedHashMap<>();
        if (items == null) {
            return projects;
        }
        for (NoonAuthRecoveryItemRecord item : items) {
            if (item != null && item.getOwnerUserId() != null && StringUtils.hasText(item.getProjectCode())) {
                projects.putIfAbsent(projectKey(item), item);
            }
        }
        return projects;
    }

    private Set<String> excludedMessageHashes(NoonAuthIdentityRecoveryRecord candidate) {
        Set<String> hashes = new LinkedHashSet<>();
        if (StringUtils.hasText(candidate.getLastMailUidHash())) {
            hashes.add(candidate.getLastMailUidHash());
        }
        if (StringUtils.hasText(candidate.getLastMessageIdHash())) {
            hashes.add(candidate.getLastMessageIdHash());
        }
        return hashes;
    }

    private boolean isInterruptedAttempt(NoonAuthRecoveryStatus status) {
        return status == NoonAuthRecoveryStatus.AUTHENTICATING
                || status == NoonAuthRecoveryStatus.WAITING_EMAIL
                || status == NoonAuthRecoveryStatus.VALIDATING
                || status == NoonAuthRecoveryStatus.APPLYING_PROJECTS
                || status == NoonAuthRecoveryStatus.RECOVERING_PULLS;
    }

    private String projectKey(NoonAuthRecoveryItemRecord item) {
        return item.getOwnerUserId() + ":" + item.getProjectCode();
    }

    private String safeDiagnostic(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("(?i)(cookie|otp|token|password|secret)\\s*[=:]\\s*[^\\s;]+", "$1=[REDACTED]");
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static final class ExecutionFence {
        private final Long recoveryId;
        private final String leaseToken;
        private NoonAuthRecoveryStatus status;
        private long version;

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
