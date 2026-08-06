package com.nuono.next.competitoranalysis;

import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Restarts only persisted user-requested store-monitoring batches. */
final class CompetitorMonitoringBatchRecovery {
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final int RECOVERY_LIMIT = 1000;
    private static final int MAX_SCAN_PAGES = 10;
    private final OperationalTaskService taskService;
    private final Clock clock;
    private final Predicate<OperationalTask> queuedResumer;
    private final Consumer<OperationalTask> staleRestarter;
    private final Function<OperationalTask, OperationalTask> staleReplacer;
    private final Predicate<OperationalTask> manualTask;
    private long queuedCursor;
    private long staleCursor;

    CompetitorMonitoringBatchRecovery(
            OperationalTaskService taskService,
            Clock clock,
            Predicate<OperationalTask> queuedResumer,
            Consumer<OperationalTask> staleRestarter,
            Function<OperationalTask, OperationalTask> staleReplacer,
            Predicate<OperationalTask> manualTask
    ) {
        this.taskService = taskService;
        this.clock = clock;
        this.queuedResumer = queuedResumer;
        this.staleRestarter = staleRestarter;
        this.staleReplacer = staleReplacer;
        this.manualTask = manualTask;
    }

    synchronized int resumeQueued() {
        int resumed = 0;
        long afterTaskId = queuedCursor;
        int scannedPages = 0;
        while (resumed < RECOVERY_LIMIT && scannedPages++ < MAX_SCAN_PAGES) {
            List<OperationalTask> tasks = page(afterTaskId);
            if (tasks.isEmpty()) {
                queuedCursor = 0L;
                break;
            }
            for (OperationalTask task : tasks) {
                afterTaskId = task.getId();
                if (task.getStatus() != OperationalTaskStatus.QUEUED
                        || !manualTask.test(task)) {
                    continue;
                }
                if (queuedResumer.test(task) && ++resumed >= RECOVERY_LIMIT) {
                    break;
                }
            }
            queuedCursor = afterTaskId;
        }
        return resumed;
    }

    synchronized int recoverStale() {
        int recovered = 0;
        LocalDateTime staleBefore = LocalDateTime.now(clock).minus(STALE_AFTER);
        long afterTaskId = staleCursor;
        int scannedPages = 0;
        while (recovered < RECOVERY_LIMIT && scannedPages++ < MAX_SCAN_PAGES) {
            List<OperationalTask> tasks = page(afterTaskId);
            if (tasks.isEmpty()) {
                staleCursor = 0L;
                break;
            }
            for (OperationalTask task : tasks) {
                afterTaskId = task.getId();
                if (task.getStatus() != OperationalTaskStatus.RUNNING
                        || !isStale(task, staleBefore)
                        || !manualTask.test(task)) {
                    continue;
                }
                OperationalTask replacement = staleReplacer.apply(task);
                if (replacement == null) {
                    continue;
                }
                staleRestarter.accept(replacement);
                if (++recovered >= RECOVERY_LIMIT) {
                    break;
                }
            }
            staleCursor = afterTaskId;
        }
        return recovered;
    }

    private List<OperationalTask> page(long afterTaskId) {
        return taskService.listActiveAfter(
                CompetitorMonitoringBatchService.STORE_TASK_TYPE,
                afterTaskId,
                RECOVERY_LIMIT
        );
    }

    private boolean isStale(OperationalTask task, LocalDateTime staleBefore) {
        LocalDateTime updatedAt = task.getUpdatedAt() == null
                ? task.getStartedAt()
                : task.getUpdatedAt();
        return updatedAt != null && !updatedAt.isAfter(staleBefore);
    }
}
