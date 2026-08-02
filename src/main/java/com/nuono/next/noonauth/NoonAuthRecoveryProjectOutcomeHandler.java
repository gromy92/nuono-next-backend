package com.nuono.next.noonauth;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

final class NoonAuthRecoveryProjectOutcomeHandler {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(NoonAuthRecoveryProjectOutcomeHandler.class);

    private final NoonAuthRecoveryRepository repository;
    private final NoonAuthTransientOrchestrator transientOrchestrator;
    private NoonAuthWaitingTaskRouter waitingTaskRouter;

    NoonAuthRecoveryProjectOutcomeHandler(
            NoonAuthRecoveryRepository repository,
            NoonAuthTransientOrchestrator transientOrchestrator
    ) {
        this.repository = repository;
        this.transientOrchestrator = transientOrchestrator;
        this.waitingTaskRouter = new NoonAuthWaitingTaskRouter(repository, java.util.Collections.emptyList());
    }

    void setWaitingTaskHandlers(List<NoonAuthWaitingTaskHandler> handlers) {
        this.waitingTaskRouter = new NoonAuthWaitingTaskRouter(repository, handlers);
    }

    void apply(
            NoonAuthRecoveryWorker worker,
            NoonAuthRecoveryWorker.ExecutionFence fence,
            NoonAuthIdentityRecoveryRecord candidate,
            List<NoonAuthRecoveryItemRecord> pending,
            List<NoonAuthRecoveryProjectTarget> targets,
            Map<String, Long> logicalStoreIds,
            LocalDateTime existingNextBlockedUntil,
            LocalDateTime fallbackBlockedUntil,
            NoonAuthRecoveryAttemptResult attemptResult
    ) {
        if (!worker.transition(
                fence,
                NoonAuthRecoveryStatus.APPLYING_PROJECTS,
                null,
                null,
                null,
                false
        )) {
            return;
        }
        Map<String, NoonAuthRecoveryProjectResult> resultsByKey =
                attemptResult.getProjectResults().stream().collect(Collectors.toMap(
                        result -> result.getTarget().key(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<String> recoveredKeys = new LinkedHashSet<>();
        int failedProjects = 0;
        int transientProjects = 0;
        LocalDateTime nextBlockedUntil = existingNextBlockedUntil;

        for (NoonAuthRecoveryProjectTarget target : targets) {
            NoonAuthRecoveryProjectResult result = resultsByKey.get(target.key());
            boolean providerRecovered = result != null
                    && result.isRecovered()
                    && StringUtils.hasText(result.getCookie());
            if (!providerRecovered) {
                continue;
            }
            if (!worker.renewFence(fence)) {
                return;
            }
            LocalDateTime now = worker.now();
            boolean persisted = repository.persistRecoveredProjectCookieCas(
                    target.getOwnerUserId(),
                    target.getProjectCode(),
                    candidate.getId(),
                    target.getExpectedAuthVersion(),
                    fence.status,
                    fence.version,
                    fence.leaseToken,
                    result.getCookie(),
                    result.getUserCode(),
                    target.getOwnerUserId(),
                    now
            );
            if (!persisted) {
                continue;
            }
            try {
                if (!transientOrchestrator.recordSuccess(
                        logicalStoreIds.get(target.key()),
                        worker.backoffFence(fence)
                )) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to reset Noon auth transient backoff after cookie commit. "
                                + "recoveryId={} project={}",
                        candidate.getId(),
                        target.getProjectCode(),
                        exception
                );
                return;
            }
            recoveredKeys.add(target.key());
        }

        for (NoonAuthRecoveryProjectTarget target : targets) {
            if (recoveredKeys.contains(target.key()) || !worker.renewFence(fence)) {
                if (!recoveredKeys.contains(target.key())) {
                    return;
                }
                continue;
            }
            LocalDateTime now = worker.now();
            NoonAuthRecoveryProjectResult result = resultsByKey.get(target.key());
            boolean providerRecovered = result != null
                    && result.isRecovered()
                    && StringUtils.hasText(result.getCookie());
            if (result != null && result.isTransientFailure()) {
                NoonAuthTransientBackoffState hold = transientOrchestrator.recordFailure(
                        target,
                        logicalStoreIds.get(target.key()),
                        worker.backoffFence(fence),
                        result.getFailureStage(),
                        result.getTransientErrorType(),
                        result.getSafeDiagnostic()
                );
                if (hold == null) {
                    return;
                }
                transientProjects++;
                nextBlockedUntil = NoonAuthTransientOrchestrator.earlier(
                        nextBlockedUntil,
                        hold.getBlockedUntil()
                );
                continue;
            }
            failedProjects++;
            String failureCode = providerRecovered
                    ? "PROJECT_BINDING_CHANGED"
                    : result == null ? "PROJECT_RESULT_MISSING" : result.getCode().name();
            String diagnostic = providerRecovered
                    ? "project binding changed while auth recovery was in progress"
                    : result == null
                            ? "provider returned no project result"
                            : result.getSafeDiagnostic();
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
            ) || !worker.failSnapshotItemsTaskFirst(
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

        if (!worker.transition(
                fence,
                NoonAuthRecoveryStatus.RECOVERING_PULLS,
                null,
                null,
                null,
                false
        )) {
            return;
        }
        int recoveredTasks = recoverPullTasks(
                worker,
                candidate.getId(),
                pending,
                recoveredKeys,
                fence
        );
        if (recoveredTasks < 0) {
            return;
        }
        if (transientProjects > 0 || nextBlockedUntil != null) {
            String failureCode = transientProjects == 1
                    ? transientOrchestrator.onlyTransientFailureCode(resultsByKey, targets)
                    : "PROJECT_TRANSIENT_BACKOFF";
            worker.cooldown(
                    fence,
                    failureCode,
                    "projectsRecovered=" + recoveredKeys.size()
                            + "; projectsTransient=" + transientProjects
                            + "; projectsFailed=" + failedProjects
                            + "; tasksRecovered=" + recoveredTasks,
                    nextBlockedUntil == null ? fallbackBlockedUntil : nextBlockedUntil
            );
            return;
        }
        worker.complete(
                fence,
                failedProjects == 0 ? null : "PROJECT_PARTIAL_FAILURE",
                "projectsRecovered=" + recoveredKeys.size()
                        + "; projectsFailed=" + failedProjects
                        + "; tasksRecovered=" + recoveredTasks
        );
    }

    int recoverPullTasks(
            NoonAuthRecoveryWorker worker,
            Long recoveryId,
            List<NoonAuthRecoveryItemRecord> items,
            Set<String> recoveredKeys,
            NoonAuthRecoveryWorker.ExecutionFence fence
    ) {
        int recoveredTasks = 0;
        for (NoonAuthRecoveryItemRecord item : items) {
            if (!recoveredKeys.contains(projectKey(item))) {
                continue;
            }
            if (!worker.renewFence(fence)) {
                return -1;
            }
            LocalDateTime now = worker.now();
            NoonAuthRecoveryItemStatus targetStatus = NoonAuthRecoveryItemStatus.RECOVERED;
            String failureCode = null;
            String diagnostic = "project cookie verified";
            if (item.getSourceTaskId() != null) {
                NoonAuthWaitingTaskOutcome outcome = waitingTaskRouter.resume(
                        item, fence.status, fence.version, fence.leaseToken, now
                );
                if (outcome == NoonAuthWaitingTaskOutcome.RESUMED) {
                    recoveredTasks++;
                } else if (outcome == NoonAuthWaitingTaskOutcome.MANUAL_REVIEW) {
                    diagnostic = "project cookie verified; source task requires readback";
                } else if (!worker.renewFence(fence)) {
                    return -1;
                } else {
                    targetStatus = NoonAuthRecoveryItemStatus.STALE;
                    failureCode = "SOURCE_TASK_NOT_RESUMED";
                    diagnostic = "project recovered but source task handler rejected the transition";
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
            if (!transitioned && !worker.renewFence(fence)) {
                return -1;
            }
        }
        return recoveredTasks;
    }

    private static String projectKey(NoonAuthRecoveryItemRecord item) {
        return item.getOwnerUserId() + ":" + item.getProjectCode();
    }

    NoonAuthWaitingTaskOutcome failWaitingTask(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryWorker.ExecutionFence fence,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    ) {
        return waitingTaskRouter.fail(
                item, fence.status, fence.version, fence.leaseToken,
                failureCode, diagnostic, now
        );
    }
}
