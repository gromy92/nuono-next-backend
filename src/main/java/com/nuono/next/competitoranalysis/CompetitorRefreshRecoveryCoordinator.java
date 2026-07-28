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
    private final CompetitorRefreshInvalidRecovery invalidRecovery;
    private final CompetitorRefreshTaskDispatcher taskDispatcher;
    private final Predicate<CompetitorWatchProductRow> executionAllowed;
    private final Predicate<OperationalTask> taskReadiness;
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
        this(
                mapper,
                operationalTaskService,
                taskFactory,
                taskDispatcher,
                executionAllowed,
                null,
                refreshExecution,
                clock
        );
    }

    CompetitorRefreshRecoveryCoordinator(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorRefreshTaskFactory taskFactory,
            CompetitorRefreshTaskDispatcher taskDispatcher,
            Predicate<CompetitorWatchProductRow> executionAllowed,
            Predicate<OperationalTask> taskReadiness,
            RefreshExecution refreshExecution,
            Clock clock
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.taskFactory = taskFactory;
        this.invalidRecovery = new CompetitorRefreshInvalidRecovery(taskFactory);
        this.taskDispatcher = taskDispatcher;
        this.executionAllowed = executionAllowed;
        this.refreshExecution = refreshExecution;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.taskReadiness = taskReadiness == null
                ? task -> CompetitorRefreshRecoveryPayload.isReady(
                        task, LocalDateTime.now(this.clock)
                )
                : taskReadiness;
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
        CompetitorRefreshExecutionMode mode;
        try {
            mode = CompetitorRefreshExecutionMode.strictFromTriggerMode(
                    run.getTriggerMode()
            );
        } catch (CompetitorRefreshRecoveryIdentityException exception) {
            return invalidRecovery.failStale(interruptedTask, run, staleBefore, false);
        }
        if (mode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                && !hasRecoverableDetailState(
                        interruptedTask, watchProduct.getId(), mode
                )) {
            return invalidRecovery.failStale(interruptedTask, run, staleBefore, true);
        }
        int keywordTotal = mode.runsRank()
                ? mapper.listActiveKeywordsByWatchProductId(watchProduct.getId()).size()
                : 0;
        String batchKey;
        try {
            batchKey = CompetitorRefreshRecoveryPayload.batchKey(interruptedTask);
        } catch (CompetitorRefreshRecoveryPayloadException exception) {
            return invalidRecovery.failStale(interruptedTask, run, staleBefore, false);
        }
        try {
            return taskFactory.replaceStale(
                    interruptedTask,
                    run,
                    watchProduct,
                    staleBefore,
                    run.getRequestedBy(),
                    mode,
                    batchKey,
                    keywordTotal,
                    queued -> dispatchSafely(
                            queued, watchProduct, run.getRequestedBy(), mode
                    )
            ) != null;
        } catch (CompetitorRefreshRecoveryIdentityException exception) {
            return invalidRecovery.failStale(interruptedTask, run, staleBefore, false);
        }
    }

    private boolean hasRecoverableDetailState(
            OperationalTask task,
            Long watchProductId,
            CompetitorRefreshExecutionMode mode
    ) {
        try {
            return CompetitorDetailRetryPayload.fromJson(
                    task == null ? null : task.getPayloadJson()
            ).isInitialized()
                    && CompetitorRefreshRecoveryPayload.matchesIdentity(
                            task, watchProductId, mode
                    );
        } catch (CompetitorDetailRetryPayloadException
                | CompetitorRefreshRecoveryPayloadException exception) {
            return false;
        }
    }

    boolean resubmitQueued(
            OperationalTask task,
            CompetitorSearchRunRow run,
            CompetitorWatchProductRow watchProduct
    ) {
        if (!isReady(task, run) || !executionAllowed.test(watchProduct)) {
            return false;
        }
        try {
            return submit(
                    task,
                    run,
                    watchProduct,
                    run.getRequestedBy(),
                    CompetitorRefreshExecutionMode.strictFromTriggerMode(
                            run.getTriggerMode()
                    )
            );
        } catch (CompetitorRefreshRecoveryIdentityException exception) {
            invalidRecovery.failQueued(task, run);
            return false;
        }
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
                && isReady(task, run)) {
            try {
                submit(task, run, watchProduct, actorUserId, mode);
            } catch (CompetitorRefreshRecoveryIdentityException exception) {
                invalidRecovery.failQueued(task, run);
            }
        }
    }

    private boolean isReady(OperationalTask task, CompetitorSearchRunRow run) {
        try {
            return taskReadiness.test(task);
        } catch (CompetitorDetailRetryPayloadException
                | CompetitorRefreshRecoveryPayloadException exception) {
            invalidRecovery.failQueued(task, run);
            return false;
        }
    }

    private boolean submit(
            OperationalTask task,
            CompetitorSearchRunRow run,
            CompetitorWatchProductRow watchProduct,
            Long actorUserId,
            CompetitorRefreshExecutionMode mode
    ) {
        CompetitorRefreshRecoveryIdentity.validate(task, run, watchProduct, mode);
        return taskDispatcher.submit(
                accountKey(watchProduct),
                task,
                run,
                RUNNING_MESSAGE,
                () -> taskReadiness.test(task) && executionAllowed.test(watchProduct),
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
