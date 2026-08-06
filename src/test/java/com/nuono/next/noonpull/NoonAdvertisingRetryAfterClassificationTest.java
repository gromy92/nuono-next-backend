package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonRequestPacingException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NoonAdvertisingRetryAfterClassificationTest {

    private final NoonAdvertisingOutcomeClassifier classifier =
            new NoonAdvertisingOutcomeClassifier();

    @Test
    void carriesHttpRetryAfterForRiskAndTransientFailures() {
        ProviderOutcome<Object> risk = classifier.classify(
                new NoonHttpException(
                        403,
                        "forbidden",
                        "/ads",
                        Duration.ofSeconds(37)
                ),
                "ADS_READ_FAILED"
        );
        ProviderOutcome<Object> transientFailure = classifier.classify(
                new NoonHttpException(
                        503,
                        "unavailable",
                        "/ads",
                        Duration.ofSeconds(43)
                ),
                "ADS_READ_FAILED"
        );

        assertEquals(ProviderOutcomeType.RISK_CONTROL, risk.getType());
        assertEquals(Duration.ofSeconds(37), risk.getRetryAfter());
        assertEquals(ProviderOutcomeType.TRANSIENT, transientFailure.getType());
        assertEquals(Duration.ofSeconds(43), transientFailure.getRetryAfter());
    }

    @Test
    void carriesLocalPacingHint() {
        ProviderOutcome<Object> outcome = classifier.classify(
                new NoonRequestPacingException(Duration.ofMillis(525)),
                "ADS_READ_FAILED"
        );

        assertEquals(ProviderOutcomeType.TRANSIENT, outcome.getType());
        assertEquals("ADS_LOCAL_PACING", outcome.getSanitizedCode());
        assertEquals(Duration.ofMillis(525), outcome.getRetryAfter());
    }
}
