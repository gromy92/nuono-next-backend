package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import java.util.List;

final class CompetitorProductDetailBatchRunner {
    private static final String UNAVAILABLE = "DETAIL_ADAPTER_UNAVAILABLE";
    private static final String UNAVAILABLE_MESSAGE =
            "竞品列表补拉适配器或快照服务不可用。";

    private final boolean adapterAvailable;
    private final boolean snapshotServiceAvailable;
    private final CompetitorProductListingIO listingIO;

    CompetitorProductDetailBatchRunner(
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            CompetitorProductDetailWriteGuard writeGuard,
            CompetitorListingObservationService observationService,
            CompetitorProductDetailSupport detailSupport
    ) {
        this.adapterAvailable = detailAdapter != null;
        this.snapshotServiceAvailable = snapshotService != null;
        this.listingIO = new CompetitorProductListingIO(
                detailAdapter,
                writeGuard,
                observationService,
                detailSupport
        );
    }

    CompetitorProductDetailRefreshResult refresh(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailPlanEntry> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId,
            CompetitorDetailRetrySession retrySession,
            Runnable beforeFirstRequest
    ) {
        if (!adapterAvailable || !snapshotServiceAvailable) {
            return unavailable(targets, retrySession);
        }
        if (beforeFirstRequest != null && targets != null && !targets.isEmpty()) {
            beforeFirstRequest.run();
        }
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        for (int index = 0; index < targets.size(); index++) {
            CompetitorProductDetailPlanEntry context = targets.get(index);
            CompetitorProductDetailTarget target = context.target;
            result.recordTarget();
            if (context.recordTerminalFailure(result)) {
                checkpointFailure(
                        retrySession,
                        target,
                        context.terminalErrorCode,
                        context.terminalErrorMessage,
                        false
                );
                continue;
            }
            if (context.recordCovered(result)) {
                if (retrySession != null) {
                    retrySession.completeWithoutRequest(target);
                }
                continue;
            }
            if (context.recordDeferred(result)) {
                checkpointDeferred(
                        retrySession,
                        target,
                        context.deferredErrorCode,
                        context.deferredErrorMessage
                );
                continue;
            }
            NoonProductDetail detail = listingIO.fetch(
                    watchProduct,
                    context,
                    taskId,
                    actorUserId,
                    result,
                    retrySession
            );
            if (detail == null) {
                if (result.hasRiskBackoffFailure()) {
                    deferRemaining(targets, index, result, retrySession);
                    break;
                }
                continue;
            }
            listingIO.write(
                    watchProduct,
                    context,
                    detail,
                    searchRunId,
                    taskId,
                    actorUserId,
                    result,
                    retrySession
            );
            if (result.hasRiskBackoffFailure()) {
                deferRemaining(targets, index, result, retrySession);
                break;
            }
        }
        return result;
    }

    private CompetitorProductDetailRefreshResult unavailable(
            List<CompetitorProductDetailPlanEntry> targets,
            CompetitorDetailRetrySession retrySession
    ) {
        if (targets == null || targets.isEmpty()) {
            return CompetitorProductDetailRefreshResult.unavailable(
                    UNAVAILABLE, UNAVAILABLE_MESSAGE
            );
        }
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        for (CompetitorProductDetailPlanEntry entry : targets) {
            result.recordTarget();
            if (entry.recordTerminalFailure(result)) {
                checkpointFailure(
                        retrySession,
                        entry.target,
                        entry.terminalErrorCode,
                        entry.terminalErrorMessage,
                        false
                );
            } else {
                result.recordFailure(entry.target, UNAVAILABLE, UNAVAILABLE_MESSAGE);
                checkpointFailure(
                        retrySession,
                        entry.target,
                        UNAVAILABLE,
                        UNAVAILABLE_MESSAGE,
                        false
                );
            }
        }
        return result;
    }

    private void deferRemaining(
            List<CompetitorProductDetailPlanEntry> targets,
            int failedIndex,
            CompetitorProductDetailRefreshResult result,
            CompetitorDetailRetrySession retrySession
    ) {
        for (int index = failedIndex + 1; index < targets.size(); index++) {
            CompetitorProductDetailPlanEntry entry = targets.get(index);
            result.recordTarget();
            if (entry.recordTerminalFailure(result)) {
                checkpointFailure(
                        retrySession,
                        entry.target,
                        entry.terminalErrorCode,
                        entry.terminalErrorMessage,
                        false
                );
            } else {
                result.recordDeferred(
                        entry.target,
                        result.getRiskErrorCode(),
                        result.getRiskErrorMessage()
                );
            }
        }
    }

    private void checkpointFailure(
            CompetitorDetailRetrySession retrySession,
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage,
            boolean requested
    ) {
        if (retrySession != null) {
            retrySession.recordFailure(
                    target, errorCode, errorMessage, requested
            );
        }
    }

    private void checkpointDeferred(
            CompetitorDetailRetrySession retrySession,
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage
    ) {
        if (retrySession != null) {
            retrySession.recordDeferred(
                    target,
                    errorCode,
                    errorMessage
            );
        }
    }

}
