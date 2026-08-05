package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRefreshResult;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderFailureCode;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10FailurePolicyTest extends Ali1688Dp10JobTestSupport {
    private final Ali1688Dp10FailurePolicy policy = new Ali1688Dp10FailurePolicy(
            new ProviderWaitTransition(new BackoffPolicy(
                    Duration.ofMinutes(1), Duration.ofHours(1), 0.0d
            ))
    );

    @Test
    void unauthorizedWaitsForAuthWithoutRiskSharing() {
        AdvanceResult result = failure("auth_required", null);

        assertThat(result.getNextState()).isEqualTo(TaskState.WAITING_AUTH);
        assertThat(result.getSanitizedCode()).isEqualTo("DP10_AUTH_REQUIRED");
        assertThat(result.getBackoffShareLevel()).isNull();
    }

    @Test
    void uncertainRefreshPostWaitsForManualAuthorizationWithoutBackoff() {
        Ali1688HistoricalOrderAuthorizationRefreshResult refresh =
                Ali1688HistoricalOrderAuthorizationRefreshResult.failure(
                        Ali1688HistoricalOrderFailureCode.AUTH_REFRESH_OUTCOME_UNKNOWN,
                        null
                );

        AdvanceResult result = policy.refreshFailure(
                task(authorization()),
                Ali1688Dp10Job.LIST_STEP,
                "checkpoint",
                refresh
        );

        assertThat(result.getNextState()).isEqualTo(TaskState.WAITING_AUTH);
        assertThat(result.getSanitizedCode())
                .isEqualTo("DP10_AUTH_REFRESH_OUTCOME_UNKNOWN");
        assertThat(result.getBackoffShareLevel()).isNull();
    }

    @Test
    void forbiddenAndRateLimitedUseExactRiskBackoff() {
        AdvanceResult forbidden = failure("blocked_by_risk_control", Duration.ofMinutes(3));
        AdvanceResult limited = failure("rate_limited", Duration.ofMinutes(4));

        assertRisk(forbidden, "DP10_BLOCKED_BY_RISK_CONTROL", Duration.ofMinutes(3));
        assertRisk(limited, "DP10_RATE_LIMITED", Duration.ofMinutes(4));
    }

    @Test
    void unknownProviderCodeRetriesAsSanitizedUnexpectedResponse() {
        AdvanceResult result = failure("new_unmapped_provider_code", null);

        assertThat(result.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(result.getSanitizedCode()).isEqualTo("DP10_UNEXPECTED_RESPONSE");
        assertThat(result.getRetryAfter()).isEqualTo(Duration.ofMinutes(1));
        assertThat(result.getBackoffShareLevel()).isEqualTo(RiskShareLevel.EXACT);
    }

    private AdvanceResult failure(String code, Duration retryAfter) {
        Ali1688HistoricalOrderProvider.Page page =
                new Ali1688HistoricalOrderProvider.Page(List.of());
        page.setFailureCode(code);
        page.setRetryAfter(retryAfter);
        return policy.pageFailure(
                task(authorization()),
                Ali1688Dp10Job.LIST_STEP,
                "checkpoint",
                page
        );
    }

    private void assertRisk(AdvanceResult result, String code, Duration delay) {
        assertThat(result.getNextState()).isEqualTo(TaskState.WAITING_BACKOFF);
        assertThat(result.getSanitizedCode()).isEqualTo(code);
        assertThat(result.getRetryAfter()).isEqualTo(delay);
        assertThat(result.getBackoffShareLevel()).isEqualTo(RiskShareLevel.EXACT);
    }
}
