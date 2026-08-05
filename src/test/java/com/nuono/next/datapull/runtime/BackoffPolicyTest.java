package com.nuono.next.datapull.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BackoffPolicyTest {

    private final BackoffKey key = new BackoffKey(
            "noon-consumer-front",
            "PRJ108065",
            OperationCode.DP05,
            "STR108065-NSA:SA"
    );

    @Test
    void retryAfterTakesPriorityOverConfiguredCeiling() {
        BackoffPolicy policy = new BackoffPolicy(Duration.ofSeconds(10), Duration.ofMinutes(5), 0.20d);
        ProviderOutcome<Object> outcome = ProviderOutcome.riskControl(
                "HTTP_429",
                Duration.ofMinutes(45),
                RiskShareLevel.EXACT
        );

        assertEquals(Duration.ofMinutes(45), policy.delayFor(outcome, key, 1));
    }

    @Test
    void fallbackIsExponentialJitteredAndBounded() {
        BackoffPolicy policy = new BackoffPolicy(Duration.ofSeconds(10), Duration.ofMinutes(5), 0.25d);
        ProviderOutcome<Object> outcome = ProviderOutcome.transientFailure("TIMEOUT");

        Duration first = policy.delayFor(outcome, key, 1);
        Duration second = policy.delayFor(outcome, key, 2);
        Duration saturated = policy.delayFor(outcome, key, 64);

        assertTrue(first.compareTo(Duration.ofSeconds(10)) >= 0);
        assertTrue(second.compareTo(first) > 0);
        assertTrue(second.compareTo(Duration.ofMinutes(5)) <= 0);
        assertEquals(Duration.ofMinutes(5), saturated);
    }

    @Test
    void jitterIsDeterministicForTheSameIsolationKeyAndAttempt() {
        BackoffPolicy policy = new BackoffPolicy(Duration.ofSeconds(100), Duration.ofHours(1), 0.50d);
        ProviderOutcome<Object> outcome = ProviderOutcome.riskControl("CAPTCHA");

        Duration firstCalculation = policy.delayFor(outcome, key, 3);
        Duration secondCalculation = policy.delayFor(outcome, key, 3);

        assertEquals(firstCalculation, secondCalculation);
    }

    @Test
    void exactIsTheDefaultShareLevelAndNonRetryableOutcomesAreRejected() {
        BackoffPolicy policy = new BackoffPolicy(Duration.ofSeconds(10), Duration.ofMinutes(5), 0.0d);

        assertEquals(RiskShareLevel.EXACT, policy.getDefaultShareLevel());
        assertEquals(
                RiskShareLevel.EXACT,
                policy.shareLevelFor(ProviderOutcome.transientFailure("RESET"), key)
        );
        assertEquals(
                Duration.ofSeconds(10),
                policy.delayFor(ProviderOutcome.contractError("BAD_CONTAINER"), key, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.delayFor(ProviderOutcome.notFound("NOT_FOUND"), key, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.delayFor(ProviderOutcome.transientFailure("RESET"), key, 0)
        );
    }

    @Test
    void exitSharingRequiresAVerifiedStableEgressIdentity() {
        BackoffPolicy policy = new BackoffPolicy(Duration.ofSeconds(10), Duration.ofMinutes(5), 0.0d);
        ProviderOutcome<Object> exitRisk = ProviderOutcome.riskControl(
                "EXIT_RATE_LIMIT",
                null,
                RiskShareLevel.EXIT
        );
        BackoffKey verifiedExit = new BackoffKey(
                "noon-consumer-front",
                "PRJ108065",
                OperationCode.DP05,
                "STR108065-NSA:SA",
                "egress-cn-1"
        );

        assertThrows(IllegalStateException.class, () -> policy.shareLevelFor(exitRisk, key));
        assertEquals(RiskShareLevel.EXIT, policy.shareLevelFor(exitRisk, verifiedExit));
    }
}
