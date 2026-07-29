package com.nuono.next.competitoranalysis;

import org.springframework.util.StringUtils;

final class CompetitorProductDetailPlanEntry {
    final CompetitorProductDetailTarget target;
    final CompetitorProductRow product;
    final String terminalErrorCode;
    final String terminalErrorMessage;
    final String deferredErrorCode;
    final String deferredErrorMessage;
    private final boolean coveredByRank;

    CompetitorProductDetailPlanEntry(
            CompetitorProductDetailTarget target,
            CompetitorProductRow product
    ) {
        this(target, product, null, null, null, null, false);
    }

    private CompetitorProductDetailPlanEntry(
            CompetitorProductDetailTarget target,
            CompetitorProductRow product,
            String terminalErrorCode,
            String terminalErrorMessage,
            String deferredErrorCode,
            String deferredErrorMessage,
            boolean coveredByRank
    ) {
        this.target = target;
        this.product = product;
        this.terminalErrorCode = terminalErrorCode;
        this.terminalErrorMessage = terminalErrorMessage;
        this.deferredErrorCode = deferredErrorCode;
        this.deferredErrorMessage = deferredErrorMessage;
        this.coveredByRank = coveredByRank;
    }

    static CompetitorProductDetailPlanEntry stale(
            CompetitorProductDetailTarget target,
            String message
    ) {
        return new CompetitorProductDetailPlanEntry(
                target,
                null,
                "DETAIL_TARGET_STALE",
                message,
                null,
                null,
                false
        );
    }

    static CompetitorProductDetailPlanEntry deferred(
            CompetitorProductDetailTarget target,
            CompetitorProductRow product,
            String errorCode,
            String message
    ) {
        return new CompetitorProductDetailPlanEntry(
                target,
                product,
                null,
                null,
                errorCode,
                message,
                false
        );
    }

    static CompetitorProductDetailPlanEntry covered(
            CompetitorProductDetailTarget target,
            CompetitorProductRow product
    ) {
        return new CompetitorProductDetailPlanEntry(
                target,
                product,
                null,
                null,
                null,
                null,
                true
        );
    }

    boolean isTerminalFailure() {
        return StringUtils.hasText(terminalErrorCode);
    }

    boolean recordTerminalFailure(
            CompetitorProductDetailRefreshResult result
    ) {
        if (!isTerminalFailure()) {
            return false;
        }
        result.recordFailure(
                target,
                terminalErrorCode,
                terminalErrorMessage
        );
        return true;
    }

    boolean isDeferred() {
        return StringUtils.hasText(deferredErrorCode);
    }

    boolean recordDeferred(
            CompetitorProductDetailRefreshResult result
    ) {
        if (!isDeferred()) {
            return false;
        }
        result.recordDeferred(
                target,
                deferredErrorCode,
                deferredErrorMessage
        );
        return true;
    }

    boolean recordCovered(
            CompetitorProductDetailRefreshResult result
    ) {
        if (!coveredByRank) {
            return false;
        }
        result.recordSuccess(target);
        return true;
    }
}
