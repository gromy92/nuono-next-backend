package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CompetitorAnalysisTaskRecovery {
    private static final Logger log = LoggerFactory.getLogger(CompetitorAnalysisTaskRecovery.class);
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final int RECOVERY_LIMIT = 1000;
    private static final int MAX_SCAN_PAGES = 10;
    private static final String STALE_MESSAGE = "刷新任务超过 30 分钟未完成，已自动释放。";
    private static final String ORPHAN_MESSAGE = "刷新任务缺少执行记录，已自动释放。";

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final Clock clock;
    private final QueuedTaskSubmitter queuedTaskSubmitter;
    private final InterruptedTaskRetry interruptedTaskRetry;
    private final IntSupplier dispatchCapacity;
    private long queuedScanCursor;
    private long staleScanCursor;

    CompetitorAnalysisTaskRecovery(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            Clock clock,
            QueuedTaskSubmitter queuedTaskSubmitter,
            InterruptedTaskRetry interruptedTaskRetry
    ) {
        this(
                mapper,
                operationalTaskService,
                clock,
                queuedTaskSubmitter,
                interruptedTaskRetry,
                () -> RECOVERY_LIMIT
        );
    }

    CompetitorAnalysisTaskRecovery(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            Clock clock,
            QueuedTaskSubmitter queuedTaskSubmitter,
            InterruptedTaskRetry interruptedTaskRetry,
            IntSupplier dispatchCapacity
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.clock = clock;
        this.queuedTaskSubmitter = queuedTaskSubmitter;
        this.interruptedTaskRetry = interruptedTaskRetry;
        this.dispatchCapacity = dispatchCapacity;
    }

    synchronized int resumeQueuedRefreshTasks() {
        int capacity = Math.max(0, Math.min(RECOVERY_LIMIT, dispatchCapacity.getAsInt()));
        if (capacity <= 0) {
            return 0;
        }
        int resumed = 0;
        long afterTaskId = queuedScanCursor;
        int scannedPages = 0;
        while (resumed < capacity && scannedPages++ < MAX_SCAN_PAGES) {
            java.util.List<OperationalTask> tasks = nextPage(afterTaskId);
            if (tasks.isEmpty()) {
                queuedScanCursor = 0L;
                break;
            }
            for (OperationalTask task : tasks) {
                afterTaskId = task.getId();
                if (task.getStatus() != OperationalTaskStatus.QUEUED) {
                    continue;
                }
                CompetitorSearchRunRow run = mapper.selectSearchRunByTaskId(task.getId());
                if (run == null) {
                    releaseStaleOrphan(task);
                    continue;
                }
                if (!"QUEUED".equals(run.getStatus())) {
                    continue;
                }
                CompetitorWatchProductRow watchProduct = mapper.selectWatchProductForRefresh(run.getWatchProductId());
                if (watchProduct == null) {
                    failMissingWatchProduct(task, run);
                    continue;
                }
                if (queuedTaskSubmitter.submit(task, run, watchProduct) && ++resumed >= capacity) {
                    break;
                }
            }
            queuedScanCursor = afterTaskId;
        }
        return resumed;
    }

    int recoverStaleRefreshTasks() {
        return recoverInterruptedProductTasks();
    }

    private synchronized int recoverInterruptedProductTasks() {
        int recovered = 0;
        long afterTaskId = staleScanCursor;
        int scannedPages = 0;
        LocalDateTime staleBefore = LocalDateTime.now(clock).minus(STALE_AFTER);
        while (recovered < RECOVERY_LIMIT && scannedPages++ < MAX_SCAN_PAGES) {
            java.util.List<OperationalTask> tasks = nextPage(afterTaskId);
            if (tasks.isEmpty()) {
                staleScanCursor = 0L;
                break;
            }
            for (OperationalTask task : tasks) {
                afterTaskId = task.getId();
                if (task.getStatus() != OperationalTaskStatus.RUNNING || !isStale(task, staleBefore)) {
                    continue;
                }
                CompetitorSearchRunRow run = mapper.selectSearchRunByTaskId(task.getId());
                if (run == null) {
                    if (releaseStaleRunningOrphan(task, staleBefore)) {
                        recovered++;
                    }
                    continue;
                }
                CompetitorWatchProductRow watchProduct =
                        mapper.selectWatchProductForRefresh(run.getWatchProductId());
                if (recoverInterruptedRun(task, run, watchProduct, staleBefore)) {
                    recovered++;
                }
                if (recovered >= RECOVERY_LIMIT) {
                    break;
                }
            }
            staleScanCursor = afterTaskId;
        }
        return recovered;
    }

    private java.util.List<OperationalTask> nextPage(long afterTaskId) {
        return operationalTaskService.listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                afterTaskId,
                RECOVERY_LIMIT
        );
    }

    private boolean recoverInterruptedRun(
            OperationalTask task,
            CompetitorSearchRunRow run,
            CompetitorWatchProductRow watchProduct,
            LocalDateTime staleBefore
    ) {
        try {
            return interruptedTaskRetry.retry(task, watchProduct, run, staleBefore);
        } catch (RuntimeException exception) {
            log.warn(
                    "competitor interrupted refresh retry failed taskId={} runId={} watchProductId={} error={}",
                    task.getId(), run.getId(), run.getWatchProductId(), exception.getMessage(), exception
            );
            return false;
        }
    }

    private boolean releaseStaleRunningOrphan(OperationalTask task, LocalDateTime staleBefore) {
        return operationalTaskService.failStaleRunning(
                task.getId(),
                staleBefore,
                "FAILED_STALE",
                STALE_MESSAGE
        );
    }

    private void releaseStaleOrphan(OperationalTask task) {
        operationalTaskService.failStaleQueued(
                task.getId(),
                LocalDateTime.now(clock).minus(STALE_AFTER),
                "COMPETITOR_SEARCH_RUN_MISSING",
                ORPHAN_MESSAGE
        );
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

    private boolean isStale(OperationalTask task, LocalDateTime staleBefore) {
        LocalDateTime updatedAt = task.getUpdatedAt() == null ? task.getStartedAt() : task.getUpdatedAt();
        return updatedAt != null && !updatedAt.isAfter(staleBefore);
    }

    @FunctionalInterface
    interface QueuedTaskSubmitter {
        boolean submit(OperationalTask task, CompetitorSearchRunRow run, CompetitorWatchProductRow watchProduct);
    }

    @FunctionalInterface
    interface InterruptedTaskRetry {
        boolean retry(
                OperationalTask task,
                CompetitorWatchProductRow watchProduct,
                CompetitorSearchRunRow run,
                LocalDateTime staleBefore
        );
    }
}
