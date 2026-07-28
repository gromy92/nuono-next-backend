package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private static final int MAX_SCAN_PAGES = 10;
    private static final String QUEUED_MESSAGE = "竞品监控批次等待持久化任务。";
    private static final String STALE_MESSAGE = "竞品监控批次超过 30 分钟未更新，已由检查点续跑。";
    private final CompetitorMonitoringMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorMonitoringRecoveryService recoveryService;
    private final CompetitorTaskSubmitter taskSubmitter;
    private final CompetitorMonitoringBatchRunner runner;
    private final Clock clock;
    private final CompetitorMonitoringPlanFactory plans;
    private final Set<Long> submittedStoreTaskIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> queuedScanCursors = new ConcurrentHashMap<>();
    private final Map<String, Long> staleScanCursors = new ConcurrentHashMap<>();
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
        this.plans = new CompetitorMonitoringPlanFactory(clock);
        this.runner = new CompetitorMonitoringBatchRunner(
                mapper,
                operationalTaskService,
                productEnqueuer,
                childTaskPump
        );
    }
    CompetitorTaskView requestStore(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long requestedBy,
            CompetitorRefreshExecutionMode mode
    ) {
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
    int runScheduledCycle(CompetitorRefreshExecutionMode mode) {
        String naturalKey = plans.cycleNaturalKey(mode);
        OperationalTask active = operationalTaskService.findActive(CYCLE_TASK_TYPE, naturalKey).orElse(null);
        if (active != null) {
            return active.getStatus() == OperationalTaskStatus.QUEUED ? runner.run(active) : 0;
        }
        OperationalTask latest = operationalTaskService.findLatest(CYCLE_TASK_TYPE, naturalKey).orElse(null);
        if (latest != null && latest.getStatus() == OperationalTaskStatus.SUCCEEDED) {
            return plans.completedScopes(latest.getResultJson());
        }
        CompetitorMonitoringBoundaryRow boundary = mapper.selectRefreshableScopeBoundary();
        if (boundary == null || boundary.getUpperWatchProductId() == null || plans.eligibleTotal(boundary) <= 0L) {
            return 0;
        }
        CompetitorWatchProductScopeRow upper = mapper.selectRefreshableScopeUpperBound(
                boundary.getUpperWatchProductId()
        );
        if (upper == null) {
            return 0;
        }
        CompetitorMonitoringCheckpoint checkpoint = plans.cycleCheckpoint(naturalKey, mode, boundary, upper);
        OperationalTask task = queue(CYCLE_TASK_TYPE, naturalKey, null, null, null, checkpoint);
        return runner.run(task);
    }
    synchronized int resumeQueuedBatches() {
        int resumed = 0;
        for (String taskType : List.of(CYCLE_TASK_TYPE, STORE_TASK_TYPE)) {
            long afterTaskId = queuedScanCursors.getOrDefault(taskType, 0L);
            int scannedPages = 0;
            while (resumed < RECOVERY_LIMIT && scannedPages++ < MAX_SCAN_PAGES) {
                List<OperationalTask> tasks = activeBatchPage(taskType, afterTaskId);
                if (tasks.isEmpty()) {
                    queuedScanCursors.put(taskType, 0L);
                    break;
                }
                for (OperationalTask task : tasks) {
                    afterTaskId = task.getId();
                    if (task.getStatus() != OperationalTaskStatus.QUEUED) {
                        continue;
                    }
                    if (CYCLE_TASK_TYPE.equals(taskType)) {
                        runner.run(task);
                        resumed++;
                    } else if (submitStore(task)) {
                        resumed++;
                    }
                    if (resumed >= RECOVERY_LIMIT) {
                        break;
                    }
                }
                queuedScanCursors.put(taskType, afterTaskId);
            }
        }
        return resumed;
    }

    synchronized int recoverStaleBatches() {
        int recovered = 0;
        LocalDateTime staleBefore = LocalDateTime.now(clock).minus(STALE_AFTER);
        for (String taskType : List.of(CYCLE_TASK_TYPE, STORE_TASK_TYPE)) {
            long afterTaskId = staleScanCursors.getOrDefault(taskType, 0L);
            int scannedPages = 0;
            while (recovered < RECOVERY_LIMIT && scannedPages++ < MAX_SCAN_PAGES) {
                List<OperationalTask> tasks = activeBatchPage(taskType, afterTaskId);
                if (tasks.isEmpty()) {
                    staleScanCursors.put(taskType, 0L);
                    break;
                }
                for (OperationalTask task : tasks) {
                    afterTaskId = task.getId();
                    if (task.getStatus() != OperationalTaskStatus.RUNNING
                            || !isStale(task, staleBefore)) {
                        continue;
                    }
                    OperationalTask replacement = replaceStale(task);
                    if (replacement == null) {
                        continue;
                    }
                    if (CYCLE_TASK_TYPE.equals(taskType)) {
                        runner.run(replacement);
                    } else {
                        submitStore(replacement);
                    }
                    if (++recovered >= RECOVERY_LIMIT) {
                        break;
                    }
                }
                staleScanCursors.put(taskType, afterTaskId);
            }
        }
        return recovered;
    }
    private OperationalTask replaceStale(OperationalTask task) {
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
        if (submittedStoreTaskIds.size() >= RECOVERY_LIMIT) {
            return false;
        }
        if (!submittedStoreTaskIds.add(task.getId())) {
            return false;
        }
        try {
            taskSubmitter.submit(accountKey(task), () -> {
                try {
                    task.setPayloadJson(replacementPayload(task));
                    runner.run(task);
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

    private List<OperationalTask> activeBatchPage(String taskType, long afterTaskId) {
        return operationalTaskService.listActiveAfter(taskType, afterTaskId, RECOVERY_LIMIT);
    }

    private String accountKey(OperationalTask task) {
        return task.getOwnerUserId() + "::" + task.getStoreCode();
    }

    private boolean isStale(OperationalTask task, LocalDateTime staleBefore) {
        LocalDateTime updatedAt = task.getUpdatedAt() == null ? task.getStartedAt() : task.getUpdatedAt();
        return updatedAt != null && !updatedAt.isAfter(staleBefore);
    }

}
