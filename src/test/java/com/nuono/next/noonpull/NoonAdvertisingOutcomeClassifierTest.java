package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noon.NoonHttpException;
import org.junit.jupiter.api.Test;

class NoonAdvertisingOutcomeClassifierTest {

    private final NoonAdvertisingOutcomeClassifier classifier =
            new NoonAdvertisingOutcomeClassifier();

    @Test
    void everyHttp403IsRiskControlEvenWhenBodyLooksLikeAuthentication() {
        ProviderOutcome<Object> outcome = classifier.classify(
                new NoonHttpException(403, "unauthorized project session", "/metrics"),
                "ADS_DASHBOARD_READ_FAILED"
        );

        assertEquals(ProviderOutcomeType.RISK_CONTROL, outcome.getType());
        assertEquals("ADS_RISK_CONTROL", outcome.getSanitizedCode());
    }

    @Test
    void http401RemainsAuthenticationRequired() {
        ProviderOutcome<Object> outcome = classifier.classify(
                new NoonHttpException(401, "invalid session", "/metrics"),
                "ADS_DASHBOARD_READ_FAILED"
        );

        assertEquals(ProviderOutcomeType.AUTH_REQUIRED, outcome.getType());
        assertEquals("ADS_AUTH_REQUIRED", outcome.getSanitizedCode());
    }
}
