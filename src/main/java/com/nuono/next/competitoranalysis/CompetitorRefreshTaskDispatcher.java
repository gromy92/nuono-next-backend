package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class CompetitorRefreshTaskDispatcher {
    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorTaskSubmitter taskSubmitter;
    private final Set<Long> submittedTaskIds = ConcurrentHashMap.newKeySet();

    CompetitorRefreshTaskDispatcher(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.taskSubmitter = taskSubmitter;
    }

    boolean submit(
            String accountKey,
            OperationalTask task,
            CompetitorSearchRunRow run,
            String runningMessage,
            Runnable execution
    ) {
        if (!submittedTaskIds.add(task.getId())) {
            return false;
        }
        try {
            taskSubmitter.submit(accountKey, () -> executeClaimed(task, run, runningMessage, execution));
            return true;
        } catch (RuntimeException exception) {
            submittedTaskIds.remove(task.getId());
            throw exception;
        }
    }

    private void executeClaimed(
            OperationalTask task,
            CompetitorSearchRunRow run,
            String runningMessage,
            Runnable execution
    ) {
        try {
            if (!operationalTaskService.claimQueued(task.getId(), runningMessage)) {
                return;
            }
            mapper.markSearchRunRunning(run.getId());
            execution.run();
        } finally {
            submittedTaskIds.remove(task.getId());
        }
    }
}
