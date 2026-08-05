package com.nuono.next.competitoranalysis;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional legacy-only seam for scheduled competitor task identity. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
class LegacyCompetitorScheduledTaskFactory {
    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService tasks;

    LegacyCompetitorScheduledTaskFactory(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService tasks
    ) {
        this.mapper = mapper;
        this.tasks = tasks;
    }

    @Transactional
    OperationalTask persist(
            CompetitorWatchProductRow product,
            CompetitorRefreshExecutionMode mode,
            String naturalKey,
            String batchKey,
            int keywordTotal
    ) {
        if (CompetitorRefreshExecutionMode.requireKnown(mode).isManual()) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Legacy competitor task factory accepts scheduled modes only."
            );
        }
        OperationalTask task = tasks.queue(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                naturalKey,
                OperationalTaskPayload.builder()
                        .ownerUserId(product.getOwnerUserId())
                        .storeCode(product.getStoreCode())
                        .siteCode(product.getSiteCode())
                        .payloadJson(CompetitorRefreshRecoveryPayload.fresh(
                                product.getId(), keywordTotal, mode, batchKey))
                        .message("竞品刷新正在后台执行。")
                        .build()
        );
        if (mapper.selectSearchRunByTaskId(task.getId()) != null) return task;
        CompetitorSearchRunInsertCommand command = new CompetitorSearchRunInsertCommand();
        command.setId(mapper.nextSearchRunId());
        command.setWatchProductId(product.getId());
        command.setTaskId(task.getId());
        command.setTriggerMode(mode.triggerMode());
        command.setStatus("QUEUED");
        command.setKeywordTotal(keywordTotal);
        mapper.insertSearchRun(command);
        return task;
    }
}
