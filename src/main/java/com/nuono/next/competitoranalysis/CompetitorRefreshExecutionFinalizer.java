package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompetitorRefreshExecutionFinalizer {
    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorRefreshLeaseGuard leaseGuard;
    private final boolean fenced;

    CompetitorRefreshExecutionFinalizer(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorRefreshLeaseGuard leaseGuard
    ) {
        this(mapper, operationalTaskService, leaseGuard, true);
    }

    private CompetitorRefreshExecutionFinalizer(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorRefreshLeaseGuard leaseGuard,
            boolean fenced
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.leaseGuard = leaseGuard;
        this.fenced = fenced;
    }

    static CompetitorRefreshExecutionFinalizer unfenced(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService
    ) {
        return new CompetitorRefreshExecutionFinalizer(
                mapper,
                operationalTaskService,
                CompetitorRefreshLeaseGuard.disabled(mapper),
                false
        );
    }

    @Transactional
    public void progress(
            Long taskId,
            Long runId,
            Long watchProductId,
            int progressPercent,
            String message
    ) {
        leaseGuard.acquire(taskId, runId, watchProductId);
        operationalTaskService.progress(taskId, progressPercent, message);
    }

    @Transactional
    public <T> T withLease(
            Long taskId,
            Long runId,
            Long watchProductId,
            Supplier<T> action
    ) {
        leaseGuard.acquire(taskId, runId, watchProductId);
        return action.get();
    }

    @Transactional
    public void withLease(
            Long taskId,
            Long runId,
            Long watchProductId,
            Runnable action
    ) {
        leaseGuard.acquire(taskId, runId, watchProductId);
        action.run();
    }

    @Transactional
    public void complete(
            Long taskId,
            Long runId,
            Long watchProductId,
            CompetitorRefreshCompletion completion
    ) {
        if (!fenced) {
            completeLegacy(runId, watchProductId, completion);
            finishTask(taskId, completion);
            return;
        }
        leaseGuard.acquire(taskId, runId, watchProductId);
        int affectedRows = mapper.completeRunningRefreshRun(
                taskId,
                runId,
                watchProductId,
                completion.getRunStatus(),
                completion.getKeywordSuccess(),
                completion.getKeywordFailed(),
                completion.getCandidateUpsertedCount(),
                completion.getRankFactWrittenCount(),
                completion.getRunErrorCode(),
                completion.getRunErrorMessage(),
                completion.getActorUserId()
        );
        leaseGuard.requireMutation(affectedRows, taskId, runId);
        mapper.updateLatestRefreshRunIfNotOlder(
                watchProductId,
                runId,
                completion.getRunStatus(),
                completion.getActorUserId()
        );
        finishTask(taskId, completion);
    }

    @Transactional
    public void fail(
            Long taskId,
            Long runId,
            Long watchProductId,
            String errorCode,
            String errorMessage,
            Long actorUserId
    ) {
        if (!fenced) {
            mapper.markSearchRunFailed(runId, errorCode, errorMessage);
            mapper.updateWatchProductLatestRun(
                    watchProductId, runId, "FAILED", actorUserId
            );
            operationalTaskService.fail(taskId, errorCode, errorMessage);
            return;
        }
        leaseGuard.acquire(taskId, runId, watchProductId);
        leaseGuard.requireMutation(
                mapper.failRunningRefreshRun(
                        taskId,
                        runId,
                        watchProductId,
                        errorCode,
                        errorMessage,
                        actorUserId
                ),
                taskId,
                runId
        );
        mapper.updateLatestRefreshRunIfNotOlder(
                watchProductId, runId, "FAILED", actorUserId
        );
        operationalTaskService.fail(taskId, errorCode, errorMessage);
    }

    private void completeLegacy(
            Long runId,
            Long watchProductId,
            CompetitorRefreshCompletion value
    ) {
        mapper.completeSearchRun(
                runId, value.getRunStatus(),
                value.getKeywordSuccess(), value.getKeywordFailed(),
                value.getCandidateUpsertedCount(),
                value.getRankFactWrittenCount(),
                value.getRunErrorCode(), value.getRunErrorMessage(),
                value.getActorUserId()
        );
        mapper.updateWatchProductLatestRun(
                watchProductId, runId,
                value.getRunStatus(), value.getActorUserId()
        );
    }

    private void finishTask(Long taskId, CompetitorRefreshCompletion value) {
        if (value.getTaskErrorCode() == null) {
            operationalTaskService.complete(
                    taskId, value.getTaskResultJson(), value.getTaskMessage()
            );
            return;
        }
        operationalTaskService.fail(
                taskId, value.getTaskErrorCode(), value.getTaskMessage(),
                value.getTaskResultJson()
        );
    }
}
