package com.nuono.next.noonauth;

import static com.nuono.next.noonauth.NoonAuthRecoveryWorkerValues.projectKey;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Routes source-task transitions while the identity-recovery fence remains live. */
final class NoonAuthWaitingTaskCoordinator {
    private final NoonAuthRecoveryRepository repository;
    private NoonAuthWaitingTaskRouter router;

    NoonAuthWaitingTaskCoordinator(NoonAuthRecoveryRepository repository) {
        this.repository = repository;
        this.router = new NoonAuthWaitingTaskRouter(repository, Collections.emptyList());
    }

    void setHandlers(List<NoonAuthWaitingTaskHandler> handlers) {
        router = new NoonAuthWaitingTaskRouter(repository, handlers);
    }

    NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryWorker.ExecutionFence fence,
            LocalDateTime now
    ) {
        return router.resume(item, fence.status, fence.version, fence.leaseToken, now);
    }

    boolean holdSourceTasks(
            NoonAuthRecoveryWorker worker,
            List<NoonAuthRecoveryItemRecord> items,
            NoonAuthRecoveryProjectTarget target,
            NoonAuthRecoveryWorker.ExecutionFence fence,
            String failureCode,
            String diagnostic
    ) {
        for (NoonAuthRecoveryItemRecord item : items) {
            if (item == null || item.getSourceTaskId() == null
                    || !target.key().equals(projectKey(item))) {
                continue;
            }
            if (!worker.renewFence(fence)) {
                return false;
            }
            NoonAuthWaitingTaskOutcome outcome = router.hold(
                    item, fence.status, fence.version, fence.leaseToken,
                    failureCode, diagnostic, worker.now()
            );
            if (outcome == NoonAuthWaitingTaskOutcome.STALE && !worker.renewFence(fence)) {
                return false;
            }
        }
        return true;
    }

    boolean failSourceTasks(
            NoonAuthRecoveryWorker worker,
            List<NoonAuthRecoveryItemRecord> items,
            NoonAuthRecoveryProjectTarget target,
            NoonAuthRecoveryWorker.ExecutionFence fence,
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
            if (!worker.renewFence(fence)) {
                return false;
            }
            now = worker.now();
            NoonAuthWaitingTaskOutcome outcome = router.fail(
                    item, fence.status, fence.version, fence.leaseToken,
                    failureCode, diagnostic, now
            );
            if (outcome == NoonAuthWaitingTaskOutcome.STALE && !worker.renewFence(fence)) {
                return false;
            }
            taskTerminalItemIds.add(item.getId());
        }
        return true;
    }
}
