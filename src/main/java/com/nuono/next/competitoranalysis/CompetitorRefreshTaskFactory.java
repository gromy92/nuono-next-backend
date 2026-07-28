package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
