package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
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
        NoonPullProjectAuthGate,
        NoonAuthWaitQueue {
    private final NoonAuthRecoveryRepository recoveryRepository;
    private final StoreSyncMapper storeSyncMapper;
    private final NoonAuthRecoveryProperties properties;
    private final String configuredEmail;
    private final String configuredMailboxAuthCode;
    private final Clock clock;

    @Autowired
    public NoonAuthRecoveryCoordinator(
            NoonAuthRecoveryRepository recoveryRepository,
            StoreSyncMapper storeSyncMapper,
            NoonAuthRecoveryProperties properties,
            @Value("${nuono.noon.auth.email-otp.email:}") String configuredEmail,
            @Value("${nuono.noon.auth.email-otp.mail-auth-code:}") String configuredMailboxAuthCode
    ) {
        this(
                recoveryRepository,
                storeSyncMapper,
                properties,
                configuredEmail,
                configuredMailboxAuthCode,
                Clock.systemUTC()
        );
    }

    NoonAuthRecoveryCoordinator(
            NoonAuthRecoveryRepository recoveryRepository,
            StoreSyncMapper storeSyncMapper,
            NoonAuthRecoveryProperties properties,
            String configuredEmail,
            String configuredMailboxAuthCode,
            Clock clock
    ) {
        this.recoveryRepository = recoveryRepository;
        this.storeSyncMapper = storeSyncMapper;
        this.properties = properties;
        this.configuredEmail = normalize(configuredEmail);
        this.configuredMailboxAuthCode = normalize(configuredMailboxAuthCode);
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = NoonAuthRetrySuppressedException.class)
    public Optional<Long> enqueue(NoonAuthWaitRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        Long ownerUserId = request.getOwnerUserId();
        String normalizedProjectCode = normalize(request.getProjectCode());
        NoonAuthRecoveryProjectCandidate target = NoonAuthRecoveryStoreTargetResolver.resolve(
                storeSyncMapper, ownerUserId, normalizedProjectCode, request.getStoreCode());
        if (target == null || !canQueueProject(ownerUserId, target.getProjectCode())) {
            return Optional.empty();
        }
        return enqueueTarget(
                ownerUserId,
                target.getProjectCode(),
                target.getStoreCode(),
                StringUtils.hasText(request.getSiteCode())
                        ? request.getSiteCode()
                        : target.getSiteCode(),
                request.getSourceTaskId(),
                request.getSourceDomain(),
                request.getCheckpoint(),
                request.getResumePolicy(),
                request.getSourceStartedAt(),
                request.hasSourceTask() ? "AUTH_REQUIRED" : "BINDING_PENDING",
                !request.hasSourceTask()
        );
    }

    private Optional<Long> enqueueTarget(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            Long sourceTaskId,
            String sourceDomain,
            String sourceCheckpoint,
            NoonAuthResumePolicy resumePolicy,
            LocalDateTime sourceStartedAt,
            String failureCode,
            boolean explicitBinding
    ) {

        projectCode = normalize(projectCode);
        storeCode = normalize(storeCode);
        siteCode = NoonAuthRecoveryTargetPolicy.normalizeSite(siteCode);
        if (!NoonAuthRecoveryTargetPolicy.hasCompleteBusinessIdentity(
                ownerUserId,
                projectCode,
                storeCode,
                siteCode
        )) {
            return Optional.empty();
        }

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
        if (sourceTaskId != null
                && stateBeforeEnqueue != null
                && stateBeforeEnqueue.getStatus() == NoonProjectAuthStatus.HEALTHY
                && stateBeforeEnqueue.getAuthVersion() != null
                && recoveryRepository.hasRecoveredSourceTaskAtCurrentAuthVersion(
                        ownerUserId,
                        projectCode,
                        sourceDomain,
                        sourceTaskId,
                        stateBeforeEnqueue.getAuthVersion()
                )) {
            throw new NoonAuthRetrySuppressedException(
                    "同一任务已在当前 Noon Project 授权版本完成过恢复，但业务接口仍拒绝访问；"
                            + "再次发送 OTP 无法修复该通道，系统已停止重复认证。"
            );
        }
        reopenManualHoldAfterCredentialChange(identityKey, configFingerprint, now);
        boolean startsFreshRenewal = NoonAuthRecoveryQueuePolicy.startsFreshIdentityRenewal(
                stateBeforeEnqueue,
                sourceTaskId,
                explicitBinding,
                configFingerprint
        );
        if (!explicitBinding
                && stateBeforeEnqueue != null
                && stateBeforeEnqueue.getStatus() == NoonProjectAuthStatus.MANUAL_HOLD
                && !startsFreshRenewal
                && Objects.equals(stateBeforeEnqueue.getConfigFingerprint(), configFingerprint)) {
            return Optional.empty();
        }
        Long reopenedRenewalId = startsFreshRenewal
                ? recoveryRepository.reopenLegacyManualHoldForRenewal(identityKey, now)
                : null;

        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setIdentityKey(identityKey);
        recovery.setConfigFingerprint(configFingerprint);
        recovery.setRequestedAt(now);
        recovery.setCoalesceUntil(now.plus(properties.coalesceDuration()));
        recovery.setNextAttemptAt(recovery.getCoalesceUntil());
        Long activeRecoveryId = reopenedRenewalId == null
                ? recoveryRepository.coalesceActiveRecovery(recovery)
                : reopenedRenewalId;
        if (activeRecoveryId == null) {
            throw new IllegalStateException("Noon auth recovery queue did not allocate a recovery id.");
        }
        NoonAuthIdentityRecoveryRecord activeRecovery = recoveryRepository.selectActiveRecoveryForUpdate(identityKey);
        if (activeRecovery == null || !activeRecoveryId.equals(activeRecovery.getId())) {
            throw new IllegalStateException("Noon auth recovery queue lost its active identity fence.");
        }
        NoonProjectAuthStateRecord existingProjectState = recoveryRepository.selectProjectAuthStateForUpdate(
                ownerUserId,
                projectCode
        );
        boolean staleSourceTaskAuthFailure = NoonAuthRecoveryQueuePolicy.sourceTaskPredatesCurrentAuth(
                sourceStartedAt,
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
        Long projectBoundRecoveryId = NoonAuthRecoveryQueuePolicy.resolveProjectBoundRecovery(
                existingProjectState,
                activeRecovery,
                identityKey
        );
        Long recoveryId;
        if (committedProjectItem != null) {
            recoveryId = activeRecoveryId;
        } else if (projectBoundRecoveryId != null) {
            recoveryId = projectBoundRecoveryId;
        } else if (activeProjectItem != null) {
            recoveryId = activeRecoveryId;
        } else {
            recoveryId = activeRecoveryId;
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
                    sourceTaskId,
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
        item.setSourceTaskId(sourceTaskId);
        item.setSourceDomain(sourceDomain);
        item.setSourceCheckpoint(sourceCheckpoint);
        item.setResumePolicy(resumePolicy);
        item.setExpectedAuthVersion(expectedAuthVersion);
        item.setStatus(NoonAuthRecoveryItemStatus.PENDING);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        recoveryRepository.coalesceRecoveryItem(item);
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

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
