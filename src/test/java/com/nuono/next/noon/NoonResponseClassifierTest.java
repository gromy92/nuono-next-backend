package com.nuono.next.noon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoonResponseClassifierTest {

    private final NoonResponseClassifier classifier = new NoonResponseClassifier();

    @Test
    void recognizesCreateAsnLinePbarcodeMappingFailureAndExtractsPsku() {
        NoonHttpException response = new NoonHttpException(
                400,
                "{\"error\":\"psku_codes ['afbb2138c32bb212a0eab446b986b2aa'] invalid or not mapped to pbarcode\"}",
                "/asn/lines/create-batch"
        );

        NoonResponseClassification result = classifier.classify("create_asn_lines", response);

        assertThat(result.getCode()).isEqualTo("NOON_PBARCODE_UNMAPPED");
        assertThat(result.getCategory()).isEqualTo(NoonFailureCategory.BUSINESS_VALIDATION);
        assertThat(result.getApiStatus()).isEqualTo(422);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getAffectedPskuCodes())
                .containsExactly("afbb2138c32bb212a0eab446b986b2aa");
        assertThat(result.getUserMessage())
                .doesNotContain("invalid or not mapped")
                .doesNotContain("traceback");
    }

    @Test
    void leavesSameTextUnclassifiedForUnrelatedOperation() {
        NoonHttpException response = new NoonHttpException(
                400,
                "{\"error\":\"psku_codes ['afbb2138c32bb212a0eab446b986b2aa'] invalid or not mapped to pbarcode\"}",
                "/other"
        );

        NoonResponseClassification result = classifier.classify("SYNC_ASN_LIST", response);

        assertThat(result.getCode()).isEqualTo("NOON_UNCLASSIFIED_RESPONSE");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void recognizesRateLimitAndUpstreamFailuresWithoutReturningRawBody() {
        NoonResponseClassification rateLimit = classifier.classify(
                "SCHEDULE_APPOINTMENT",
                new NoonHttpException(429, "Too many requests | Error Reference: SECRET", "/schedule")
        );
        NoonResponseClassification upstream = classifier.classify(
                "QUERY_ASN_DETAIL",
                new NoonHttpException(503, "<html>provider traceback SECRET</html>", "/details")
        );

        assertThat(rateLimit.getCode()).isEqualTo("NOON_RATE_LIMITED");
        assertThat(rateLimit.getApiStatus()).isEqualTo(429);
        assertThat(rateLimit.getUserMessage()).doesNotContain("SECRET");
        assertThat(upstream.getCode()).isEqualTo("NOON_UPSTREAM_UNAVAILABLE");
        assertThat(upstream.getApiStatus()).isEqualTo(502);
        assertThat(upstream.getUserMessage()).doesNotContain("traceback");
    }

    @Test
    void transportExceptionMessageDoesNotExposeProviderBody() {
        NoonHttpException exception = new NoonHttpException(
                400,
                "private-provider-response",
                "/asn/lines/create-batch"
        );

        assertThat(exception.getMessage())
                .contains("Noon HTTP 400")
                .doesNotContain("private-provider-response");
        assertThat(exception.getResponseBody()).isEqualTo("private-provider-response");
    }
}
