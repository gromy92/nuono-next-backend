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

    CompetitorRefreshExecutionFinalizer(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorRefreshLeaseGuard leaseGuard
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.leaseGuard = leaseGuard;
    }

    static CompetitorRefreshExecutionFinalizer unfenced(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService
    ) {
        return new CompetitorRefreshExecutionFinalizer(
                mapper,
                operationalTaskService,
                CompetitorRefreshLeaseGuard.disabled(mapper)
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
    public void checkpointDetailRetry(
            Long taskId,
            Long runId,
            Long watchProductId,
            String payloadJson
    ) {
        leaseGuard.acquire(taskId, runId, watchProductId);
        if (!operationalTaskService.checkpointRunning(
                taskId, payloadJson, 5, "竞品详情重试状态已保存。"
        )) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
    }

    @Transactional
    public void requeueDetailRetry(
            Long taskId,
            Long runId,
            Long watchProductId,
            String payloadJson,
            String errorCode,
            String errorMessage
    ) {
        leaseGuard.acquire(taskId, runId, watchProductId);
        if (!operationalTaskService.requeueRunning(
                taskId,
                payloadJson,
                5,
                errorCode,
                errorMessage
        )) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
        int affectedRows = mapper.requeueSearchRun(
                taskId,
                runId,
                watchProductId,
                errorCode,
                errorMessage
        );
        if (affectedRows != 1) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
    }

    @Transactional
    public boolean failQueued(
            Long taskId,
            Long runId,
            Long watchProductId,
            String errorCode,
            String errorMessage
    ) {
        if (mapper.lockQueuedRefreshTask(taskId) == null
                || mapper.lockQueuedRefreshRun(taskId, runId, watchProductId) == null) {
            return false;
        }
        if (mapper.failQueuedRefreshRun(
                taskId, runId, watchProductId, errorCode, errorMessage
        ) != 1) {
            throw new IllegalStateException("Queued competitor refresh run changed during finalization.");
        }
        operationalTaskService.fail(taskId, errorCode, errorMessage);
        return true;
    }

    @Transactional
    public void complete(
            Long taskId,
            Long runId,
            Long watchProductId,
            String runStatus,
            int keywordSuccess,
            int keywordFailed,
            int candidateUpsertedCount,
            int rankFactWrittenCount,
            String runErrorCode,
            String runErrorMessage,
            Long actorUserId,
            String taskErrorCode,
            String taskResultJson,
            String taskMessage
    ) {
        leaseGuard.acquire(taskId, runId, watchProductId);
        int affectedRows = mapper.completeRunningRefreshRun(
                taskId,
                runId,
                watchProductId,
                runStatus,
                keywordSuccess,
                keywordFailed,
                candidateUpsertedCount,
                rankFactWrittenCount,
                runErrorCode,
                runErrorMessage,
                actorUserId
        );
        leaseGuard.requireMutation(affectedRows, taskId, runId);
        mapper.updateLatestRefreshRunIfNotOlder(
                watchProductId, runId, runStatus, actorUserId
        );
        if (taskErrorCode == null) {
            operationalTaskService.complete(taskId, taskResultJson, taskMessage);
        } else {
            operationalTaskService.fail(taskId, taskErrorCode, taskMessage, taskResultJson);
        }
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
        leaseGuard.acquire(taskId, runId, watchProductId);
        int affectedRows = mapper.failRunningRefreshRun(
                taskId,
                runId,
                watchProductId,
                errorCode,
                errorMessage,
                actorUserId
        );
        leaseGuard.requireMutation(affectedRows, taskId, runId);
        mapper.updateLatestRefreshRunIfNotOlder(
                watchProductId, runId, "FAILED", actorUserId
        );
        operationalTaskService.fail(taskId, errorCode, errorMessage);
    }
}
