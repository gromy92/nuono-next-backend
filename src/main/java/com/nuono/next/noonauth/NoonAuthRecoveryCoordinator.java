package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonpull.NoonPullAuthRecoveryQueue;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.noonpull.NoonPullRepository;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class NoonAuthRecoveryCoordinator implements
        NoonPullAuthRecoveryQueue,
        NoonPullProjectAuthGate,
        NoonProjectAuthRecoveryQueue {
    private final NoonAuthRecoveryRepository recoveryRepository;
    private final NoonPullRepository pullRepository;
    private final StoreSyncMapper storeSyncMapper;
    private final NoonAuthRecoveryProjectBatchCatalog projectBatchCatalog;
    private final NoonAuthRecoveryProperties properties;
    private final String configuredEmail;
    private final String configuredMailboxAuthCode;
    private final Clock clock;

    @Autowired
    public NoonAuthRecoveryCoordinator(
            NoonAuthRecoveryRepository recoveryRepository,
            NoonPullRepository pullRepository,
            StoreSyncMapper storeSyncMapper,
            NoonAuthRecoveryProjectBatchCatalog projectBatchCatalog,
            NoonAuthRecoveryProperties properties,
            @Value("${nuono.noon.auth.email-otp.email:}") String configuredEmail,
            @Value("${nuono.noon.auth.email-otp.mail-auth-code:}") String configuredMailboxAuthCode
    ) {
        this(
                recoveryRepository,
                pullRepository,
                storeSyncMapper,
                projectBatchCatalog,
                properties,
                configuredEmail,
                configuredMailboxAuthCode,
                Clock.systemUTC()
        );
    }

    NoonAuthRecoveryCoordinator(
            NoonAuthRecoveryRepository recoveryRepository,
            NoonPullRepository pullRepository,
            StoreSyncMapper storeSyncMapper,
            NoonAuthRecoveryProperties properties,
            String configuredEmail,
            String configuredMailboxAuthCode,
            Clock clock
    ) {
        this(
                recoveryRepository,
                pullRepository,
                storeSyncMapper,
                NoonAuthRecoveryProjectBatchCatalog.empty(),
                properties,
                configuredEmail,
                configuredMailboxAuthCode,
                clock
        );
    }

    NoonAuthRecoveryCoordinator(
            NoonAuthRecoveryRepository recoveryRepository,
            NoonPullRepository pullRepository,
            StoreSyncMapper storeSyncMapper,
            NoonAuthRecoveryProjectBatchCatalog projectBatchCatalog,
            NoonAuthRecoveryProperties properties,
            String configuredEmail,
            String configuredMailboxAuthCode,
            Clock clock
    ) {
        this.recoveryRepository = recoveryRepository;
        this.pullRepository = pullRepository;
        this.storeSyncMapper = storeSyncMapper;
        this.projectBatchCatalog = projectBatchCatalog;
        this.properties = properties;
        this.configuredEmail = normalize(configuredEmail);
        this.configuredMailboxAuthCode = normalize(configuredMailboxAuthCode);
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<Long> blockAndEnqueue(NoonPullTaskRecord task, String rawFailure) {
        if (!canRecover(task, rawFailure)) {
            return Optional.empty();
        }

        StoreSyncStoreRecord project = storeSyncMapper.selectOwnerProject(task.getOwnerUserId(), task.getStoreCode());
        if (project == null || !StringUtils.hasText(project.getProjectCode())) {
            return Optional.empty();
        }
        String projectCode = project.getProjectCode().trim();
        if (!properties.allowsProject(projectCode)) {
            return Optional.empty();
        }

        Optional<Long> primaryRecovery = enqueueTarget(
                task.getOwnerUserId(),
                projectCode,
                task.getStoreCode(),
                task.getSiteCode(),
                task,
                "AUTH_REQUIRED",
                false,
                null
        );
        primaryRecovery.ifPresent(ignored -> enqueueIdentityBatchSiblings(
                task.getOwnerUserId(),
                projectCode
        ));
        return primaryRecovery;
    }

    @Override
    @Transactional
    public Optional<Long> enqueueProject(Long ownerUserId, String projectCode, String storeCode) {
        String normalizedProjectCode = normalize(projectCode);
        if (!canQueueProject(ownerUserId, normalizedProjectCode)) {
            return Optional.empty();
        }
        StoreSyncStoreRecord project = storeSyncMapper.selectOwnerProject(ownerUserId, normalizedProjectCode);
        if (project == null
                || !normalizedProjectCode.equals(normalize(project.getProjectCode()))) {
            return Optional.empty();
        }
        Optional<Long> primaryRecovery = enqueueTarget(
                ownerUserId,
                normalizedProjectCode,
                firstNonBlank(storeCode, project.getStoreCode(), normalizedProjectCode),
                project.getSite(),
                null,
                "BINDING_PENDING",
                true,
                "STORE_BINDING"
        );
        primaryRecovery.ifPresent(ignored -> enqueueIdentityBatchSiblings(
                ownerUserId,
                normalizedProjectCode
        ));
        return primaryRecovery;
    }

    private void enqueueIdentityBatchSiblings(Long sourceOwnerUserId, String sourceProjectCode) {
        for (NoonAuthRecoveryProjectCandidate candidate : projectBatchCatalog.listEligibleProjects(
                properties.normalizedProjectAllowlist()
        )) {
            if (candidate == null
                    || candidate.getOwnerUserId() == null
                    || !StringUtils.hasText(candidate.getProjectCode())
                    || !properties.allowsProject(candidate.getProjectCode())
                    || sameProject(candidate, sourceOwnerUserId, sourceProjectCode)) {
                continue;
            }
            String projectCode = candidate.getProjectCode().trim();
            enqueueTarget(
                    candidate.getOwnerUserId(),
                    projectCode,
                    firstNonBlank(candidate.getStoreCode(), projectCode),
                    null,
                    null,
                    "AUTH_REQUIRED_SHARED_IDENTITY",
                    false,
                    "IDENTITY_BATCH"
            );
        }
    }

    private boolean sameProject(
            NoonAuthRecoveryProjectCandidate candidate,
            Long ownerUserId,
            String projectCode
    ) {
        return ownerUserId.equals(candidate.getOwnerUserId())
                && projectCode.equals(candidate.getProjectCode().trim());
    }

    private Optional<Long> enqueueTarget(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            NoonPullTaskRecord sourceTask,
            String failureCode,
            boolean explicitBinding,
            String sourceDomain
    ) {

        String identityKey = NoonAuthIdentityKey.fromEmail(configuredEmail);
        String configFingerprint = NoonAuthIdentityKey.configFingerprint(
                configuredEmail,
                configuredMailboxAuthCode,
                properties.normalizedTrustedSenderDomains()
        );
        String bindingFingerprint = recoveryRepository.selectProjectBindingFingerprint(
                ownerUserId,
                projectCode
        );
        if (!StringUtils.hasText(bindingFingerprint)) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        NoonProjectAuthStateRecord stateBeforeEnqueue = recoveryRepository.selectProjectAuthState(
                ownerUserId,
                projectCode
        );
        if (!explicitBinding && NoonAuthRecoveryQueuePolicy.keepsManualHold(
                recoveryRepository,
                stateBeforeEnqueue,
                bindingFingerprint,
                configFingerprint
        )) {
            return Optional.empty();
        }
        recoveryRepository.promoteReadySuccessors(now.plus(properties.coalesceDuration()), now);
        reopenManualHoldAfterCredentialChange(identityKey, configFingerprint, now);

        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setIdentityKey(identityKey);
        recovery.setConfigFingerprint(configFingerprint);
        recovery.setRequestedAt(now);
        recovery.setCoalesceUntil(now.plus(properties.coalesceDuration()));
        recovery.setNextAttemptAt(recovery.getCoalesceUntil());
        Long activeRecoveryId = recoveryRepository.coalesceActiveRecovery(recovery);
        if (activeRecoveryId == null) {
            throw new IllegalStateException("Noon auth recovery queue did not allocate a recovery id.");
        }
        NoonAuthIdentityRecoveryRecord activeRecovery = recoveryRepository.selectActiveRecoveryForUpdate(identityKey);
        if (activeRecovery == null || !activeRecoveryId.equals(activeRecovery.getId())) {
            throw new IllegalStateException("Noon auth recovery queue lost its active identity fence.");
        }
        NoonAuthIdentityRecoveryRecord waitingSuccessor = recoveryRepository.selectWaitingSuccessorForUpdate(
                identityKey
        );
        NoonProjectAuthStateRecord existingProjectState = recoveryRepository.selectProjectAuthStateForUpdate(
                ownerUserId,
                projectCode
        );
        if (!explicitBinding && NoonAuthRecoveryQueuePolicy.keepsManualHoldWithLockedRecoveries(
                existingProjectState,
                bindingFingerprint,
                configFingerprint,
                activeRecovery,
                waitingSuccessor
        )) {
            recoveryRepository.cancelEmptyRecoveryAfterRejectedEnqueue(activeRecoveryId, now);
            return Optional.empty();
        }
        boolean staleSourceTaskAuthFailure = NoonAuthRecoveryQueuePolicy.sourceTaskPredatesCurrentAuth(
                sourceTask,
                existingProjectState
        );
        NoonAuthRecoveryItemRecord committedProjectItem = explicitBinding
                ? null
                : NoonAuthRecoveryQueuePolicy.resolveCommittedProjectJoin(
                        recoveryRepository,
                        existingProjectState,
                        activeRecovery,
                        ownerUserId,
                        projectCode
                );
        NoonAuthRecoveryItemRecord activeProjectItem = explicitBinding
                ? recoveryRepository.selectProjectRecoveryItem(
                        activeRecoveryId,
                        ownerUserId,
                        projectCode
                )
                : null;
        NoonAuthRecoveryItemRecord successorProjectItem = explicitBinding && waitingSuccessor != null
                ? recoveryRepository.selectProjectRecoveryItem(
                        waitingSuccessor.getId(),
                        ownerUserId,
                        projectCode
                )
                : null;
        Long projectBoundRecoveryId = NoonAuthRecoveryQueuePolicy.resolveProjectBoundRecovery(
                existingProjectState,
                activeRecovery,
                waitingSuccessor,
                identityKey
        );
        Long recoveryId;
        if (committedProjectItem != null) {
            recoveryId = activeRecoveryId;
        } else if (projectBoundRecoveryId != null) {
            recoveryId = projectBoundRecoveryId;
        } else if (activeProjectItem != null) {
            recoveryId = activeRecoveryId;
        } else if (successorProjectItem != null) {
            recoveryId = waitingSuccessor.getId();
        } else if (activeRecovery.getStatus() == NoonAuthRecoveryStatus.COALESCING) {
            recoveryId = activeRecoveryId;
        } else if (waitingSuccessor != null) {
            recoveryId = waitingSuccessor.getId();
        } else {
            recovery.setId(null);
            recovery.setPredecessorRecoveryId(activeRecoveryId);
            recoveryId = recoveryRepository.coalesceSuccessorRecovery(recovery);
            NoonAuthIdentityRecoveryRecord successor = recoveryId == null
                    ? null
                    : recoveryRepository.selectRecovery(recoveryId);
            if (successor == null
                    || successor.getStatus() != NoonAuthRecoveryStatus.WAITING_PREDECESSOR
                    || !identityKey.equals(successor.getIdentityKey())
                    || !activeRecoveryId.equals(successor.getPredecessorRecoveryId())) {
                throw new IllegalStateException("Noon auth recovery queue could not fence a successor batch.");
            }
        }

        Long expectedAuthVersion;
        if (explicitBinding) {
            expectedAuthVersion = recoveryRepository.rebaseProjectBindingEpoch(
                    recoveryId,
                    ownerUserId,
                    projectCode,
                    identityKey,
                    bindingFingerprint,
                    configFingerprint,
                    now.plus(properties.coalesceDuration()),
                    now.plus(properties.minResendDelay()),
                    now
            );
            if (expectedAuthVersion == null) {
                throw new IllegalStateException("Noon auth recovery binding epoch did not return a project fence.");
            }
        } else if (committedProjectItem != null) {
            expectedAuthVersion = committedProjectItem.getExpectedAuthVersion();
        } else if (staleSourceTaskAuthFailure) {
            expectedAuthVersion = existingProjectState.getAuthVersion() - 1L;
        } else {
            recoveryRepository.upsertProjectAuthRequired(
                    ownerUserId,
                    projectCode,
                    identityKey,
                    recoveryId,
                    bindingFingerprint,
                    configFingerprint,
                    failureCode,
                    sourceTask == null ? null : sourceTask.getId(),
                    now
            );
            NoonProjectAuthStateRecord projectState = recoveryRepository.selectProjectAuthStateForUpdate(
                    ownerUserId,
                    projectCode
            );
            if (projectState == null
                    || !recoveryId.equals(projectState.getActiveRecoveryId())
                    || projectState.getAuthVersion() == null) {
                throw new IllegalStateException("Noon auth recovery project state was not fenced to the active recovery.");
            }
            expectedAuthVersion = projectState.getAuthVersion();
        }

        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setRecoveryId(recoveryId);
        item.setOwnerUserId(ownerUserId);
        item.setProjectCode(projectCode);
        item.setStoreCode(storeCode);
        item.setSiteCode(siteCode);
        item.setSourceTaskId(sourceTask == null ? null : sourceTask.getId());
        item.setSourceDomain(sourceTask == null
                ? sourceDomain
                : sourceTask.getDataDomain() == null ? null : sourceTask.getDataDomain().name());
        item.setExpectedAuthVersion(expectedAuthVersion);
        item.setStatus(NoonAuthRecoveryItemStatus.PENDING);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        recoveryRepository.coalesceRecoveryItem(item);

        if (sourceTask == null) {
            return Optional.of(recoveryId);
        }
        int blocked = pullRepository.blockTaskForAuth(
                sourceTask.getId(),
                recoveryId,
                safeTaskDiagnostic(sourceTask),
                now
        );
        if (blocked != 1) {
            NoonPullTaskRecord current = pullRepository.selectTask(sourceTask.getId());
            if (current == null
                    || current.getStatus() != NoonPullTaskStatus.BLOCKED_AUTH
                    || !recoveryId.equals(current.getAuthRecoveryId())) {
                throw new IllegalStateException("Noon pull task could not be fenced to the auth recovery batch.");
            }
        }
        return Optional.of(recoveryId);
    }

    @Override
    public boolean isBlocked(Long ownerUserId, String projectCode) {
        if (!properties.isEnabled() || ownerUserId == null || !StringUtils.hasText(projectCode)) {
            return false;
        }
        NoonProjectAuthStateRecord state = recoveryRepository.selectProjectAuthState(
                ownerUserId,
                projectCode.trim()
        );
        return state != null && state.getStatus() != null && state.getStatus().blocksProviderCalls();
    }

    private boolean canRecover(NoonPullTaskRecord task, String rawFailure) {
        return properties.isEnabled()
                && task != null
                && task.getId() != null
                && task.getOwnerUserId() != null
                && StringUtils.hasText(task.getStoreCode())
                && StringUtils.hasText(configuredEmail)
                && StringUtils.hasText(configuredMailboxAuthCode)
                && !properties.normalizedTrustedSenderDomains().isEmpty()
                && NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry(rawFailure);
    }

    private boolean canQueueProject(Long ownerUserId, String projectCode) {
        return properties.isEnabled()
                && ownerUserId != null
                && StringUtils.hasText(projectCode)
                && StringUtils.hasText(configuredEmail)
                && StringUtils.hasText(configuredMailboxAuthCode)
                && !properties.normalizedTrustedSenderDomains().isEmpty()
                && properties.allowsProject(projectCode);
    }

    private void reopenManualHoldAfterCredentialChange(
            String identityKey,
            String configFingerprint,
            LocalDateTime now
    ) {
        NoonAuthIdentityRecoveryRecord active = recoveryRepository.selectActiveRecovery(identityKey);
        if (active == null
                || active.getStatus() != NoonAuthRecoveryStatus.MANUAL_HOLD
                || !StringUtils.hasText(active.getConfigFingerprint())
                || active.getConfigFingerprint().equals(configFingerprint)) {
            return;
        }
        recoveryRepository.releaseManualHoldOnConfigChange(
                identityKey,
                active.getConfigFingerprint(),
                configFingerprint,
                now.plus(properties.minResendDelay()),
                now
        );
    }

    private String safeTaskDiagnostic(NoonPullTaskRecord task) {
        String domain = task.getDataDomain() == null
                ? "UNKNOWN"
                : task.getDataDomain().name().toUpperCase(Locale.ROOT);
        return "auth expiry queued; task=" + task.getId() + "; domain=" + domain;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }
}
