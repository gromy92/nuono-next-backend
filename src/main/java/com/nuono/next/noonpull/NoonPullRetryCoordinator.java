package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonPullRetryCoordinator {
    private final NoonPullRepository repository;
    private final NoonPullFoundationService foundationService;
    private final Clock clock;

    @Autowired
    public NoonPullRetryCoordinator(
            NoonPullRepository repository,
            NoonPullFoundationService foundationService
    ) {
        this(repository, foundationService, Clock.systemUTC());
    }

    NoonPullRetryCoordinator(
            NoonPullRepository repository,
            NoonPullFoundationService foundationService,
            Clock clock
    ) {
        this.repository = repository;
        this.foundationService = foundationService;
        this.clock = clock;
    }

    public List<NoonPullTaskRecord> retryDueFailedTasks() {
        LocalDateTime now = now();
        List<NoonPullTaskRecord> retried = new ArrayList<>();
        for (NoonPullTaskRecord task : repository.listTasks()) {
            if (!isDueLatestRetryableTask(task, now)) {
                continue;
            }
            NoonPullTaskRecord retry = foundationService.retryTask(task.getId());
            if (!Objects.equals(retry.getId(), task.getId())) {
                retried.add(retry);
            }
            clearNextRetryAt(task.getPlanId(), now);
        }
        return retried;
    }

    public int attemptNumber(NoonPullTaskRecord task) {
        if (task == null || !StringUtils.hasText(task.getActiveLockKey())) {
            return 1;
        }
        long attempts = repository.listTasks().stream()
                .filter(candidate -> candidate != null
                        && task.getActiveLockKey().equals(candidate.getActiveLockKey())
                        && candidate.getCreatedAt() != null
                        && task.getCreatedAt() != null
                        && !candidate.getCreatedAt().isAfter(task.getCreatedAt()))
                .count();
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, attempts));
    }

    public NoonPullTaskRecord task(Long taskId) {
        NoonPullTaskRecord task = repository.selectTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Noon pull task not found: " + taskId);
        }
        return task.copy();
    }

    public void clearBackoffAfterSuccess(NoonPullTaskRecord task) {
        if (task == null || task.getPlanId() == null) {
            return;
        }
        NoonPullPlanRecord plan = repository.selectPlan(task.getPlanId());
        if (plan == null) {
            return;
        }
        plan.setLatestFailureType(null);
        plan.setNextRetryAt(null);
        plan.setUpdatedAt(now());
        repository.updatePlan(plan);
    }

    private boolean isDueLatestRetryableTask(NoonPullTaskRecord task, LocalDateTime now) {
        if (task == null
                || task.getStatus() != NoonPullTaskStatus.FAILED
                || !Boolean.TRUE.equals(task.getRetryable())
                || Boolean.TRUE.equals(task.getRequiresManualAction())
                || task.getDataDomain() != NoonPullDataDomain.OFFICIAL_WAREHOUSE_ASN
                || task.getTriggerMode() != NoonPullTriggerMode.MANUAL_REFRESH
                || !StringUtils.hasText(task.getActiveLockKey())) {
            return false;
        }
        NoonPullTaskRecord latest = repository.selectLatestTaskByLockKey(task.getActiveLockKey());
        NoonPullPlanRecord plan = repository.selectPlan(task.getPlanId());
        return latest != null
                && Objects.equals(latest.getId(), task.getId())
                && plan != null
                && plan.isEnabled()
                && !plan.isPaused()
                && plan.getNextRetryAt() != null
                && !plan.getNextRetryAt().isAfter(now);
    }

    private void clearNextRetryAt(Long planId, LocalDateTime now) {
        NoonPullPlanRecord plan = repository.selectPlan(planId);
        if (plan != null) {
            plan.setNextRetryAt(null);
            plan.setUpdatedAt(now);
            repository.updatePlan(plan);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
