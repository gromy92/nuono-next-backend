package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderFailureCodeTest {

    @Test
    void stableCodesCoverAuthorizationProviderAndDataQualityFailures() {
        assertThat(Ali1688HistoricalOrderFailureCode.values())
                .extracting(Ali1688HistoricalOrderFailureCode::getCode)
                .containsExactlyInAnyOrder(
                        "auth_required",
                        "provider_not_configured",
                        "provider_unavailable",
                        "rate_limited",
                        "blocked_by_risk_control",
                        "missing_fields",
                        "partial_success",
                        "unexpected_response"
                );
    }

    @Test
    void retryableAndManualActionFlagsAreStable() {
        assertThat(Ali1688HistoricalOrderFailureCode.fromCode("auth_required").isRequiresManualAction())
                .isTrue();
        assertThat(Ali1688HistoricalOrderFailureCode.fromCode("provider_not_configured").isRequiresManualAction())
                .isTrue();
        assertThat(Ali1688HistoricalOrderFailureCode.fromCode("provider_unavailable").isRetryable())
                .isTrue();
        assertThat(Ali1688HistoricalOrderFailureCode.fromCode("rate_limited").isRetryable())
                .isTrue();
        assertThat(Ali1688HistoricalOrderFailureCode.fromCode("blocked_by_risk_control").isRequiresManualAction())
                .isTrue();
        assertThat(Ali1688HistoricalOrderFailureCode.fromCode("missing_fields").isRetryable())
                .isTrue();
        assertThat(Ali1688HistoricalOrderFailureCode.fromCode("partial_success").isRetryable())
                .isTrue();
    }

    @Test
    void unknownProviderFailureFallsBackToUnexpectedResponse() {
        Ali1688HistoricalOrderFailureCode failureCode =
                Ali1688HistoricalOrderFailureCode.fromCode("provider_returned_new_unmapped_code");

        assertThat(failureCode).isEqualTo(Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE);
        assertThat(failureCode.isRetryable()).isTrue();
        assertThat(failureCode.isRequiresManualAction()).isFalse();
    }
}
