package com.nuono.next.datapull.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderOutcomeTest {

    @Test
    void exposesEveryProviderClassificationWithoutStringGuessing() {
        assertEquals(ProviderOutcomeType.SUCCESS, ProviderOutcome.success("payload").getType());
        assertEquals(ProviderOutcomeType.NOT_FOUND, ProviderOutcome.notFound("PRODUCT_NOT_FOUND").getType());
        assertEquals(ProviderOutcomeType.RISK_CONTROL, ProviderOutcome.riskControl("HTTP_429").getType());
        assertEquals(ProviderOutcomeType.TRANSIENT, ProviderOutcome.transientFailure("HTTP_503").getType());
        assertEquals(ProviderOutcomeType.AUTH_REQUIRED, ProviderOutcome.authRequired("COOKIE_EXPIRED").getType());
        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, ProviderOutcome.contractError("MISSING_COLUMN").getType());
        assertEquals(ProviderOutcomeType.UNKNOWN_OUTCOME, ProviderOutcome.unknownOutcome("CREATE_UNKNOWN").getType());
    }

    @Test
    void defaultsToExactAndWidensOnlyForVerifiedRiskControl() {
        ProviderOutcome<Object> transientOutcome = ProviderOutcome.transientFailure("TIMEOUT");
        ProviderOutcome<Object> accountRisk = ProviderOutcome.riskControl(
                "ACCOUNT_RATE_LIMIT",
                Duration.ofMinutes(10),
                RiskShareLevel.ACCOUNT
        );

        assertEquals(RiskShareLevel.EXACT, transientOutcome.getShareLevel());
        assertEquals(RiskShareLevel.ACCOUNT, accountRisk.getShareLevel());
        assertNull(transientOutcome.getValue());
    }

    @Test
    void rejectsUnsafeDiagnosticCodesAndNegativeRetryAfter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProviderOutcome.notFound("raw provider body contains spaces")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ProviderOutcome.riskControl(
                        "HTTP_429",
                        Duration.ofSeconds(-1),
                        RiskShareLevel.EXACT
                )
        );
    }
}
