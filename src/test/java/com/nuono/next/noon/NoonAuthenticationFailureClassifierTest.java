package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoonAuthenticationFailureClassifierTest {

    @Test
    void structuredHttpAuthenticationAndRedirectFailuresAreRecognizedThroughCauseChain() {
        assertTrue(NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                new IllegalStateException(
                        "wrapped",
                        new NoonHttpException(401, "redacted", "/offer/list/noon")
                )
        ));
        assertTrue(NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                new NoonHttpException(307, "", "/offer/list/noon")
        ));
        assertTrue(NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                new IllegalStateException(
                        "wrapped",
                        new NoonAuthenticationRequiredException("Project authorization recovery is pending.")
                )
        ));
    }

    @Test
    void messageTextAloneNeverClassifiesAuthentication() {
        assertFalse(NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                new IllegalStateException("auth cookie session unauthorized 307")
        ));
        assertFalse(NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                new NoonHttpException(500, "unauthorized", "/offer/list/noon")
        ));
    }

    @Test
    void explicitAuthenticationRejectionRequiresARealHttpAuthenticationResponse() {
        assertTrue(NoonAuthenticationFailureClassifier
                .isExplicitAuthenticationRejection(
                        new IllegalStateException(
                                "wrapped",
                                new NoonHttpException(307, "", "/create")
                        )
                ));
        assertFalse(NoonAuthenticationFailureClassifier
                .isExplicitAuthenticationRejection(
                        new NoonAuthenticationRequiredException(
                                "authorization recovery pending"
                        )
                ));
        assertFalse(NoonAuthenticationFailureClassifier
                .isExplicitAuthenticationRejection(
                        new IllegalStateException(
                                "connection reset after request write"
                        )
                ));
    }
}
