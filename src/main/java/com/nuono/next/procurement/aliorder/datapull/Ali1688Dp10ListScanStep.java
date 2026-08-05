package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRefreshResult;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderRequest;
import java.time.LocalDateTime;

/** Owns PASS1/PASS2 list order and the exact order-independent multiset seal. */
final class Ali1688Dp10ListScanStep {
    private final Ali1688Dp10ScopeSource scopeSource;
    private final Ali1688HistoricalOrderProvider provider;
    private final Ali1688Dp10PageStageStore stageStore;
    private final Ali1688Dp10StageCleanup stageCleanup;
    private final Ali1688Dp10FailurePolicy failurePolicy;
    private final Ali1688Dp10CheckpointCodec checkpointCodec;
    private final Ali1688Dp10PageValidator pageValidator = new Ali1688Dp10PageValidator();

    Ali1688Dp10ListScanStep(
            Ali1688Dp10ScopeSource scopeSource,
            Ali1688HistoricalOrderProvider provider,
            Ali1688Dp10PageStageStore stageStore,
            Ali1688Dp10StageCleanup stageCleanup,
            Ali1688Dp10FailurePolicy failurePolicy,
            Ali1688Dp10CheckpointCodec checkpointCodec
    ) {
        this.scopeSource = scopeSource;
        this.provider = provider;
        this.stageStore = stageStore;
        this.stageCleanup = stageCleanup;
        this.failurePolicy = failurePolicy;
        this.checkpointCodec = checkpointCodec;
    }

    AdvanceResult advance(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        return Ali1688Dp10Job.SEAL_STEP.equals(task.getStepCode())
                ? seal(task, nowUtc, checkpoint)
                : list(task, nowUtc, checkpoint);
    }

