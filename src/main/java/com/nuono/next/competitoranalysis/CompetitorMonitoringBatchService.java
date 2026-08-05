package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class CompetitorMonitoringBatchService {
    static final String STORE_TASK_TYPE = "OPERATIONS_COMPETITOR_MONITORING";
    static final String CYCLE_TASK_TYPE = "OPERATIONS_COMPETITOR_MONITORING_CYCLE";
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final int RECOVERY_LIMIT = 1000;
    private static final String QUEUED_MESSAGE = "竞品监控批次等待持久化任务。";
    private static final String STALE_MESSAGE = "竞品监控批次超过 30 分钟未更新，已由检查点续跑。";
    private final CompetitorMonitoringMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorMonitoringRecoveryService recoveryService;
    private final CompetitorTaskSubmitter taskSubmitter;
    private final CompetitorMonitoringBatchRunner runner;
    private final Clock clock;
    private final CompetitorMonitoringPlanFactory plans;
    private final CompetitorMonitoringBatchRecovery recovery;
    private final Set<Long> submittedStoreTaskIds = ConcurrentHashMap.newKeySet();
    CompetitorMonitoringBatchService(
            CompetitorMonitoringMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorMonitoringRecoveryService recoveryService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorMonitoringBatchRunner.ProductEnqueuer productEnqueuer,
            Runnable childTaskPump,
            Clock clock
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.recoveryService = recoveryService;
        this.taskSubmitter = taskSubmitter;
        this.clock = clock;
        this.plans = new CompetitorMonitoringPlanFactory();
        this.runner = new CompetitorMonitoringBatchRunner(
                mapper,
                operationalTaskService,
                productEnqueuer,
                childTaskPump
        );
        this.recovery = new CompetitorMonitoringBatchRecovery(
                operationalTaskService,
                clock,
                this::resumeQueuedTask,
                this::restartStaleTask,
                this::replaceStale,
                task -> CompetitorManualRecoveryScope.includesBatch(task, plans)
        );
    }
    CompetitorTaskView requestStore(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long requestedBy
    ) {
        CompetitorRefreshExecutionMode mode =
                CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR;
        String naturalKey = plans.storeNaturalKey(ownerUserId, storeCode, siteCode, mode);
        OperationalTask active = operationalTaskService.findActive(STORE_TASK_TYPE, naturalKey).orElse(null);
        if (active != null) {
            if (active.getStatus() == OperationalTaskStatus.QUEUED) {
                submitStore(active);
            } else if (isStale(active, LocalDateTime.now(clock).minus(STALE_AFTER))) {
                OperationalTask replacement = replaceStale(active);
                if (replacement != null) {
                    submitStore(replacement);
                    return CompetitorTaskView.from(replacement);
                }
            }
            return CompetitorTaskView.from(active);
        }
        CompetitorMonitoringBoundaryRow boundary = mapper.selectRefreshableWatchProductBoundary(
                ownerUserId,
                storeCode,
                siteCode
        );
        if (boundary == null || boundary.getUpperWatchProductId() == null || plans.eligibleTotal(boundary) <= 0L) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "COMPETITOR_MONITOR_NO_REFRESHABLE_PRODUCT"
            );
        }
        CompetitorMonitoringCheckpoint checkpoint = plans.storeCheckpoint(
                ownerUserId,
                storeCode,
                siteCode,
                requestedBy,
                mode,
                boundary
        );
        OperationalTask task = queue(
                STORE_TASK_TYPE,
                naturalKey,
                ownerUserId,
                storeCode,
                siteCode,
                checkpoint
        );
        submitStore(task);
        return CompetitorTaskView.from(task);
    }
    synchronized int resumeQueuedManualBatches() {
        return recovery.resumeQueued();
    }

    synchronized int recoverStaleManualBatches() {
        return recovery.recoverStale();
    }
    private OperationalTask replaceStale(OperationalTask task) {
        if (!isManualBatch(task)) {
            return null;
        }
        String replacementPayloadJson = replacementPayload(task);
        return recoveryService.replaceStale(
                task,
                LocalDateTime.now(clock).minus(STALE_AFTER),
                "FAILED_STALE",
                STALE_MESSAGE,
                QUEUED_MESSAGE,
                replacementPayloadJson
        );
    }
    private String replacementPayload(OperationalTask task) {
        if (!STORE_TASK_TYPE.equals(task.getTaskType())) {
            return task.getPayloadJson();
        }
        CompetitorRefreshExecutionMode legacyMode = plans.legacyStoreMode(task.getPayloadJson());
        if (legacyMode == null) {
            return task.getPayloadJson();
        }
        CompetitorMonitoringBoundaryRow boundary = mapper.selectRefreshableWatchProductBoundary(
                task.getOwnerUserId(),
                task.getStoreCode(),
                task.getSiteCode()
        );
        if (boundary == null || boundary.getUpperWatchProductId() == null) {
            boundary = new CompetitorMonitoringBoundaryRow();
            boundary.setEligibleTotal(0L);
            boundary.setUpperWatchProductId(0L);
        }
        return plans.storeCheckpoint(
                task.getOwnerUserId(),
                task.getStoreCode(),
                task.getSiteCode(),
                null,
                legacyMode,
                boundary
        ).toJson();
    }
    private boolean submitStore(OperationalTask task) {
        if (!isManualBatch(task)) {
            return false;
        }
        if (submittedStoreTaskIds.size() >= RECOVERY_LIMIT) {
            return false;
        }
        if (!submittedStoreTaskIds.add(task.getId())) {
            return false;
        }
        try {
            taskSubmitter.submit(accountKey(task), () -> {
                try {
                    operationalTaskService.prepareQueuedPayload(
                            task, replacementPayload(task), QUEUED_MESSAGE
                    ).ifPresent(runner::run);
                } finally {
                    submittedStoreTaskIds.remove(task.getId());
                }
            });
            return true;
        } catch (RuntimeException exception) {
            submittedStoreTaskIds.remove(task.getId());
            if (exception instanceof RejectedExecutionException) {
                return false;
            }
            throw exception;
        }
    }

    private boolean resumeQueuedTask(OperationalTask task) {
        if (!isManualBatch(task)) {
            return false;
        }
        return submitStore(task);
    }

    private void restartStaleTask(OperationalTask task) {
        if (isManualBatch(task)) {
            submitStore(task);
        }
    }

    private boolean isManualBatch(OperationalTask task) {
        return CompetitorManualRecoveryScope.includesBatch(task, plans);
    }

    private OperationalTask queue(
            String taskType,
            String naturalKey,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            CompetitorMonitoringCheckpoint checkpoint
    ) {
        return operationalTaskService.queue(
                taskType,
                naturalKey,
                OperationalTaskPayload.builder()
                        .ownerUserId(ownerUserId)
                        .storeCode(storeCode)
                        .siteCode(siteCode)
                        .payloadJson(checkpoint.toJson())
                        .message(QUEUED_MESSAGE)
                        .build()
        );
    }

    private String accountKey(OperationalTask task) {
        return task.getOwnerUserId() + "::" + task.getStoreCode();
    }

    private boolean isStale(OperationalTask task, LocalDateTime staleBefore) {
        LocalDateTime updatedAt = task.getUpdatedAt() == null ? task.getStartedAt() : task.getUpdatedAt();
        return updatedAt != null && !updatedAt.isAfter(staleBefore);
    }

}
