package com.nuono.next.noonpull;

import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/** Atomically attaches one durable pull task to the shared authorization queue. */
final class NoonPullAuthWaitSupport {
    private final NoonPullRepository repository;
    private NoonAuthWaitQueue queue = request -> Optional.empty();

    NoonPullAuthWaitSupport(NoonPullRepository repository) {
        this.repository = repository;
    }

    void setQueue(NoonAuthWaitQueue queue) {
        this.queue = queue == null ? request -> Optional.empty() : queue;
    }

    NoonPullTaskRecord block(
            NoonPullTaskRecord task,
            String diagnostic,
            LocalDateTime now
    ) {
        if (task == null || !NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task)) {
            return null;
        }
        String sourceDomain = task.getDataDomain() == null ? "NOON_PULL" : task.getDataDomain().name();
        Optional<Long> recoveryId = queue.enqueue(NoonAuthWaitRequest.task(
                task.getOwnerUserId(), null, task.getStoreCode(), task.getSiteCode(),
                sourceDomain, task.getId(),
                sourceCheckpoint(task),
                NoonAuthResumePolicy.AUTO_RESUME, task.getStartedAt()
        ));
        if (recoveryId.isEmpty()) {
            return null;
        }
        int blockedRows = repository.blockTaskForAuth(task.getId(), recoveryId.get(), diagnostic, now);
        NoonPullTaskRecord blocked = requireTask(task.getId());
        if ((blockedRows != 1 && (blocked.getStatus() != NoonPullTaskStatus.BLOCKED_AUTH
                || !Objects.equals(recoveryId.get(), blocked.getAuthRecoveryId())))
                || blocked.getStatus() != NoonPullTaskStatus.BLOCKED_AUTH
                || !Objects.equals(recoveryId.get(), blocked.getAuthRecoveryId())) {
            throw new IllegalStateException(
                    "Noon pull task could not enter the shared auth wait queue " + task.getId()
            );
        }
        NoonPullPlanRecord plan = requirePlan(blocked.getPlanId());
        plan.setLatestFailureAt(now);
        plan.setLatestFailureType(NoonPullFailureType.AUTH_REQUIRED.code());
        plan.setNextRetryAt(null);
        plan.setUpdatedAt(now);
        repository.updatePlan(plan);
        return blocked.copy();
    }

    private String sourceCheckpoint(NoonPullTaskRecord task) {
        String checkpoint = firstNonBlank(
                task.getCheckpointCursor(),
                task.getNextResumePosition(),
                "PERSISTED_TASK_STATE"
        );
        return checkpoint.length() <= 64 ? checkpoint : checkpoint.substring(0, 64);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        throw new IllegalStateException("Noon pull auth checkpoint is unavailable.");
    }

    private NoonPullTaskRecord requireTask(Long taskId) {
        NoonPullTaskRecord task = repository.selectTask(taskId);
        if (task == null) {
            throw new IllegalStateException("Noon pull task does not exist: " + taskId);
        }
        return task;
    }

    private NoonPullPlanRecord requirePlan(Long planId) {
        NoonPullPlanRecord plan = repository.selectPlan(planId);
        if (plan == null) {
            throw new IllegalStateException("Noon pull plan does not exist: " + planId);
        }
        return plan;
    }
}
