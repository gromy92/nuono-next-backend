package com.nuono.next.noonpull.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonRequestPacingException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NoonSnapshotRetryAfterClassificationTest {

    @Test
    void carriesRiskAndTransientHttpHints() {
        ProviderOutcome<Object> risk = NoonSnapshotProviderFailureClassifier.classify(
                new NoonHttpException(
                        418,
                        "risk control",
                        "/snapshot",
                        Duration.ofSeconds(23)
                ),
                "DP04_PRODUCT"
        );
        ProviderOutcome<Object> transientFailure =
                NoonSnapshotProviderFailureClassifier.classify(
                        new NoonHttpException(
                                408,
                                "timeout",
                                "/snapshot",
                                Duration.ofSeconds(29)
                        ),
                        "DP07A_INVENTORY"
                );

        assertEquals(ProviderOutcomeType.RISK_CONTROL, risk.getType());
        assertEquals(Duration.ofSeconds(23), risk.getRetryAfter());
        assertEquals(ProviderOutcomeType.TRANSIENT, transientFailure.getType());
        assertEquals(Duration.ofSeconds(29), transientFailure.getRetryAfter());
    }

    @Test
    void carriesLocalPacingHintThroughWrappedProviderFailure() {
        ProviderOutcome<Object> outcome = NoonSnapshotProviderFailureClassifier.classify(
                new IllegalStateException(
                        "provider wrapper",
                        new NoonRequestPacingException(Duration.ofMillis(640))
                ),
                "DP07A_INVENTORY"
        );

        assertEquals(ProviderOutcomeType.TRANSIENT, outcome.getType());
        assertEquals("DP07A_INVENTORY_LOCAL_PACING", outcome.getSanitizedCode());
        assertEquals(Duration.ofMillis(640), outcome.getRetryAfter());
    }
}
