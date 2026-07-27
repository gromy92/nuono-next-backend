package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoonAuthenticationFailureClassifierTest {

    @Test
    void typedAuthenticationAndExplicit401AreRecognizedThroughCauseChain() {
        assertTrue(NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                new IllegalStateException(
                        "wrapped",
                        new NoonHttpException(401, "redacted", "/offer/list/noon")
                )
        ));
        assertTrue(NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                new IllegalStateException(
                        "wrapped",
                        new NoonAuthenticationRequiredException("Project authorization recovery is pending.")
                )
        ));
        assertTrue(NoonAuthenticationFailureClassifier
                .isExplicitAuthenticationRejection(
                        new NoonHttpException(401, "", "/offer/list/noon")
                ));
    }

    @Test
    void bareRedirectAndForbiddenStatusesNeverProveAuthentication() {
        for (int status : new int[] {301, 302, 303, 307, 308, 403}) {
            NoonHttpException failure =
                    new NoonHttpException(status, "auth_required", "/create");
            assertFalse(NoonAuthenticationFailureClassifier
                    .isAuthenticationFailure(failure));
            assertFalse(NoonAuthenticationFailureClassifier
                    .isExplicitAuthenticationRejection(failure));
        }
    }

    @Test
    void permanent401CredentialAndProjectFailuresVetoAuthentication() {
        NoonHttpException invalidCredential = new NoonHttpException(
                401, "invalid username or password", "/catalog");
        IllegalStateException projectMismatch = new IllegalStateException(
                "current project mismatch",
                new NoonHttpException(401, "", "/catalog")
        );
        assertFalse(NoonAuthenticationFailureClassifier
                .isAuthenticationFailure(invalidCredential));
        assertFalse(NoonAuthenticationFailureClassifier
                .isExplicitAuthenticationRejection(invalidCredential));
        assertFalse(NoonAuthenticationFailureClassifier
                .isAuthenticationFailure(projectMismatch));
        assertFalse(NoonAuthenticationFailureClassifier
                .isExplicitAuthenticationRejection(projectMismatch));
    }

    @Test
    void typedSignalsRemainDistinctFromExplicitHttpRejectionAndMessageText() {
        assertFalse(NoonAuthenticationFailureClassifier
                .isExplicitAuthenticationRejection(
                        new NoonAuthenticationRequiredException(
                                "authorization recovery pending"
                        )
                ));
        assertFalse(NoonAuthenticationFailureClassifier
                .isAuthenticationFailure(
                        new IllegalStateException(
                                "auth cookie session unauthorized 307")));
        assertFalse(NoonAuthenticationFailureClassifier
                .isAuthenticationFailure(
                        new NoonHttpException(
                                500, "unauthorized", "/offer/list/noon")));
    }
}
