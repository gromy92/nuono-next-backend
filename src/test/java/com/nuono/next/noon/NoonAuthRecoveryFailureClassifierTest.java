package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryFailureClassifierTest {

    @Test
    void http403IsIdentityFailureAndMustNotBeReportedAsRiskControl() {
        assertEquals(
                NoonAuthRecoveryFailureCode.IDENTITY_AUTH_FAILED,
                NoonAuthRecoveryFailureClassifier.classifySendFailure(
                        new NoonHttpException(403, "forbidden", "{}")
                )
        );
    }

    @Test
    void http418And429RemainSeparateProviderSignals() {
        assertEquals(
                NoonAuthRecoveryFailureCode.SEND_RISK_BLOCKED,
                NoonAuthRecoveryFailureClassifier.classifySendFailure(
                        new NoonHttpException(418, "teapot", "{}")
                )
        );
        assertEquals(
                NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED,
                NoonAuthRecoveryFailureClassifier.classifySendFailure(
                        new NoonHttpException(429, "rate limited", "{}")
                )
        );
    }
}
