package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

final class CompetitorProductDetailBatchRunner {
    private static final Logger log =
            LoggerFactory.getLogger(CompetitorProductDetailBatchRunner.class);
    private static final String UNAVAILABLE = "DETAIL_ADAPTER_UNAVAILABLE";
    private static final String UNAVAILABLE_MESSAGE =
            "竞品详情适配器或快照服务不可用。";

    private final NoonProductDetailAdapter detailAdapter;
    private final boolean snapshotServiceAvailable;
    private final CompetitorProductDetailWriteGuard writeGuard;
    private final CompetitorProductDetailSupport detailSupport;

    CompetitorProductDetailBatchRunner(
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            CompetitorProductDetailWriteGuard writeGuard,
            CompetitorProductDetailSupport detailSupport
    ) {
        this.detailAdapter = detailAdapter;
        this.snapshotServiceAvailable = snapshotService != null;
        this.writeGuard = writeGuard;
        this.detailSupport = detailSupport;
    }

    CompetitorProductDetailRefreshResult refresh(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTargetPlan.Entry> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId,
            CompetitorDetailRetrySession retrySession
    ) {
        if (detailAdapter == null || !snapshotServiceAvailable) {
            return unavailable(targets, retrySession);
        }
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        for (int index = 0; index < targets.size(); index++) {
            CompetitorProductDetailTargetPlan.Entry context = targets.get(index);
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
            NoonProductDetail detail = fetch(
                    watchProduct, context, taskId, result, retrySession
            );
            if (detail == null) {
                if (result.hasRiskBackoffFailure()) {
                    deferRemaining(targets, index, result, retrySession);
                    break;
                }
                continue;
            }
            write(
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

    private NoonProductDetail fetch(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductDetailTargetPlan.Entry context,
            Long taskId,
            CompetitorProductDetailRefreshResult result,
            CompetitorDetailRetrySession retrySession
    ) {
        CompetitorProductDetailTarget target = context.target;
        CompetitorProductRow product = context.product;
        String code = target.getNoonProductCode();
        if (retrySession != null) {
            retrySession.beginRequest(target);
        }
        try {
            result.recordRequestAttempt();
            NoonProductDetail detail = detailAdapter.fetch(
                    detailSupport.buildRequest(watchProduct, product, code)
            );
            if (detail == null) {
                throw new IllegalStateException("Noon 前台商品详情未返回结果。");
            }
            detailSupport.normalizeDetail(detail, code, product);
            return detail;
        } catch (CompetitorRefreshLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(result, retrySession, target, exception, true);
            logFailure("fetch", watchProduct, target, product, taskId, exception);
            return null;
        }
    }

    private void write(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductDetailTargetPlan.Entry context,
            NoonProductDetail detail,
            Long searchRunId,
            Long taskId,
            Long actorUserId,
            CompetitorProductDetailRefreshResult result,
            CompetitorDetailRetrySession retrySession
    ) {
        CompetitorProductDetailTarget target = context.target;
        CompetitorProductRow product = context.product;
        String checkpointPayload = retrySession == null
                ? null
                : retrySession.payloadAfterSuccess(target);
        try {
            writeGuard.write(
                    taskId,
                    searchRunId,
                    watchProduct,
                    product,
                    product == null
                            ? null
                            : detailSupport.buildProductUpdate(
                                    product, detail, actorUserId
                            ),
                    detail,
                    actorUserId,
                    checkpointPayload
            );
            if (retrySession != null) {
                retrySession.successCommitted(checkpointPayload);
            }
            result.recordSuccess(target);
        } catch (CompetitorRefreshLeaseLostException exception) {
            throw exception;
        } catch (CompetitorDetailTargetStaleException exception) {
            result.recordFailure(
                    target,
                    CompetitorDetailTargetStaleException.ERROR_CODE,
                    exception.getMessage()
            );
            checkpointFailure(
                    retrySession,
                    target,
                    CompetitorDetailTargetStaleException.ERROR_CODE,
                    exception.getMessage(),
                    true
            );
            logFailure("stale", watchProduct, target, product, taskId, exception);
        } catch (RuntimeException exception) {
            recordFailure(result, retrySession, target, exception, true);
            logFailure("write", watchProduct, target, product, taskId, exception);
        }
    }

    private CompetitorProductDetailRefreshResult unavailable(
            List<CompetitorProductDetailTargetPlan.Entry> targets,
            CompetitorDetailRetrySession retrySession
    ) {
        if (targets == null || targets.isEmpty()) {
            return CompetitorProductDetailRefreshResult.unavailable(
                    UNAVAILABLE, UNAVAILABLE_MESSAGE
            );
        }
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        for (CompetitorProductDetailTargetPlan.Entry entry : targets) {
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
            List<CompetitorProductDetailTargetPlan.Entry> targets,
            int failedIndex,
            CompetitorProductDetailRefreshResult result,
            CompetitorDetailRetrySession retrySession
    ) {
        for (int index = failedIndex + 1; index < targets.size(); index++) {
            CompetitorProductDetailTargetPlan.Entry entry = targets.get(index);
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

    private void recordFailure(
            CompetitorProductDetailRefreshResult result,
            CompetitorDetailRetrySession retrySession,
            CompetitorProductDetailTarget target,
            RuntimeException exception,
            boolean requested
    ) {
        String errorCode = exception instanceof NoonSearchProviderException
                ? ((NoonSearchProviderException) exception).getErrorCode()
                : "DETAIL_REFRESH_FAILED";
        String errorMessage = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage().trim()
                : "竞品详情抓取失败。";
        result.recordFailure(target, errorCode, errorMessage);
        checkpointFailure(
                retrySession, target, errorCode, errorMessage, requested
        );
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

    private void logFailure(
            String phase,
            CompetitorWatchProductRow watchProduct,
            CompetitorProductDetailTarget target,
            CompetitorProductRow product,
            Long taskId,
            RuntimeException exception
    ) {
        log.warn(
                "competitor detail {} failed watchProductId={} subjectType={} competitorProductId={} noonProductCode={} taskId={} error={}",
                phase,
                watchProduct == null ? null : watchProduct.getId(),
                target == null ? null : target.getSubjectType(),
                product == null ? null : product.getId(),
                target == null ? null : target.getNoonProductCode(),
                taskId,
                exception.getMessage(),
                exception
        );
    }
}