    private AdvanceResult list(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        try {
            if (stageCleanup.cleanupOlderGenerations(
                    task, checkpoint.getGenerationNo(), nowUtc)
                    == Ali1688Dp10StageCleanupAdvance.PROGRESSED) {
                return queued(Ali1688Dp10Job.LIST_STEP, checkpoint);
            }
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint,
                    "DP10_STAGE_CLEANUP_UNKNOWN");
        }
        try {
            Ali1688Dp10StagedPage existing = stageStore.load(
                    task, checkpoint.getGenerationNo(), checkpoint.getScanPass(),
                    checkpoint.getPartition(), checkpoint.getPageNo(), nowUtc).orElse(null);
            if (existing != null) return afterStaged(task, checkpoint, existing);
        } catch (Ali1688Dp10PageContractException failure) {
            return driftOrRetry(task, checkpoint, failure.getSanitizedCode());
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint, "DP10_STAGE_READ_UNKNOWN");
        }
        Ali1688HistoricalOrderAuthorizationRow authorization;
        try {
            authorization = requireAuthorization(task);
        } catch (Ali1688Dp10AuthorizationUnavailableException missing) {
            return failurePolicy.auth(task, Ali1688Dp10Job.LIST_STEP, encode(checkpoint));
        } catch (IllegalArgumentException | IllegalStateException invalidScope) {
            return failed(checkpoint, "DP10_SCOPE_INVALID");
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint, "DP10_SCOPE_READ_UNKNOWN");
        }
        AdvanceResult refresh = refreshAuthorization(task, checkpoint, authorization);
        if (refresh != null) return refresh;
        Ali1688HistoricalOrderProvider.Page page;
        try {
            page = provider.fetchOrderList(request(authorization, checkpoint));
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint, "DP10_PROVIDER_UNKNOWN");
        }
        if (page == null) return retry(
                task, Ali1688Dp10Job.LIST_STEP, checkpoint, "DP10_PROVIDER_EMPTY_RESULT");
        if (page.hasFailure()) return failurePolicy.pageFailure(
                task, Ali1688Dp10Job.LIST_STEP, encode(checkpoint), page);
        try {
            Ali1688Dp10ValidatedPage validated = pageValidator.validate(page, checkpoint);
            Ali1688Dp10Checkpoint bound = checkpoint.bindContract(
                    validated.getTotalRecord(), validated.getExpectedPages());
            Ali1688Dp10StagedPage staged = stageStore.stageList(
                    task, checkpoint.getGenerationNo(), checkpoint.getScanPass(),
                    validated, nowUtc);
            return afterStaged(task, bound, staged);
        } catch (Ali1688Dp10PageContractException failure) {
            return driftOrRetry(task, checkpoint, failure.getSanitizedCode());
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint, "DP10_STAGE_WRITE_UNKNOWN");
        }
    }

    private AdvanceResult afterStaged(
            DataPullTask task,
            Ali1688Dp10Checkpoint checkpoint,
            Ali1688Dp10StagedPage staged
    ) {
        Ali1688Dp10Checkpoint next = checkpoint.bindContract(
                staged.getTotalRecord(), staged.getExpectedPages()).afterPage(staged);
        return queued(next.isScansClosed() ? Ali1688Dp10Job.SEAL_STEP : Ali1688Dp10Job.LIST_STEP, next);
    }

    private AdvanceResult seal(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        Ali1688HistoricalOrderProvider.Partition partition = checkpoint.nextSealPartition();
        if (partition == null) return checkpoint.isSealed()
                ? routeAfterSeal(task, nowUtc, checkpoint)
                : failed(checkpoint, "DP10_SEAL_STATE_INVALID");
        try {
            Ali1688Dp10SealBatch batch = stageStore.readSealBatch(
                    task, checkpoint.getGenerationNo(), partition,
                    checkpoint.getSealAfterFingerprint(), nowUtc);
            if (!batch.isMatching()) return restartForSealDrift(task, checkpoint);
            Ali1688Dp10Checkpoint next = checkpoint.afterSealBatch(partition, batch);
            if (!batch.isExhausted()) return queued(Ali1688Dp10Job.SEAL_STEP, next);
            return next.isSealed()
                    ? routeAfterSeal(task, nowUtc, next)
                    : queued(Ali1688Dp10Job.SEAL_STEP, next);
        } catch (Ali1688Dp10PageContractException contractFailure) {
            if ("DP10_MULTIPASS_MULTISET_DRIFT".equals(
                    contractFailure.getSanitizedCode())) {
                return restartForSealDrift(task, checkpoint);
            }
            return retry(task, Ali1688Dp10Job.SEAL_STEP, checkpoint,
                    contractFailure.getSanitizedCode());
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.SEAL_STEP, checkpoint, "DP10_SEAL_UNKNOWN");
        }
    }

    private AdvanceResult restartForSealDrift(
            DataPullTask task,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        try {
            return retry(task, Ali1688Dp10Job.LIST_STEP,
                    checkpoint.restartGeneration(), "DP10_MULTIPASS_MULTISET_DRIFT");
        } catch (RuntimeException overflow) {
            return failed(checkpoint, "DP10_GENERATION_EXHAUSTED");
        }
    }

    private AdvanceResult routeAfterSeal(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        try {
            Ali1688Dp10PendingItem pending = stageStore.nextPendingDetail(
                    task, checkpoint.getGenerationNo(), nowUtc).orElse(null);
            return pending == null
                    ? queued(Ali1688Dp10Job.VERIFY_STEP, checkpoint.atDetail(null))
                    : queued(Ali1688Dp10Job.DETAIL_STEP, checkpoint.atDetail(pending));
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.SEAL_STEP, checkpoint, "DP10_STAGE_READ_UNKNOWN");
        }
    }

    private AdvanceResult driftOrRetry(
            DataPullTask task,
            Ali1688Dp10Checkpoint checkpoint,
            String code
    ) {
        if (!isDrift(code)) return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint, code);
        try {
            return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint.restartGeneration(), code);
        } catch (RuntimeException overflow) {
            return failed(checkpoint, "DP10_GENERATION_EXHAUSTED");
        }
    }

    private boolean isDrift(String code) {
        return "DP10_PARTITION_TOTAL_DRIFT".equals(code)
                || "DP10_PAGE_RAW_ROW_COUNT_INVALID".equals(code)
                || "DP10_PARTITION_RAW_COUNT_MISMATCH".equals(code)
                || "DP10_MULTIPASS_TOTAL_DRIFT".equals(code)
                || "DP10_STAGED_PAGE_DRIFT".equals(code);
    }

    private AdvanceResult refreshAuthorization(
            DataPullTask task,
            Ali1688Dp10Checkpoint checkpoint,
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        try {
            if (!provider.requiresAuthorizationRefresh(authorization)) return null;
            AdvanceResult held = failurePolicy.holdUnknownRefresh(
                    task, Ali1688Dp10Job.LIST_STEP, encode(checkpoint));
            if (held != null) return held;
            Ali1688HistoricalOrderAuthorizationRefreshResult result =
                    provider.refreshAuthorization(authorization);
            if (result == null) return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint,
                    "DP10_AUTH_REFRESH_EMPTY_RESULT");
            return result.isSuccess()
                    ? queued(Ali1688Dp10Job.LIST_STEP, checkpoint)
                    : failurePolicy.refreshFailure(
                            task, Ali1688Dp10Job.LIST_STEP, encode(checkpoint), result);
        } catch (RuntimeException failure) {
            return retry(task, Ali1688Dp10Job.LIST_STEP, checkpoint,
                    "DP10_AUTH_REFRESH_UNKNOWN");
        }
    }

    private Ali1688HistoricalOrderAuthorizationRow requireAuthorization(DataPullTask task) {
        return scopeSource.findForTask(task)
                .orElseThrow(Ali1688Dp10AuthorizationUnavailableException::new);
    }

    private Ali1688HistoricalOrderRequest request(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        return Ali1688HistoricalOrderRequest.window(
                authorization, checkpoint.getMode(), checkpoint.getPartition(),
                checkpoint.getPageNo(), checkpoint.getPageSize(),
                checkpoint.windowStart(), checkpoint.windowEnd());
    }

    private AdvanceResult queued(String step, Ali1688Dp10Checkpoint checkpoint) {
        return AdvanceResult.queued(step, null, encode(checkpoint));
    }

    private AdvanceResult failed(Ali1688Dp10Checkpoint checkpoint, String code) {
        return AdvanceResult.failed(Ali1688Dp10Job.LIST_STEP, null, encode(checkpoint), code);
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
