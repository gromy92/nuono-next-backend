package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRefreshResult;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.LocalDateTime;
import java.util.Optional;

/** Performs at most one exact detail call after both list partitions have closed. */
final class Ali1688Dp10DetailStep {
    private final Ali1688Dp10ScopeSource scopeSource;
    private final Ali1688HistoricalOrderProvider provider;
    private final Ali1688Dp10PageStageStore stageStore;
    private final Ali1688Dp10FailurePolicy failurePolicy;
    private final Ali1688Dp10CheckpointCodec checkpointCodec;
    private final Ali1688Dp10OrderValidator orderValidator = new Ali1688Dp10OrderValidator();

    Ali1688Dp10DetailStep(
            Ali1688Dp10ScopeSource scopeSource,
            Ali1688HistoricalOrderProvider provider,
            Ali1688Dp10PageStageStore stageStore,
            Ali1688Dp10FailurePolicy failurePolicy,
            Ali1688Dp10CheckpointCodec checkpointCodec
    ) {
        this.scopeSource = scopeSource;
        this.provider = provider;
        this.stageStore = stageStore;
        this.failurePolicy = failurePolicy;
        this.checkpointCodec = checkpointCodec;
    }

    AdvanceResult advance(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        if (!checkpoint.isSealed()) {
            return retry(task, checkpoint, "DP10_DETAIL_BEFORE_SEAL");
        }
        Ali1688Dp10PendingItem pending;
        try {
            pending = stageStore.nextPendingDetail(
                    task, checkpoint.getGenerationNo(), nowUtc).orElse(null);
        } catch (RuntimeException failure) {
            return retry(task, checkpoint, "DP10_STAGE_READ_UNKNOWN");
        }
        if (pending == null) return queued(Ali1688Dp10Job.VERIFY_STEP, checkpoint.atDetail(null));
        if (!same(checkpoint, pending)) {
            return queued(Ali1688Dp10Job.DETAIL_STEP, checkpoint.atDetail(pending));
        }
        Ali1688Dp10StagedOrder stagedOrder;
        try {
            stagedOrder = stageStore.load(
                    task, pending.getGenerationNo(), pending.getScanPass(),
                    pending.getPartition(), pending.getPageNo(), nowUtc)
                    .orElseThrow(() -> new Ali1688Dp10PageContractException(
                            "DP10_STAGED_PAGE_MISSING"))
                    .orderAt(pending.getItemOrdinal());
        } catch (Ali1688Dp10PageContractException contractFailure) {
            return retry(task, checkpoint, contractFailure.getSanitizedCode());
        } catch (RuntimeException failure) {
            return retry(task, checkpoint, "DP10_STAGE_READ_UNKNOWN");
        }
        Ali1688HistoricalOrderAuthorizationRow authorization;
        try {
            authorization = scopeSource.findForTask(task)
                    .orElseThrow(Ali1688Dp10AuthorizationUnavailableException::new);
        } catch (Ali1688Dp10AuthorizationUnavailableException missing) {
            return failurePolicy.auth(task, Ali1688Dp10Job.DETAIL_STEP, encode(checkpoint));
        } catch (RuntimeException failure) {
            return retry(task, checkpoint, "DP10_SCOPE_READ_UNKNOWN");
        }
        AdvanceResult refresh = refreshAuthorization(task, checkpoint, authorization);
        if (refresh != null) return refresh;
        Ali1688HistoricalOrderProvider.DetailResult result;
        try {
            result = provider.fetchOrderDetail(authorization, stagedOrder.getProviderOrderNo());
        } catch (RuntimeException failure) {
            return retry(task, checkpoint, "DP10_DETAIL_PROVIDER_UNKNOWN");
        }
        if (result == null) return retry(task, checkpoint, "DP10_DETAIL_EMPTY_RESULT");
        if (result.getStatus() == Ali1688HistoricalOrderProvider.DetailStatus.FAILURE) {
            return failurePolicy.detailFailure(
                    task, Ali1688Dp10Job.DETAIL_STEP, encode(checkpoint), result);
        }
        try {
            Ali1688Dp10DetailDecision decision = result.getStatus()
                    == Ali1688HistoricalOrderProvider.DetailStatus.NOT_FOUND
                    ? new Ali1688Dp10DetailDecision(
                            Ali1688Dp10ItemState.SKIP_NOT_FOUND,
                            null,
                            "DP10_DETAIL_NOT_FOUND")
                    : orderValidator.validateDetail(stagedOrder.getOrder(), result.getOrder());
            stageStore.recordDetail(task, pending, decision, nowUtc);
            return routeNext(task, nowUtc, checkpoint);
        } catch (Ali1688Dp10PageContractException contractFailure) {
            return retry(task, checkpoint, contractFailure.getSanitizedCode());
        } catch (RuntimeException failure) {
            return retry(task, checkpoint, "DP10_DETAIL_STAGE_UNKNOWN");
        }
    }

    private AdvanceResult routeNext(
            DataPullTask task,
            LocalDateTime nowUtc,
            Ali1688Dp10Checkpoint checkpoint
    ) {
        Optional<Ali1688Dp10PendingItem> next = stageStore.nextPendingDetail(
                task, checkpoint.getGenerationNo(), nowUtc);
        return next.isPresent()
                ? queued(Ali1688Dp10Job.DETAIL_STEP, checkpoint.atDetail(next.get()))
                : queued(Ali1688Dp10Job.VERIFY_STEP, checkpoint.atDetail(null));
    }

    private boolean same(Ali1688Dp10Checkpoint checkpoint, Ali1688Dp10PendingItem item) {
        return checkpoint.getGenerationNo() == item.getGenerationNo()
                && item.getScanPass() == 2
                && checkpoint.getDetailPartition() == item.getPartition()
                && java.util.Objects.equals(checkpoint.getDetailPageNo(), item.getPageNo())
                && java.util.Objects.equals(
                        checkpoint.getDetailItemOrdinal(), item.getItemOrdinal());
    }

    private AdvanceResult refreshAuthorization(
            DataPullTask task,
            Ali1688Dp10Checkpoint checkpoint,
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        try {
            if (!provider.requiresAuthorizationRefresh(authorization)) return null;
            AdvanceResult held = failurePolicy.holdUnknownRefresh(
                    task, Ali1688Dp10Job.DETAIL_STEP, encode(checkpoint));
            if (held != null) return held;
            Ali1688HistoricalOrderAuthorizationRefreshResult result =
                    provider.refreshAuthorization(authorization);
            if (result == null) return retry(task, checkpoint, "DP10_AUTH_REFRESH_EMPTY_RESULT");
            return result.isSuccess()
                    ? queued(Ali1688Dp10Job.DETAIL_STEP, checkpoint)
                    : failurePolicy.refreshFailure(
                            task, Ali1688Dp10Job.DETAIL_STEP, encode(checkpoint), result);
        } catch (RuntimeException failure) {
            return retry(task, checkpoint, "DP10_AUTH_REFRESH_UNKNOWN");
        }
    }

    private AdvanceResult retry(DataPullTask task, Ali1688Dp10Checkpoint checkpoint, String code) {
        return failurePolicy.transientFailure(
                task, Ali1688Dp10Job.DETAIL_STEP, encode(checkpoint), code);
    }

    private AdvanceResult queued(String step, Ali1688Dp10Checkpoint checkpoint) {
        return AdvanceResult.queued(step, null, encode(checkpoint));
    }

    private String encode(Ali1688Dp10Checkpoint checkpoint) {
        return checkpointCodec.encode(checkpoint);
    }
}
