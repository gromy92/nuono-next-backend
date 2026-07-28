package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
class CompetitorRefreshTaskFactory {
    private static final String TASK_MESSAGE = "竞品刷新正在后台执行。";

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;

    CompetitorRefreshTaskFactory(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
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
        OperationalTask task = operationalTaskService.queue(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                naturalKey,
                OperationalTaskPayload.builder()
                        .ownerUserId(watchProduct.getOwnerUserId())
                        .storeCode(watchProduct.getStoreCode())
                        .siteCode(watchProduct.getSiteCode())
                        .payloadJson(payloadJson(watchProduct.getId(), keywordTotal, mode, batchKey))
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
        if (!claimLinkedStale(staleTask, staleRun, staleBefore, "FAILED_STALE",
                "刷新任务超过 30 分钟未完成，已自动释放。")) {
            return null;
        }
        CompetitorQueuedRefresh replacement = persistQueued(
                watchProduct,
                requestedBy,
                mode,
                staleTask.getNaturalKey(),
                batchKey,
                keywordTotal
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
        return claimLinkedStale(staleTask, staleRun, staleBefore, errorCode, errorMessage);
    }

    private boolean claimLinkedStale(
            OperationalTask staleTask,
            CompetitorSearchRunRow staleRun,
            LocalDateTime staleBefore,
            String errorCode,
            String errorMessage
    ) {
        if (staleTask == null || staleTask.getId() == null || staleBefore == null) {
            return false;
        }
        if (staleRun != null && !Objects.equals(staleTask.getId(), staleRun.getTaskId())) {
            throw new IllegalArgumentException("Competitor search run is not linked to the stale task.");
        }
        if (!operationalTaskService.failStaleRunning(
                staleTask.getId(),
                staleBefore,
                errorCode,
                errorMessage
        )) {
            return false;
        }
        if (staleRun != null && mapper.markActiveSearchRunFailedForTask(
                staleRun.getId(),
                staleTask.getId(),
                errorCode,
                errorMessage
        ) != 1) {
            throw new IllegalStateException("Competitor search run changed during stale recovery.");
        }
        return true;
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

    private String payloadJson(
            Long watchProductId,
            int keywordTotal,
            CompetitorRefreshExecutionMode mode,
            String batchKey
    ) {
        return "{"
                + "\"watchProductId\":" + watchProductId
                + ",\"keywordTotal\":" + keywordTotal
                + ",\"triggerMode\":\"" + json(mode.triggerMode()) + "\""
                + ",\"executionMode\":\"" + json(mode.taskKey()) + "\""
                + ",\"rankRefresh\":" + mode.runsRank()
                + ",\"detailRefresh\":" + mode.runsDetail()
                + (StringUtils.hasText(batchKey) ? ",\"batchKey\":\"" + json(batchKey) + "\"" : "")
                + "}";
    }

    private boolean payloadHasBatchKey(OperationalTask task, String batchKey) {
        return task != null
                && StringUtils.hasText(batchKey)
                && StringUtils.hasText(task.getPayloadJson())
                && task.getPayloadJson().contains("\"batchKey\":\"" + json(batchKey) + "\"");
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
