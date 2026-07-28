package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompetitorDetailBatchTakeover {
    private static final String SUPERSEDED_DETAIL_BATCH =
            "SUPERSEDED_BY_NEW_DETAIL_BATCH";

    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorRefreshExecutionFinalizer executionFinalizer;

    CompetitorDetailBatchTakeover(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorRefreshExecutionFinalizer executionFinalizer
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.executionFinalizer = executionFinalizer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompetitorDetailBatchTakeoverOutcome takeoverOlderBatches(
            Long taskId,
            Long runId,
            Long watchProductId
    ) {
        return executionFinalizer.withLease(
                taskId,
                runId,
                watchProductId,
                () -> takeoverWithLease(taskId, runId, watchProductId)
        );
    }

    @Transactional
    public boolean supersedeStaleIfNewerBatchExists(
            OperationalTask staleTask,
            CompetitorSearchRunRow staleRun,
            Long watchProductId
    ) {
        long staleRoot = CompetitorDetailBatchOwnership.chainRoot(
                staleTask.getPayloadJson(), staleRun.getId()
        );
        String staleBatch = CompetitorDetailBatchOwnership.batchKey(
                staleTask.getPayloadJson()
        );
        List<CompetitorDetailTakeoverCandidateRow> candidates =
                mapper.listScheduledDetailOwnershipCandidates(
                        watchProductId, staleTask.getId(), staleRun.getId()
                );
        if (candidates == null) {
            return false;
        }
        CompetitorDetailTakeoverCandidateRow owner = null;
        long ownerRoot = staleRoot;
        for (CompetitorDetailTakeoverCandidateRow candidate : candidates) {
            CompetitorDetailBatchOwnership.Key candidateOwnership =
                    CompetitorDetailBatchOwnership.strictCandidate(candidate);
            if (candidateOwnership == null
                    || candidateOwnership.rootRunId <= staleRoot
                    || Objects.equals(candidateOwnership.batchKey, staleBatch)
                    || !ownershipPair(candidate)) {
                continue;
            }
            if (owner == null || candidateOwnership.rootRunId > ownerRoot) {
                owner = candidate;
                ownerRoot = candidateOwnership.rootRunId;
            }
        }
        return owner != null && supersedeActiveDetail(
                staleTask.getId(),
                staleRun.getId(),
                watchProductId,
                owner.getTaskId(),
                owner.getRunId()
        );
    }

    private CompetitorDetailBatchTakeoverOutcome takeoverWithLease(
            Long taskId,
            Long runId,
            Long watchProductId
    ) {
        OperationalTask currentTask = operationalTaskService.find(taskId)
                .orElseThrow(() -> new CompetitorRefreshLeaseLostException(taskId, runId));
        CompetitorSearchRunRow currentRun = mapper.selectSearchRunById(runId);
        requireScheduledDetailIdentity(
                currentTask, currentRun, taskId, runId, watchProductId
        );
        long currentRoot = CompetitorDetailBatchOwnership.chainRoot(
                currentTask.getPayloadJson(), runId
        );
        String currentBatch = CompetitorDetailBatchOwnership.batchKey(
                currentTask.getPayloadJson()
        );
        List<CompetitorDetailTakeoverCandidateRow> candidates =
                mapper.listScheduledDetailOwnershipCandidates(
                        watchProductId, taskId, runId
                );
        CompetitorDetailTakeoverCandidateRow newerOwner = newerOwner(
                candidates, currentRoot, currentBatch
        );
        if (newerOwner != null) {
            if (!supersedeActiveDetail(
                    taskId,
                    runId,
                    watchProductId,
                    newerOwner.getTaskId(),
                    newerOwner.getRunId()
            )) {
                throw new CompetitorRefreshLeaseLostException(taskId, runId);
            }
            return CompetitorDetailBatchTakeoverOutcome.currentSuperseded();
        }
        int superseded = 0;
        if (candidates == null) {
            return CompetitorDetailBatchTakeoverOutcome.olderSuperseded(
                    superseded
            );
        }
        for (CompetitorDetailTakeoverCandidateRow candidate : candidates) {
            CompetitorDetailBatchOwnership.Key candidateOwnership =
                    CompetitorDetailBatchOwnership.strictCandidate(candidate);
            if (candidateOwnership == null
                    || !activePair(candidate)
                    || candidateOwnership.rootRunId >= currentRoot
                    || Objects.equals(
                            candidateOwnership.batchKey,
                            currentBatch
                    )) {
                continue;
            }
            if (supersedeActiveDetail(
                    candidate.getTaskId(),
                    candidate.getRunId(),
                    watchProductId,
                    taskId,
                    runId
            )) {
                superseded++;
            }
        }
        return CompetitorDetailBatchTakeoverOutcome.olderSuperseded(superseded);
    }

    private CompetitorDetailTakeoverCandidateRow newerOwner(
            List<CompetitorDetailTakeoverCandidateRow> candidates,
            long currentRoot,
            String currentBatch
    ) {
        if (candidates == null) {
            return null;
        }
        CompetitorDetailTakeoverCandidateRow owner = null;
        long ownerRoot = currentRoot;
        for (CompetitorDetailTakeoverCandidateRow candidate : candidates) {
            CompetitorDetailBatchOwnership.Key candidateOwnership =
                    CompetitorDetailBatchOwnership.strictCandidate(candidate);
            if (candidateOwnership == null
                    || candidateOwnership.rootRunId <= currentRoot
                    || Objects.equals(
                            candidateOwnership.batchKey, currentBatch
                    )
                    || !ownershipPair(candidate)) {
                continue;
            }
            if (owner == null || candidateOwnership.rootRunId > ownerRoot) {
                owner = candidate;
                ownerRoot = candidateOwnership.rootRunId;
            }
        }
        return owner;
    }

    private boolean supersedeActiveDetail(
            Long oldTaskId,
            Long oldRunId,
            Long watchProductId,
            Long supersedingTaskId,
            Long supersedingRunId
    ) {
        String taskStatus = mapper.lockActiveScheduledDetailTask(oldTaskId);
        if (!active(taskStatus)) {
            return false;
        }
        String runStatus = mapper.lockActiveScheduledDetailRun(
                oldTaskId, oldRunId, watchProductId
        );
        if (!active(runStatus)) {
            return false;
        }
        String resultJson = supersededResultJson(
                supersedingTaskId, supersedingRunId
        );
        String message = SUPERSEDED_DETAIL_BATCH
                + " supersedingTaskId=" + supersedingTaskId
                + " supersedingRunId=" + supersedingRunId;
        if (mapper.supersedeActiveScheduledDetailTask(
                oldTaskId, taskStatus, resultJson, message
        ) != 1) {
            throw new IllegalStateException(
                    "Competitor detail task changed during batch takeover."
            );
        }
        if (mapper.supersedeActiveScheduledDetailRun(
                oldTaskId, oldRunId, watchProductId, runStatus
        ) != 1) {
            throw new IllegalStateException(
                    "Competitor detail run changed during batch takeover."
            );
        }
        return true;
    }

    private void requireScheduledDetailIdentity(
            OperationalTask task,
            CompetitorSearchRunRow run,
            Long taskId,
            Long runId,
            Long watchProductId
    ) {
        if (task == null
                || run == null
                || !Objects.equals(taskId, task.getId())
                || !Objects.equals(runId, run.getId())
                || !Objects.equals(taskId, run.getTaskId())
                || !Objects.equals(watchProductId, run.getWatchProductId())
                || !CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                        .triggerMode()
                        .equals(run.getTriggerMode())) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
    }

    private boolean activePair(CompetitorDetailTakeoverCandidateRow candidate) {
        return candidate != null
                && active(candidate.getTaskStatus())
                && active(candidate.getRunStatus());
    }

    private boolean ownershipPair(CompetitorDetailTakeoverCandidateRow candidate) {
        return candidate != null
                && Objects.equals(
                        candidate.getTaskStatus(), candidate.getRunStatus()
                )
                && ownership(candidate.getTaskStatus())
                && ownership(candidate.getRunStatus());
    }

    private boolean active(String status) {
        return "QUEUED".equals(status) || "RUNNING".equals(status);
    }

    private boolean ownership(String status) {
        return active(status) || "SUCCEEDED".equals(status);
    }

    private String supersededResultJson(
            Long supersedingTaskId,
            Long supersedingRunId
    ) {
        return "{\"code\":\"" + SUPERSEDED_DETAIL_BATCH
                + "\",\"supersedingTaskId\":" + supersedingTaskId
                + ",\"supersedingRunId\":" + supersedingRunId + "}";
    }

}
