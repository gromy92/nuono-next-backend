package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
class CompetitorRefreshTaskFactory {
    private static final String TASK_MESSAGE = "竞品刷新正在后台执行。";
    private static final String INVALID_RETRY_PAYLOAD = "INVALID_DETAIL_RETRY_PAYLOAD";

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorStaleTaskReconciler staleTaskReconciler;
    private final CompetitorRefreshExecutionFinalizer executionFinalizer;
    private final CompetitorDetailBatchTakeover detailBatchTakeover;
    CompetitorRefreshTaskFactory(CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService) {
        this(mapper, operationalTaskService,
                CompetitorRefreshExecutionFinalizer.unfenced(mapper, operationalTaskService));
    }
    CompetitorRefreshTaskFactory(CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorRefreshExecutionFinalizer executionFinalizer) {
        this(mapper, operationalTaskService, executionFinalizer,
                new CompetitorDetailBatchTakeover(mapper, operationalTaskService, executionFinalizer));
    }
    @Autowired
    CompetitorRefreshTaskFactory(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorRefreshExecutionFinalizer executionFinalizer,
            CompetitorDetailBatchTakeover detailBatchTakeover
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.staleTaskReconciler = new CompetitorStaleTaskReconciler(
                mapper, operationalTaskService);
        this.executionFinalizer = executionFinalizer;
        this.detailBatchTakeover = detailBatchTakeover;
    }

    @Transactional
    public CompetitorQueuedRefresh persistQueued(
            CompetitorWatchProductRow watchProduct,
            Long requestedBy,
            CompetitorRefreshExecutionMode mode,
            String naturalKey,
            String batchKey,
            int keywordTotal
    ) {
        return persistQueued(
                watchProduct,
                requestedBy,
                mode,
                naturalKey,
                batchKey,
                keywordTotal,
                null
        );
    }

    @Transactional
    public CompetitorQueuedRefresh persistQueued(
            CompetitorWatchProductRow watchProduct,
            Long requestedBy,
            CompetitorRefreshExecutionMode mode,
            String naturalKey,
            String batchKey,
            int keywordTotal,
            String payloadJsonOverride
    ) {
        OperationalTask task = operationalTaskService.queue(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                naturalKey,
                OperationalTaskPayload.builder()
                        .ownerUserId(watchProduct.getOwnerUserId())
                        .storeCode(watchProduct.getStoreCode())
                        .siteCode(watchProduct.getSiteCode())
                        .payloadJson(StringUtils.hasText(payloadJsonOverride)
                                ? payloadJsonOverride
                                : CompetitorRefreshRecoveryPayload.fresh(
                                        watchProduct.getId(), keywordTotal, mode, batchKey
                                ))
                        .message(TASK_MESSAGE)
                        .build()
        );
        CompetitorSearchRunRow existingRun = mapper.selectSearchRunByTaskId(task.getId());
        if (existingRun != null) {
            return existing(task, existingRun, batchKey);
        }
        if (StringUtils.hasText(batchKey) && !payloadHasBatchKey(task, batchKey)) {
            return existing(task, null, batchKey);
        }
        CompetitorSearchRunInsertCommand command = new CompetitorSearchRunInsertCommand();
        command.setId(mapper.nextSearchRunId());
        command.setWatchProductId(watchProduct.getId());
        command.setTaskId(task.getId());
        command.setTriggerMode(mode.triggerMode());
        command.setStatus("QUEUED");
        command.setRequestedBy(requestedBy);
        command.setKeywordTotal(keywordTotal);
        command.setActorUserId(requestedBy);
        mapper.insertSearchRun(command);
        return new CompetitorQueuedRefresh(
                CompetitorRefreshRunView.from(task, runRow(command)),
                CompetitorMonitoringEnqueueOutcome.CREATED
        );
    }

    @Transactional
    public CompetitorQueuedRefresh replaceStale(
            OperationalTask staleTask,
            CompetitorSearchRunRow staleRun,
            CompetitorWatchProductRow watchProduct,
            LocalDateTime staleBefore,
            Long requestedBy,
            CompetitorRefreshExecutionMode mode,
            String batchKey,
            int keywordTotal,
            Consumer<CompetitorQueuedRefresh> afterCommit
    ) {
        CompetitorRefreshRecoveryIdentity.validate(
                staleTask, staleRun, watchProduct, mode
        );
        if (mode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                && detailBatchTakeover.supersedeStaleIfNewerBatchExists(
                        staleTask, staleRun, watchProduct.getId()
                )) {
            return reconciledTerminal(staleTask);
        }
        String replacementPayload = CompetitorRefreshRecoveryPayload.replacement(
                staleTask, watchProduct.getId(), keywordTotal, mode, batchKey
        );
        CompetitorStaleTaskReconciler.Outcome claim = staleTaskReconciler.claim(
                staleTask,
                staleRun,
                staleBefore,
                "FAILED_STALE",
                "刷新任务超过 30 分钟未完成，已自动释放。"
        );
        if (claim == CompetitorStaleTaskReconciler.Outcome.NOT_CLAIMED) {
            return null;
        }
        if (claim == CompetitorStaleTaskReconciler.Outcome.TERMINAL_RECONCILED) {
            return reconciledTerminal(staleTask);
        }
        CompetitorQueuedRefresh replacement = persistQueued(
                watchProduct,
                requestedBy,
                mode,
                staleTask.getNaturalKey(),
                batchKey,
                keywordTotal,
                replacementPayload
        );
        if (replacement == null
                || replacement.getOutcome() != CompetitorMonitoringEnqueueOutcome.CREATED
                || replacement.getView() == null
                || Objects.equals(staleTask.getId(), replacement.getView().getTaskId())) {
            throw new IllegalStateException("Competitor stale replacement was not persisted.");
        }
        dispatchAfterCommit(replacement, afterCommit);
        return replacement;
    }

