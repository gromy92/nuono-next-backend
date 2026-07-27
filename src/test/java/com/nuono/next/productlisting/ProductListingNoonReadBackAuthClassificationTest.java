package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductListingNoonReadBackAuthClassificationTest {

    @Test
    void bareRedirectForbiddenAndPermanent401RemainReadBackFailures() {
        for (RuntimeException failure : List.of(
                wrapped(new NoonHttpException(307, "", "/retrieve")),
                wrapped(new NoonHttpException(403, "auth_required", "/retrieve")),
                wrapped(new NoonHttpException(
                        401, "invalid username or password", "/retrieve"))
        )) {
            ProductListingNoonWriteStepResult step = verify(failure);

            assertEquals("failed", step.getStatus());
            assertEquals("noon_listing_readback_failed", step.getFailureCode());
        }
    }

    @Test
    void typedAuthenticationSignalRemainsAuthenticationThroughCauseChain() {
        ProductListingNoonWriteStepResult step = verify(wrapped(
                new NoonAuthenticationRequiredException("authorization required")
        ));

        assertEquals("failed", step.getStatus());
        assertEquals("noon_auth_required", step.getFailureCode());
    }

    private ProductListingNoonWriteStepResult verify(RuntimeException failure) {
        ProductListingRealWriteProperties properties =
                new ProductListingRealWriteProperties();
        properties.setReadBackMaxAttempts(1);
        properties.setReadBackRetryDelayMillis(0L);
        ProductListingNoonReadBackVerifier verifier =
                new ProductListingNoonReadBackVerifier(
                        new ObjectMapper(), properties);
        ProductListingDraftCommand draft =
                ProductListingTestFixtures.validCommand();
        return verifier.verify(
                new ThrowingSession(failure),
                properties.getEndpoints(),
                draft,
                List.of(),
                "ZPARENT",
                "PSKU_CODE_1",
                new NoonPullStoreBinding(
                        10002L,
                        "PRJ240053",
                        "STR245027-NAE",
                        "AE",
                        "240053",
                        "merchant@example.test",
                        "secret",
                        null,
                        "sid=test"
                ),
                Map.of()
        );
    }

    private RuntimeException wrapped(RuntimeException cause) {
        return new IllegalStateException("read-back failed", cause);
    }

    private static final class ThrowingSession
            implements NoonPullGatewaySession {
        private final RuntimeException failure;

        private ThrowingSession(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public JsonNode postJson(
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> headers
        ) {
            throw failure;
        }

        @Override
        public byte[] getBytes(
                String url,
                boolean withProject,
                Map<String, String> headers
        ) {
            throw new UnsupportedOperationException("byte read not expected");
        }
    }
}
