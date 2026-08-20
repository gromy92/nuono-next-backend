package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryItemStatus;
import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Daily keep-alive check for the single configured Noon account.
 *
 * <p>This deliberately validates existing Project sessions only. It never sends, reads, retries,
 * or stores an OTP and it never starts a business task.</p>
 */
@Component
@Profile("local-db")
public final class NoonAccountSessionDailyVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoonAccountSessionDailyVerifier.class);
    private final NoonAccountSessionMapper mapper;
    private final NoonSessionGateway sessionGateway;
    private final NoonAuthWaitQueue authWaitQueue;
    private final NoonAuthRecoveryProperties properties;
    private volatile NoonAccountSessionAuditResult latestResult;

    NoonAccountSessionDailyVerifier(
            NoonAccountSessionMapper mapper,
            NoonSessionGateway sessionGateway,
            NoonAuthWaitQueue authWaitQueue,
            NoonAuthRecoveryProperties properties
    ) {
        this.mapper = mapper;
        this.sessionGateway = sessionGateway;
        this.authWaitQueue = authWaitQueue;
        this.properties = properties;
        this.latestResult = NoonAccountSessionAuditResult.notRun(properties.projectScopeMode());
    }

    @Scheduled(cron = "${nuono.noon.account-session.daily-check.cron:0 10 4 * * *}")
    void verifyDailySession() {
        if (!properties.isSessionAuditEnabled()) {
            return;
        }
        verifyNow();
    }

    NoonAccountSessionAuditResult verifyNow() {
        List<NoonAccountSessionProjectTarget> targets = mapper.listBoundProjects();
        int totalProjects = targets.size();
        List<NoonAccountSessionProjectTarget> scopedTargets = targets.stream()
                .filter(target -> target != null && properties.allowsProject(target.getProjectCode()))
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return record("NO_PROJECTS", totalProjects, 0, 0);
        }
        if (scopedTargets.isEmpty()) {
            return record("NO_SCOPED_PROJECTS", totalProjects, 0, 0);
        }
        String noonEmail = sessionGateway.configuredMerchantLoginEmail();
        int verifiedProjects = 0;
        for (NoonAccountSessionProjectTarget target : scopedTargets) {
            if (!isUsable(target)) {
                return recoveryResult(target, totalProjects, scopedTargets.size(), verifiedProjects);
            }
            try {
                JsonNode whoami = sessionGateway.whoamiWithCookie(
                        target.getSessionCookie(), target.getProjectCode(), target.getStoreCode()
                );
                if (!NoonProjectSessionValidator.matchesTargetProject(whoami, target.getProjectCode())) {
                    return recoveryResult(target, totalProjects, scopedTargets.size(), verifiedProjects);
                }
                sessionGateway.validateCatalogSessionWithCookie(
                        target.getSessionCookie(), target.getProjectCode(), target.getStoreCode(), noonEmail
                );
                verifiedProjects++;
            } catch (RuntimeException exception) {
                return recoveryResult(target, totalProjects, scopedTargets.size(), verifiedProjects);
            }
        }
        return record("READY", totalProjects, scopedTargets.size(), verifiedProjects);
    }

    public NoonAccountSessionAuditResult latestResult() {
        return latestResult;
    }

    public NoonAccountSessionAuditResult recordRecoveryCompletion(
            List<NoonAuthRecoveryItemRecord> recoveryItems
    ) {
        List<NoonAccountSessionProjectTarget> targets = mapper.listBoundProjects();
        List<NoonAccountSessionProjectTarget> scopedTargets = targets.stream()
                .filter(target -> target != null && properties.allowsProject(target.getProjectCode()))
                .collect(Collectors.toList());
        Set<String> recoveredProjects = recoveryItems == null ? Set.of() : recoveryItems.stream()
                .filter(item -> item != null && item.getStatus() == NoonAuthRecoveryItemStatus.RECOVERED)
                .map(item -> projectKey(item.getOwnerUserId(), item.getProjectCode()))
                .collect(Collectors.toSet());
        int verifiedProjects = (int) scopedTargets.stream()
                .map(target -> projectKey(target.getOwnerUserId(), target.getProjectCode()))
                .filter(recoveredProjects::contains)
                .count();
        String status = !scopedTargets.isEmpty() && verifiedProjects == scopedTargets.size()
                ? "READY"
                : "RECOVERY_INCOMPLETE";
        return record(status, targets.size(), scopedTargets.size(), verifiedProjects);
    }

    private NoonAccountSessionAuditResult recoveryResult(
            NoonAccountSessionProjectTarget target,
            int totalProjects,
            int scopedProjects,
            int verifiedProjects
    ) {
        Optional<Long> recoveryId = enqueue(target);
        return record(
                recoveryId.isPresent() ? "RECOVERY_QUEUED" : "RECOVERY_REJECTED",
                totalProjects,
                scopedProjects,
                verifiedProjects,
                recoveryId.orElse(null)
        );
    }

    private Optional<Long> enqueue(NoonAccountSessionProjectTarget target) {
        if (target != null && target.getOwnerUserId() != null && StringUtils.hasText(target.getStoreCode())) {
            return authWaitQueue.enqueue(NoonAuthWaitRequest.binding(
                    target.getOwnerUserId(), target.getProjectCode(), target.getStoreCode()
            ));
        }
        return Optional.empty();
    }

    private NoonAccountSessionAuditResult record(
            String status,
            int totalProjects,
            int scopedProjects,
            int verifiedProjects
    ) {
        return record(status, totalProjects, scopedProjects, verifiedProjects, null);
    }

    private NoonAccountSessionAuditResult record(
            String status,
            int totalProjects,
            int scopedProjects,
            int verifiedProjects,
            Long recoveryId
    ) {
        NoonAccountSessionAuditResult result = NoonAccountSessionAuditResult.of(
                properties.projectScopeMode(),
                status,
                totalProjects,
                scopedProjects,
                verifiedProjects,
                recoveryId
        );
        latestResult = result;
        LOGGER.info(
                "Noon Project session audit status={} scope={} total={} scoped={} verified={} excluded={} unverified={}",
                result.getStatus(),
                result.getScopeMode(),
                result.getTotalProjects(),
                result.getScopedProjects(),
                result.getVerifiedProjects(),
                result.getExcludedProjects(),
                result.getUnverifiedProjects()
        );
        return result;
    }

    private static boolean isUsable(NoonAccountSessionProjectTarget target) {
        return target != null
                && StringUtils.hasText(target.getProjectCode())
                && StringUtils.hasText(target.getStoreCode())
                && StringUtils.hasText(target.getSessionCookie());
    }

    private static String projectKey(Long ownerUserId, String projectCode) {
        String normalizedProject = StringUtils.hasText(projectCode)
                ? projectCode.trim().toUpperCase(Locale.ROOT)
                : "";
        return ownerUserId + ":" + normalizedProject;
    }
}
