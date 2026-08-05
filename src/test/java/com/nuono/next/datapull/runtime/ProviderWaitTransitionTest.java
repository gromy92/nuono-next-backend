package com.nuono.next.datapull.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderWaitTransitionTest {

    private final ProviderWaitTransition transition = new ProviderWaitTransition(
            new BackoffPolicy(Duration.ofMinutes(1), Duration.ofHours(1), 0.0d)
    );

    @Test
    void riskRetainsRetryAfterSharingAndProviderOverride() {
        AdvanceResult result = transition.waitFor(
                task(OperationCode.DP05),
                OperationCode.DP05,
                ProviderOutcome.riskControl(
                        "HTTP_429",
                        Duration.ofMinutes(7),
                        RiskShareLevel.EXIT
                ),
                3,
                "FRONTEND",
                "remote-1",
                "checkpoint-1",
                "NOON_PARTNER_CATALOG"
        );

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals(Duration.ofMinutes(7), result.getRetryAfter());
        assertEquals(RiskShareLevel.EXIT, result.getBackoffShareLevel());
        assertEquals("NOON_PARTNER_CATALOG", result.getBackoffProviderChannel());
        assertEquals("remote-1", result.getRemoteHandle());
        assertEquals("checkpoint-1", result.getCheckpoint());
    }

    @Test
    void providerOverrideMatchesItsPersistenceColumn() {
        String maximum = "p".repeat(64);

        assertEquals(maximum, AdvanceResult.waitingBackoffForProvider(
                maximum, "STEP", null, "checkpoint", Duration.ZERO, "HTTP_429",
                RiskShareLevel.EXACT
        ).getBackoffProviderChannel());
        assertThrows(IllegalArgumentException.class, () -> AdvanceResult.waitingBackoffForProvider(
                "p".repeat(65), "STEP", null, "checkpoint", Duration.ZERO, "HTTP_429",
                RiskShareLevel.EXACT
        ));
    }

    @Test
    void transientUsesPolicyAndExactIsolation() {
        AdvanceResult result = transition.waitFor(
                task(OperationCode.DP06),
                OperationCode.DP06,
                ProviderOutcome.transientFailure("HTTP_503"),
                2,
                "ADS_DASHBOARD",
                null,
                "checkpoint-2",
                null
        );

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals(Duration.ofMinutes(2), result.getRetryAfter());
        assertEquals(RiskShareLevel.EXACT, result.getBackoffShareLevel());
    }

    @Test
    void authDoesNotRequireOrCreateBackoffContext() {
        AdvanceResult result = transition.waitFor(
                null,
                null,
                ProviderOutcome.authRequired("AUTH_EXPIRED"),
                0,
                "REPORT_POLL",
                "export-1",
                "checkpoint-3",
                null
        );

        assertEquals(TaskState.WAITING_AUTH, result.getNextState());
        assertEquals(Duration.ofMinutes(5), result.getRetryAfter());
        assertEquals("AUTH_EXPIRED", result.getSanitizedCode());
        assertEquals("export-1", result.getRemoteHandle());
    }

    @Test
    void invalidBackoffContextFailsWithoutLosingCheckpoint() {
        DataPullTask invalid = task(OperationCode.DP08A);
        invalid.setAccountKey(null);

        AdvanceResult result = transition.waitFor(
                invalid,
                OperationCode.DP08A,
                ProviderOutcome.transientFailure("HTTP_EOF"),
                1,
                "FETCH_PAGE_2",
                null,
                "checkpoint-4",
                null
        );

        assertEquals(TaskState.FAILED, result.getNextState());
        assertEquals("PROVIDER_BACKOFF_CONTEXT_INVALID", result.getSanitizedCode());
        assertEquals("checkpoint-4", result.getCheckpoint());
        assertNull(result.getRetryAfter());
    }

    @Test
    void contractFailureUsesExactPersistentBackoff() {
        AdvanceResult result = transition.waitFor(
                task(OperationCode.DP10), OperationCode.DP10,
                ProviderOutcome.contractError("CONTRACT_ERROR"), 1,
                "DP10_LIST", null, "checkpoint", null
        );

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("CONTRACT_ERROR", result.getSanitizedCode());
        assertEquals(RiskShareLevel.EXACT, result.getBackoffShareLevel());
    }

    @Test
    void businessAndUnknownOutcomesAreRejected() {
        List<ProviderOutcome<?>> rejected = List.of(
                ProviderOutcome.success("value"),
                ProviderOutcome.notFound("NOT_FOUND"),
                ProviderOutcome.unknownOutcome("UNKNOWN_CREATE_RESULT")
        );

        for (ProviderOutcome<?> outcome : rejected) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> transition.waitFor(
                            task(OperationCode.DP10), OperationCode.DP10, outcome, 1,
                            "DP10_LIST", null, "checkpoint", null
                    )
            );
        }
    }

    @Test
    void taskOperationMismatchNeverCreatesAHoldUnderTheWrongIdentity() {
        AdvanceResult result = transition.waitFor(
                task(OperationCode.DP04),
                OperationCode.DP05,
                ProviderOutcome.transientFailure("HTTP_503"),
                1,
                "FRONTEND",
                null,
                "checkpoint-5",
                null
        );

        assertEquals(TaskState.FAILED, result.getNextState());
        assertEquals("PROVIDER_BACKOFF_CONTEXT_INVALID", result.getSanitizedCode());
        assertEquals("checkpoint-5", result.getCheckpoint());
    }

    @Test
    void invalidAttemptIsNotMisreportedAsAnIdentityContextWait() {
        assertThrows(
                IllegalArgumentException.class,
                () -> transition.waitFor(
                        task(OperationCode.DP06),
                        OperationCode.DP06,
                        ProviderOutcome.transientFailure("HTTP_503"),
                        0,
                        "ADS_DASHBOARD",
                        null,
                        "checkpoint-6",
                        null
                )
        );
    }

    private DataPullTask task(OperationCode operationCode) {
        DataPullTask task = new DataPullTask();
        task.setOperationCode(operationCode);
        task.setProviderChannel("NOON_FRONTEND");
        task.setAccountKey("PRJ108065");
        task.setScopeKey("STR108065-NSA:SA");
        task.setEgressKey("egress-cn-1");
        return task;
    }
}
