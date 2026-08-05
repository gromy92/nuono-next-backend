package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderFailureCode;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRefreshResult;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.Duration;
import java.util.Objects;

/** Maps only sanitized provider outcomes to runtime waits; configuration errors fail fast. */
final class Ali1688Dp10FailurePolicy {
    private static final String REFRESH_OUTCOME_UNKNOWN =
            "DP10_AUTH_REFRESH_OUTCOME_UNKNOWN";
    private final ProviderWaitTransition providerWaitTransition;

    Ali1688Dp10FailurePolicy(ProviderWaitTransition providerWaitTransition) {
        this.providerWaitTransition = Objects.requireNonNull(
                providerWaitTransition,
                "providerWaitTransition"
        );
    }

    AdvanceResult pageFailure(
            DataPullTask task,
            String step,
            String checkpoint,
            Ali1688HistoricalOrderProvider.Page page
    ) {
        return failure(task, step, checkpoint, page.getFailureCode(), page.getRetryAfter());
    }

    AdvanceResult detailFailure(
            DataPullTask task,
            String step,
            String checkpoint,
            Ali1688HistoricalOrderProvider.DetailResult detail
    ) {
        return failure(task, step, checkpoint, detail.getFailureCode(), detail.getRetryAfter());
    }

    AdvanceResult refreshFailure(
            DataPullTask task,
            String step,
            String checkpoint,
            Ali1688HistoricalOrderAuthorizationRefreshResult refresh
    ) {
        return failure(
                task,
                step,
                checkpoint,
                refresh.getFailureCode(),
                refresh.getRetryAfter()
        );
    }

    AdvanceResult auth(DataPullTask task, String step, String checkpoint) {
        return waitingAuth(task, step, checkpoint, "DP10_AUTH_REQUIRED");
    }

    private AdvanceResult waitingAuth(
            DataPullTask task,
            String step,
            String checkpoint,
            String sanitizedCode
    ) {
        return providerWaitTransition.waitFor(
                task,
                OperationCode.DP10,
                ProviderOutcome.authRequired(sanitizedCode),
                attempt(task),
                step,
                null,
                checkpoint, null
        );
    }

    AdvanceResult holdUnknownRefresh(DataPullTask task, String step, String checkpoint) {
        return REFRESH_OUTCOME_UNKNOWN.equals(task.getSanitizedFailureCode())
                ? waitingAuth(task, step, checkpoint, REFRESH_OUTCOME_UNKNOWN)
                : null;
    }

    AdvanceResult transientFailure(
            DataPullTask task,
            String step,
            String checkpoint,
            String sanitizedCode
    ) {
        return backoff(
                task,
                step,
                checkpoint,
                ProviderOutcome.transientFailure(sanitizedCode)
        );
    }

    private AdvanceResult failure(
            DataPullTask task,
            String step,
            String checkpoint,
            String rawCode,
            Duration retryAfter
    ) {
        Ali1688HistoricalOrderFailureCode code = Ali1688HistoricalOrderFailureCode.fromCode(rawCode);
        String sanitized = "DP10_" + code.name();
        if (code == Ali1688HistoricalOrderFailureCode.PROVIDER_NOT_CONFIGURED) {
            return AdvanceResult.failed(step, null, checkpoint, sanitized);
        }
        if (code == Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED
                || code == Ali1688HistoricalOrderFailureCode.AUTH_REFRESH_OUTCOME_UNKNOWN) {
            return waitingAuth(task, step, checkpoint, sanitized);
        }
        if (code == Ali1688HistoricalOrderFailureCode.RATE_LIMITED
                || code == Ali1688HistoricalOrderFailureCode.BLOCKED_BY_RISK_CONTROL) {
            return backoff(
                    task,
                    step,
                    checkpoint,
                    ProviderOutcome.riskControl(sanitized, retryAfter, RiskShareLevel.EXACT)
            );
        }
        return backoff(
                task,
                step,
                checkpoint,
                ProviderOutcome.transientFailure(sanitized, retryAfter)
        );
    }

    private AdvanceResult backoff(
            DataPullTask task,
            String step,
            String checkpoint,
            ProviderOutcome<?> outcome
    ) {
        return providerWaitTransition.waitFor(
                task,
                OperationCode.DP10,
                outcome,
                attempt(task),
                step,
                null,
                checkpoint, null
        );
    }

    private int attempt(DataPullTask task) {
        return Math.max(1, (task.getAttempt() == null ? 0 : task.getAttempt()) + 1);
    }
}
