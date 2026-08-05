package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonRequestPacingException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NoonReportRetryAfterClassificationTest {

    @Test
    void carriesHttpHintsOnlyIntoRetryableReportOutcomes() {
        ProviderOutcome<Object> risk = NoonReportOutcomeClassifier.readFailure(
                wrapped(new NoonHttpException(
                        429,
                        "too many requests",
                        "/report",
                        Duration.ofSeconds(31)
                ))
        );
        ProviderOutcome<Object> transientFailure = NoonReportOutcomeClassifier.readFailure(
                wrapped(new NoonHttpException(
                        503,
                        "unavailable",
                        "/report",
                        Duration.ofSeconds(47)
                ))
        );

        assertEquals(ProviderOutcomeType.RISK_CONTROL, risk.getType());
        assertEquals(Duration.ofSeconds(31), risk.getRetryAfter());
        assertEquals(ProviderOutcomeType.TRANSIENT, transientFailure.getType());
        assertEquals(Duration.ofSeconds(47), transientFailure.getRetryAfter());
    }

    @Test
    void preRequestPacingRemainsRetryableEvenForCreate() {
        ProviderOutcome<Object> outcome = NoonReportOutcomeClassifier.createFailure(
                wrapped(new NoonRequestPacingException(Duration.ofMillis(850)))
        );

        assertEquals(ProviderOutcomeType.TRANSIENT, outcome.getType());
        assertEquals("REPORT_PROVIDER_LOCAL_PACING", outcome.getSanitizedCode());
        assertEquals(Duration.ofMillis(850), outcome.getRetryAfter());
    }

    private RuntimeException wrapped(RuntimeException cause) {
        return new IllegalStateException("sanitized wrapper", cause);
    }
}
