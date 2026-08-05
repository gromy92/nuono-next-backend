package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.checkpoint.DataPullScopeProgress;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressStore;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Routes one bounded DP-10 action; scan/detail/fact behavior stays behind deep Modules. */
final class Ali1688Dp10StepExecutor {
    private final Ali1688Dp10ScopeSource scopeSource;
    private final Ali1688Dp10FactWriter factWriter;
    private final DataPullScopeProgressStore progressStore;
    private final Ali1688Dp10CheckpointCodec checkpointCodec;
    private final Ali1688Dp10FailurePolicy failurePolicy;
    private final Ali1688Dp10ListScanStep listScanStep;
    private final Ali1688Dp10DetailStep detailStep;
    private final Ali1688Dp10StageCleanup stageCleanup;

    Ali1688Dp10StepExecutor(
            Ali1688Dp10ScopeSource scopeSource,
            Ali1688HistoricalOrderProvider provider,
            Ali1688Dp10PageStageStore stageStore,
            Ali1688Dp10StageCleanup stageCleanup,
            Ali1688Dp10FactWriter factWriter,
            DataPullScopeProgressStore progressStore,
            Ali1688Dp10CheckpointCodec checkpointCodec,
            Ali1688Dp10FailurePolicy failurePolicy
    ) {
        this.scopeSource = scopeSource;
        this.factWriter = factWriter;
        this.progressStore = progressStore;
        this.checkpointCodec = checkpointCodec;
        this.failurePolicy = failurePolicy;
        this.stageCleanup = stageCleanup;
        this.listScanStep = new Ali1688Dp10ListScanStep(
                scopeSource, provider, stageStore, stageCleanup,
                failurePolicy, checkpointCodec);
        this.detailStep = new Ali1688Dp10DetailStep(
                scopeSource, provider, stageStore, failurePolicy, checkpointCodec);
    }

    AdvanceResult advance(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        if (Ali1688Dp10Job.LIST_STEP.equals(task.getStepCode())
                || Ali1688Dp10Job.SEAL_STEP.equals(task.getStepCode())) {
            return listScanStep.advance(task, nowUtc, checkpoint);
        }
        if (Ali1688Dp10Job.DETAIL_STEP.equals(task.getStepCode())) {
            return detailStep.advance(task, nowUtc, checkpoint);
        }
        if (Ali1688Dp10Job.VERIFY_STEP.equals(task.getStepCode())
                || Ali1688Dp10Job.APPLY_STEP.equals(task.getStepCode())) {
            return facts(task, nowUtc, checkpoint);
        }
        if (Ali1688Dp10Job.CLEANUP_STEP.equals(task.getStepCode())) {
            return cleanup(task, nowUtc, checkpoint);
        }
        return AdvanceResult.failed(task.getCheckpoint(), "DP10_TASK_STEP_INVALID");
    }

    private AdvanceResult facts(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        Ali1688HistoricalOrderAuthorizationRow authorization;
        try {
            authorization = requireAuthorization(task);
        } catch (Ali1688Dp10AuthorizationUnavailableException missing) {
            return failurePolicy.auth(task, task.getStepCode(), encode(checkpoint));
        } catch (RuntimeException failure) {
            return retry(task, task.getStepCode(), checkpoint, "DP10_SCOPE_READ_UNKNOWN");
        }
        try {
            if (!checkpoint.isSealed()) {
                return failed(task.getStepCode(), checkpoint, "DP10_APPLY_BEFORE_SEAL");
            }
            Ali1688Dp10FactAdvance outcome = factWriter.advance(command(
                    task, authorization, checkpoint, nowUtc));
            if (outcome == Ali1688Dp10FactAdvance.COMPLETE) {
                return queued(Ali1688Dp10Job.CLEANUP_STEP, checkpoint);
            }
            return queued(outcome == Ali1688Dp10FactAdvance.VERIFYING
                    ? Ali1688Dp10Job.VERIFY_STEP : Ali1688Dp10Job.APPLY_STEP, checkpoint);
        } catch (Ali1688Dp10AuthorizationUnavailableException missing) {
            return failurePolicy.auth(task, task.getStepCode(), encode(checkpoint));
        } catch (Ali1688Dp10ProgressConflictException conflict) {
            return reconcileProgressConflict(task, checkpoint, nowUtc);
        } catch (RuntimeException failure) {
            return retry(task, task.getStepCode(), checkpoint, "DP10_FACT_WRITE_UNKNOWN");
        }
    }

    private Ali1688Dp10ApplyCommand command(
            DataPullTask task,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688Dp10Checkpoint checkpoint,
            LocalDateTime nowUtc
    ) {
        return new Ali1688Dp10ApplyCommand(
                task,
                authorization,
                checkpoint.getGenerationNo(),
                checkpoint.getPassOneCurrentTotal(),
                checkpoint.getPassOneCurrentPages(),
                checkpoint.getPassOneHistoryTotal(),
                checkpoint.getPassOneHistoryPages(),
                checkpoint.getExpectedProgressVersion(),
                checkpoint.windowEnd(),
                nowUtc
        );
    }

    private AdvanceResult reconcileProgressConflict(
            DataPullTask task,
            Ali1688Dp10Checkpoint checkpoint,
            LocalDateTime nowUtc
    ) {
        try {
            DataPullScopeProgress current = progressStore.getOrCreate(
                    OperationCode.DP10, task.getScopeKey(), nowUtc);
            LocalDateTime committed = current.getOfficialModifiedHighWaterUtc();
            boolean covers = current.isInitialFullCompleted() && committed != null
                    && !committed.toInstant(ZoneOffset.UTC).isBefore(checkpoint.windowEnd());
            return covers
                    ? queued(Ali1688Dp10Job.CLEANUP_STEP, checkpoint)
                    : queued(Ali1688Dp10Job.APPLY_STEP,
                            checkpoint.withExpectedProgressVersion(current.getVersion()));
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.APPLY_STEP, checkpoint,
                    "DP10_PROGRESS_RECONCILE_UNKNOWN");
        }
    }

    private AdvanceResult cleanup(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        try {
            Ali1688Dp10StageCleanupAdvance outcome = stageCleanup.cleanupCurrentGeneration(
                    task, checkpoint.getGenerationNo(), nowUtc);
            return outcome == Ali1688Dp10StageCleanupAdvance.COMPLETE
                    ? AdvanceResult.succeeded()
                    : queued(Ali1688Dp10Job.CLEANUP_STEP, checkpoint);
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.CLEANUP_STEP, checkpoint,
                    "DP10_STAGE_CLEANUP_UNKNOWN");
        }
    }

    private Ali1688HistoricalOrderAuthorizationRow requireAuthorization(DataPullTask task) {
        return scopeSource.findForTask(task)
                .orElseThrow(Ali1688Dp10AuthorizationUnavailableException::new);
    }

    private AdvanceResult queued(String step, Ali1688Dp10Checkpoint checkpoint) {
        return AdvanceResult.queued(step, null, encode(checkpoint));
    }

    private AdvanceResult failed(String step, Ali1688Dp10Checkpoint checkpoint, String code) {
        return AdvanceResult.failed(step, null, encode(checkpoint), code);
    }

    private AdvanceResult retry(
            DataPullTask task,
            String step,
            Ali1688Dp10Checkpoint checkpoint,
            String code
    ) {
        return failurePolicy.transientFailure(task, step, encode(checkpoint), code);
    }

    private String encode(Ali1688Dp10Checkpoint checkpoint) {
        return checkpointCodec.encode(checkpoint);
    }
}
