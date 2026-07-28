package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CompetitorRefreshRecoveryCoordinator {
    private static final Logger log =
            LoggerFactory.getLogger(CompetitorRefreshRecoveryCoordinator.class);
    private static final String RUNNING_MESSAGE = "竞品刷新正在后台执行。";
    private static final String STALE_MESSAGE = "刷新任务超过 30 分钟未完成，已自动释放。";

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorRefreshTaskFactory taskFactory;
    private final CompetitorRefreshTaskDispatcher taskDispatcher;
    private final Predicate<CompetitorWatchProductRow> executionAllowed;
    private final RefreshExecution refreshExecution;
    private final Clock clock;

    CompetitorRefreshRecoveryCoordinator(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorRefreshTaskFactory taskFactory,
            CompetitorRefreshTaskDispatcher taskDispatcher,
            Predicate<CompetitorWatchProductRow> executionAllowed,
            RefreshExecution refreshExecution,
            Clock clock
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.taskFactory = taskFactory;
        this.taskDispatcher = taskDispatcher;
        this.executionAllowed = executionAllowed;
        this.refreshExecution = refreshExecution;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    CompetitorQueuedRefresh replaceManualStale(
            OperationalTask staleTask,
            CompetitorSearchRunRow staleRun,
            CompetitorWatchProductRow watchProduct,
            LocalDateTime staleBefore,
            Long actorUserId,
            CompetitorRefreshExecutionMode mode,
            String batchKey,
            int keywordTotal
    ) {
        return taskFactory.replaceStale(
                staleTask,
                staleRun,
                watchProduct,
                staleBefore,
                actorUserId,
                mode,
                batchKey,
                keywordTotal,
                queued -> dispatchSafely(queued, watchProduct, actorUserId, mode)
        );
    }

    boolean recoverInterrupted(
            OperationalTask interruptedTask,
            CompetitorWatchProductRow watchProduct,
            CompetitorSearchRunRow run,
            LocalDateTime staleBefore
    ) {
        if (watchProduct == null) {
            return taskFactory.failStale(
                    interruptedTask,
                    run,
                    staleBefore,
                    "FAILED_STALE",
                    STALE_MESSAGE
            );
        }
        CompetitorRefreshExecutionMode mode =
                CompetitorRefreshExecutionMode.fromTriggerMode(run.getTriggerMode());
        int keywordTotal = mode.runsRank()
                ? mapper.listActiveKeywordsByWatchProductId(watchProduct.getId()).size()
                : 0;
        return taskFactory.replaceStale(
                interruptedTask,
                run,
                watchProduct,
                staleBefore,
                run.getRequestedBy(),
                mode,
                CompetitorRefreshRecoveryPayload.batchKey(interruptedTask),
                keywordTotal,
                queued -> dispatchSafely(queued, watchProduct, run.getRequestedBy(), mode)
        ) != null;
    }

    boolean resubmitQueued(
            OperationalTask task,
            CompetitorSearchRunRow run,
            CompetitorWatchProductRow watchProduct
    ) {
        if (!CompetitorRefreshRecoveryPayload.isReady(
                task, LocalDateTime.now(clock)
        ) || !executionAllowed.test(watchProduct)) {
            return false;
        }
        return submit(
                task,
                run,
                watchProduct,
                run.getRequestedBy(),
                CompetitorRefreshExecutionMode.fromTriggerMode(run.getTriggerMode())
        );
    }

    void dispatchQueued(
            CompetitorQueuedRefresh queued,
            CompetitorWatchProductRow watchProduct,
            Long actorUserId,
            CompetitorRefreshExecutionMode mode
    ) {
        OperationalTask task = queued == null || queued.getView() == null
                ? null
                : operationalTaskService.find(queued.getView().getTaskId()).orElse(null);
        CompetitorSearchRunRow run =
                task == null ? null : mapper.selectSearchRunByTaskId(task.getId());
        if (task != null
                && run != null
                && task.getStatus() == OperationalTaskStatus.QUEUED
                && CompetitorRefreshRecoveryPayload.isReady(task, LocalDateTime.now(clock))) {
            submit(task, run, watchProduct, actorUserId, mode);
        }
    }

    private boolean submit(
            OperationalTask task,
            CompetitorSearchRunRow run,
            CompetitorWatchProductRow watchProduct,
            Long actorUserId,
            CompetitorRefreshExecutionMode mode
    ) {
        return taskDispatcher.submit(
                accountKey(watchProduct),
                task,
                run,
                RUNNING_MESSAGE,
                () -> CompetitorRefreshRecoveryPayload.isReady(
                        task, LocalDateTime.now(clock)
                ) && executionAllowed.test(watchProduct),
                () -> refreshExecution.run(
                        task.getId(),
                        run.getId(),
                        watchProduct.getId(),
                        actorUserId,
                        mode
                )
        );
    }

    private void dispatchSafely(
            CompetitorQueuedRefresh queued,
            CompetitorWatchProductRow watchProduct,
            Long actorUserId,
            CompetitorRefreshExecutionMode mode
    ) {
        try {
            dispatchQueued(queued, watchProduct, actorUserId, mode);
        } catch (RuntimeException exception) {
            log.warn(
                    "competitor replacement dispatch failed taskId={} watchProductId={} error={}",
                    queued == null || queued.getView() == null ? null : queued.getView().getTaskId(),
                    watchProduct == null ? null : watchProduct.getId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private String accountKey(CompetitorWatchProductRow watchProduct) {
        String storeCode = watchProduct.getStoreCode();
        return watchProduct.getOwnerUserId() + "::"
                + (storeCode == null ? null : storeCode.trim().toUpperCase(Locale.ROOT));
    }

    @FunctionalInterface
    interface RefreshExecution {
        void run(
                Long taskId,
                Long runId,
                Long watchProductId,
                Long actorUserId,
                CompetitorRefreshExecutionMode mode
        );
    }
}