    @Transactional
    public boolean failStale(
            OperationalTask staleTask,
            CompetitorSearchRunRow staleRun,
            LocalDateTime staleBefore,
            String errorCode,
            String errorMessage
    ) {
        return staleTaskReconciler.claim(
                staleTask, staleRun, staleBefore, errorCode, errorMessage
        ) != CompetitorStaleTaskReconciler.Outcome.NOT_CLAIMED;
    }

    private CompetitorQueuedRefresh reconciledTerminal(OperationalTask staleTask) {
        OperationalTask task = operationalTaskService.find(staleTask.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Competitor reconciled task disappeared: " + staleTask.getId()
                ));
        CompetitorSearchRunRow run = mapper.selectSearchRunByTaskId(staleTask.getId());
        return new CompetitorQueuedRefresh(
                CompetitorRefreshRunView.from(task, run),
                CompetitorMonitoringEnqueueOutcome.STALE_TERMINAL_RECONCILED
        );
    }

    private void dispatchAfterCommit(
            CompetitorQueuedRefresh replacement,
            Consumer<CompetitorQueuedRefresh> afterCommit
    ) {
        if (afterCommit == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            afterCommit.accept(replacement);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommit.accept(replacement);
            }
        });
    }

    public boolean requeueDetailRetry(
            Long taskId,
            Long runId,
            String payloadJson,
            String errorCode,
            String message
    ) {
        CompetitorSearchRunRow run = mapper.selectSearchRunByTaskId(taskId);
        if (run == null
                || !Objects.equals(runId, run.getId())
                || run.getWatchProductId() == null) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
        executionFinalizer.requeueDetailRetry(
                taskId,
                runId,
                run.getWatchProductId(),
                payloadJson,
                errorCode,
                message
        );
        return true;
    }

    public boolean failInvalidDetailRetryPayload(Long taskId) {
        String message = "竞品详情重试载荷损坏，任务已终止以避免阻塞恢复队列。";
        CompetitorSearchRunRow run = mapper.selectSearchRunByTaskId(taskId);
        if (run == null) {
            return false;
        }
        return executionFinalizer.failQueued(
                taskId,
                run.getId(),
                run.getWatchProductId(),
                INVALID_RETRY_PAYLOAD,
                message
        );
    }

    private CompetitorQueuedRefresh existing(
            OperationalTask task,
            CompetitorSearchRunRow run,
            String batchKey
    ) {
        CompetitorMonitoringEnqueueOutcome outcome = !StringUtils.hasText(batchKey)
                || payloadHasBatchKey(task, batchKey)
                ? CompetitorMonitoringEnqueueOutcome.REUSED_SAME_BATCH
                : CompetitorMonitoringEnqueueOutcome.DEFERRED_ACTIVE;
        return new CompetitorQueuedRefresh(CompetitorRefreshRunView.from(task, run), outcome);
    }

    private CompetitorSearchRunRow runRow(CompetitorSearchRunInsertCommand command) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(command.getId());
        row.setWatchProductId(command.getWatchProductId());
        row.setTaskId(command.getTaskId());
        row.setTriggerMode(command.getTriggerMode());
        row.setStatus(command.getStatus());
        row.setRequestedBy(command.getRequestedBy());
        row.setKeywordTotal(command.getKeywordTotal());
        row.setKeywordSuccess(0);
        row.setKeywordFailed(0);
        row.setCandidateUpsertedCount(0);
        row.setRankFactWrittenCount(0);
        return row;
    }

    private boolean payloadHasBatchKey(OperationalTask task, String batchKey) {
        return StringUtils.hasText(batchKey)
                && batchKey.trim().equals(CompetitorRefreshRecoveryPayload.batchKey(task));
    }

    CompetitorRefreshExecutionFinalizer executionFinalizer() {
        return executionFinalizer;
    }

    CompetitorDetailBatchTakeover detailBatchTakeover() { return detailBatchTakeover; }

}
