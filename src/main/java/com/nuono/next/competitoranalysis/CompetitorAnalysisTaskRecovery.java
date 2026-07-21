package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CompetitorAnalysisTaskRecovery {
    private static final Logger log = LoggerFactory.getLogger(CompetitorAnalysisTaskRecovery.class);
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final int RECOVERY_LIMIT = 1000;
    private static final String STALE_MESSAGE = "刷新任务超过 30 分钟未完成，已自动释放。";

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final Clock clock;
    private final QueuedTaskSubmitter queuedTaskSubmitter;
    private final InterruptedTaskRetry interruptedTaskRetry;

    CompetitorAnalysisTaskRecovery(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            Clock clock,
            QueuedTaskSubmitter queuedTaskSubmitter,
            InterruptedTaskRetry interruptedTaskRetry
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.clock = clock;
        this.queuedTaskSubmitter = queuedTaskSubmitter;
        this.interruptedTaskRetry = interruptedTaskRetry;
    }

    int resumeQueuedRefreshTasks() {
        int resumed = 0;
        for (OperationalTask task : operationalTaskService.listActive(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                RECOVERY_LIMIT
        )) {
            if (task.getStatus() != OperationalTaskStatus.QUEUED) {
                continue;
            }
            CompetitorSearchRunRow run = mapper.selectSearchRunByTaskId(task.getId());
            if (run == null || !"QUEUED".equals(run.getStatus())) {
                continue;
            }
            CompetitorWatchProductRow watchProduct = mapper.selectWatchProductForRefresh(run.getWatchProductId());
            if (watchProduct == null) {
                failMissingWatchProduct(task, run);
                continue;
            }
            if (queuedTaskSubmitter.submit(task, run, watchProduct)) {
                resumed++;
            }
        }
        return resumed;
    }

    int recoverStaleRefreshTasks() {
        return recoverInterruptedProductTasks() + releaseStaleMonitorTasks();
    }

    private int recoverInterruptedProductTasks() {
        int recovered = 0;
        for (OperationalTask task : operationalTaskService.listActive(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                RECOVERY_LIMIT
        )) {
            if (task.getStatus() != OperationalTaskStatus.RUNNING || !isStale(task)) {
                continue;
            }
            CompetitorSearchRunRow run = mapper.selectSearchRunByTaskId(task.getId());
            if (!release(task, run)) {
                continue;
            }
            recovered++;
            retryInterruptedRun(task, run);
        }
        return recovered;
    }

    private int releaseStaleMonitorTasks() {
        int recovered = 0;
        for (OperationalTask task : operationalTaskService.listActive(
                CompetitorAnalysisRefreshService.MONITOR_TASK_TYPE,
                RECOVERY_LIMIT
        )) {
            if (!isStale(task) || !release(task, null)) {
                continue;
            }
            recovered++;
        }
        return recovered;
    }

    private void retryInterruptedRun(OperationalTask task, CompetitorSearchRunRow run) {
        if (run == null) {
            return;
        }
        CompetitorWatchProductRow watchProduct = mapper.selectWatchProductForRefresh(run.getWatchProductId());
        if (watchProduct == null) {
            return;
        }
        try {
            interruptedTaskRetry.retry(watchProduct, run);
        } catch (RuntimeException exception) {
            log.warn(
                    "competitor interrupted refresh retry failed taskId={} runId={} watchProductId={} error={}",
                    task.getId(), run.getId(), run.getWatchProductId(), exception.getMessage(), exception
            );
        }
    }

    private boolean release(OperationalTask task, CompetitorSearchRunRow run) {
        try {
            operationalTaskService.fail(task.getId(), "FAILED_STALE", STALE_MESSAGE);
            if (run != null) {
                mapper.markSearchRunFailed(run.getId(), "FAILED_STALE", STALE_MESSAGE);
            }
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private void failMissingWatchProduct(OperationalTask task, CompetitorSearchRunRow run) {
        mapper.markSearchRunFailed(
                run.getId(),
                "COMPETITOR_WATCH_PRODUCT_NOT_FOUND",
                "监控商品不存在或已删除。"
        );
        operationalTaskService.fail(
                task.getId(),
                "COMPETITOR_WATCH_PRODUCT_NOT_FOUND",
                "监控商品不存在或已删除。"
        );
    }

    private boolean isStale(OperationalTask task) {
        LocalDateTime updatedAt = task.getUpdatedAt() == null ? task.getStartedAt() : task.getUpdatedAt();
        return updatedAt != null
                && Duration.between(updatedAt, LocalDateTime.now(clock)).compareTo(STALE_AFTER) > 0;
    }

    @FunctionalInterface
    interface QueuedTaskSubmitter {
        boolean submit(OperationalTask task, CompetitorSearchRunRow run, CompetitorWatchProductRow watchProduct);
    }

    @FunctionalInterface
    interface InterruptedTaskRetry {
        void retry(CompetitorWatchProductRow watchProduct, CompetitorSearchRunRow run);
    }
}
