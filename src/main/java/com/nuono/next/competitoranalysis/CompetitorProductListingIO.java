package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import org.springframework.util.StringUtils;

final class CompetitorProductListingIO {
    private final NoonProductDetailAdapter adapter;
    private final CompetitorProductDetailWriteGuard writeGuard;
    private final CompetitorListingObservationService observationService;
    private final CompetitorProductDetailSupport detailSupport;

    CompetitorProductListingIO(
            NoonProductDetailAdapter adapter,
            CompetitorProductDetailWriteGuard writeGuard,
            CompetitorListingObservationService observationService,
            CompetitorProductDetailSupport detailSupport
    ) {
        this.adapter = adapter;
        this.writeGuard = writeGuard;
        this.observationService = observationService;
        this.detailSupport = detailSupport;
    }

    NoonProductDetail fetch(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductDetailPlanEntry context,
            Long taskId,
            Long actorUserId,
            CompetitorProductDetailRefreshResult result,
            CompetitorDetailRetrySession retrySession
    ) {
        CompetitorProductDetailTarget target = context.target;
        CompetitorProductRow product = context.product;
        String code = target.getNoonProductCode();
        CompetitorListingObservationService.Lease observationLease = null;
        boolean requested = false;
        try {
            if (observationService != null) {
                observationLease = observationService.acquireExact(
                        watchProduct,
                        code,
                        taskId,
                        actorUserId
                );
                if (observationLease.getCachedDetail() != null) {
                    NoonProductDetail cached =
                            observationLease.getCachedDetail();
                    cached.setReusedObservation(true);
                    return cached;
                }
                if (observationLease.getNotFound() != null) {
                    throw observationLease.getNotFound();
                }
            }
            if (retrySession != null) {
                retrySession.beginRequest(target);
            }
            requested = true;
            result.recordRequestAttempt();
            NoonProductDetail detail = adapter.fetch(
                    detailSupport.buildRequest(
                            watchProduct,
                            product,
                            code
                    )
            );
            if (detail == null) {
                throw new IllegalStateException(
                        "Noon 前台列表补拉未返回结果。"
                );
            }
            detailSupport.normalizeDetail(detail, code, product);
            if (observationService != null) {
                observationService.completeFound(
                        observationLease,
                        detail,
                        actorUserId
                );
            }
            return detail;
        } catch (CompetitorRefreshLeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (!requested && isObservationInProgress(exception)) {
                deferObservation(
                        result,
                        retrySession,
                        target,
                        (NoonSearchProviderException) exception
                );
                return null;
            }
            completeObservationFailure(
                    observationLease,
                    exception,
                    actorUserId
            );
            recordFailure(
                    result,
                    retrySession,
                    target,
                    exception,
                    requested
            );
            CompetitorProductListingLog.failure(
                    "fetch",
                    watchProduct,
                    target,
                    product,
                    taskId,
                    exception
            );
            return null;
        }
    }

    void write(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductDetailPlanEntry context,
            NoonProductDetail detail,
            Long searchRunId,
            Long taskId,
            Long actorUserId,
            CompetitorProductDetailRefreshResult result,
            CompetitorDetailRetrySession retrySession
    ) {
        CompetitorProductDetailTarget target = context.target;
        CompetitorProductRow product = context.product;
        boolean requested = !detail.isReusedObservation();
        String checkpointPayload = retrySession == null
                ? null
                : retrySession.payloadAfterSuccess(target, requested);
        try {
            writeGuard.write(
                    taskId,
                    searchRunId,
                    watchProduct,
                    product,
                    product == null
                            ? null
                            : detailSupport.buildProductUpdate(
                                    product,
                                    detail,
                                    actorUserId
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
                    requested
            );
            CompetitorProductListingLog.failure(
                    "stale",
                    watchProduct,
                    target,
                    product,
                    taskId,
                    exception
            );
        } catch (RuntimeException exception) {
            recordFailure(
                    result,
                    retrySession,
                    target,
                    exception,
                    requested
            );
            CompetitorProductListingLog.failure(
                    "write",
                    watchProduct,
                    target,
                    product,
                    taskId,
                    exception
            );
        }
    }

    private void deferObservation(
            CompetitorProductDetailRefreshResult result,
            CompetitorDetailRetrySession retrySession,
            CompetitorProductDetailTarget target,
            NoonSearchProviderException error
    ) {
        result.recordDeferred(
                target,
                error.getErrorCode(),
                error.getMessage()
        );
        if (retrySession != null) {
            retrySession.recordDeferred(
                    target,
                    error.getErrorCode(),
                    error.getMessage()
            );
        }
    }

    private boolean isObservationInProgress(RuntimeException error) {
        return error instanceof NoonSearchProviderException
                && "LIST_OBSERVATION_IN_PROGRESS".equalsIgnoreCase(
                        ((NoonSearchProviderException) error).getErrorCode()
                );
    }

    private void completeObservationFailure(
            CompetitorListingObservationService.Lease lease,
            RuntimeException error,
            Long actorUserId
    ) {
        if (observationService == null
                || lease == null
                || !lease.isAcquired()) {
            return;
        }
        if (error instanceof NoonSearchProviderException
                && "LIST_PRODUCT_NOT_FOUND".equalsIgnoreCase(
                        ((NoonSearchProviderException) error).getErrorCode()
                )) {
            observationService.completeNotFound(
                    lease,
                    (NoonSearchProviderException) error,
                    actorUserId
            );
        } else {
            observationService.completeRetryableFailure(
                    lease,
                    error,
                    actorUserId
            );
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
                : "竞品列表补拉失败。";
        result.recordFailure(target, errorCode, errorMessage);
        checkpointFailure(
                retrySession,
                target,
                errorCode,
                errorMessage,
                requested
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
                    target,
                    errorCode,
                    errorMessage,
                    requested
            );
        }
    }

}
