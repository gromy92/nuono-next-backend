package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeExecutorTest extends RuntimeExecutorTestFixture {

    @Test
    void mapsOneAdvanceResultThroughTheClaimedFence() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        InMemoryBackoffHoldStore holds = new InMemoryBackoffHoldStore();
        DataPullTask claimed = claimed(store, "worker-1");
        AdvanceResult result = AdvanceResult.waitingBackoff(
                "FETCH_NEXT",
                "remote-7",
                "page=7",
                Duration.ofMinutes(4),
                "HTTP_429"
        );
        RuntimeExecutor executor = executor(store, holds, ignored -> result);

        assertTrue(executor.execute(claimed, NOW.plusSeconds(10)));

        DataPullTask stored = store.find(claimed.getId()).orElseThrow();
        assertEquals(TaskState.WAITING_BACKOFF, stored.getState());
        assertEquals("FETCH_NEXT", stored.getStepCode());
        assertEquals("remote-7", stored.getRemoteHandle());
        assertEquals("page=7", stored.getCheckpoint());
        assertEquals(NOW.plusMinutes(4).plusSeconds(10), stored.getRetryNotBefore());
        assertEquals(1, stored.getAttempt());
        assertNull(stored.getLeaseOwner());
        assertTrue(holds.isHeld(
                com.nuono.next.datapull.runtime.RiskShareLevel.EXACT,
                DataPullBackoffIdentity.from(stored),
                NOW.plusMinutes(4)
        ));
    }

    @Test
    void commitsARouterStageHoldUnderTheActualProviderChannel() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        InMemoryBackoffHoldStore holds = new InMemoryBackoffHoldStore();
        DataPullTask claimed = claimed(store, "worker-1");
        RuntimeExecutor executor = executor(
                store,
                holds,
                ignored -> AdvanceResult.waitingBackoffForProvider(
                        "NOON_CONSUMER_FRONTEND",
                        "FRONTEND",
                        null,
                        "same-item",
                        Duration.ofMinutes(4),
                        "HTTP_429",
                        com.nuono.next.datapull.runtime.RiskShareLevel.EXACT
                )
        );

        assertTrue(executor.execute(claimed, NOW.plusSeconds(10)));
        DataPullTask stored = store.find(claimed.getId()).orElseThrow();
        assertTrue(holds.isHeld(
                com.nuono.next.datapull.runtime.RiskShareLevel.EXACT,
                DataPullBackoffIdentity.from(stored, "NOON_CONSUMER_FRONTEND"),
                NOW.plusMinutes(4)
        ));
        assertFalse(holds.isHeld(
                com.nuono.next.datapull.runtime.RiskShareLevel.EXACT,
                DataPullBackoffIdentity.from(stored),
                NOW.plusMinutes(4)
        ));
    }

    @Test
    void authWaitGetsABoundedDefaultPollTimeInsteadOfSleepingForever() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        RuntimeExecutor executor = executor(
                store,
                ignored -> AdvanceResult.waitingAuth("auth-pending", "AUTH_REQUIRED")
        );

        assertTrue(executor.execute(claimed, NOW.plusSeconds(10)));

        DataPullTask stored = store.find(claimed.getId()).orElseThrow();
        assertEquals(TaskState.WAITING_AUTH, stored.getState());
        assertEquals(NOW.plusMinutes(5).plusSeconds(10), stored.getRetryNotBefore());
        assertEquals(1, stored.getAttempt());
    }

    @Test
    void handlerReceivesThePersistedScopeSnapshotWithoutParsingTheStableKey() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        RuntimeExecutor executor = executor(store, context -> {
            DataPullScope scope = context.getScope();
            assertEquals(307L, scope.getOwnerUserId());
            assertEquals(108065L, scope.getLogicalStoreId());
            assertEquals("account-307", scope.getAccountKey());
            assertEquals("egress-cn-1", scope.getEgressKey());
            assertEquals("PRJ108065", scope.getProjectCode());
            assertEquals("STR108065-NSA", scope.getStoreCode());
            assertEquals("SA", scope.getSiteCode());
            assertEquals("scope-sa", scope.getStableScopeKey());
            return AdvanceResult.succeeded();
        });

        assertTrue(executor.execute(claimed, NOW.plusSeconds(10)));
    }

    @Test
    void untypedHandlerExceptionRetriesWithoutLosingCheckpointOrSensitiveDetails() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        InMemoryBackoffHoldStore holds = new InMemoryBackoffHoldStore();
        DataPullTask claimed = claimed(store, "worker-1");
        RuntimeExecutor executor = executor(store, holds, ignored -> {
            throw new IllegalStateException("secret access token and raw provider payload");
        });

        assertTrue(executor.execute(claimed, NOW.plusSeconds(10)));

        DataPullTask stored = store.find(claimed.getId()).orElseThrow();
        assertEquals(TaskState.WAITING_REMOTE, stored.getState());
        assertEquals(NOW.plusMinutes(1).plusSeconds(10), stored.getRetryNotBefore());
        assertNull(stored.getFinishedAt());
        assertEquals(claimed.getStepCode(), stored.getStepCode());
        assertEquals(claimed.getCheckpoint(), stored.getCheckpoint());
        assertEquals("DP_HANDLER_UNTYPED_FAILURE", stored.getSanitizedFailureCode());
        assertFalse(stored.getSanitizedFailureCode().contains("secret"));
        assertFalse(holds.isHeld(
                com.nuono.next.datapull.runtime.RiskShareLevel.EXACT,
                DataPullBackoffIdentity.from(stored),
                NOW.plusMinutes(20)
        ));
    }

    @Test
    void staleFenceCannotCommitEvenWhenItsDetachedSnapshotLooksLive() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask stale = claimed(store, "worker-a", NOW.plusMinutes(1));
        DataPullTask current = store.claim(
                stale.getId(),
                stale.getVersion(),
                "worker-b",
                NOW.plusMinutes(10),
                NOW.plusMinutes(2)
        ).orElseThrow();
        stale.setLeaseUntil(NOW.plusMinutes(10));
        RuntimeExecutor executor = executor(
                store,
                ignored -> AdvanceResult.succeeded(),
                NOW.plusMinutes(3)
        );

        assertFalse(executor.execute(stale, NOW.plusMinutes(3)));

        DataPullTask stored = store.find(stale.getId()).orElseThrow();
        assertEquals(current.getFenceEpoch(), stored.getFenceEpoch());
        assertEquals(TaskState.RUNNING, stored.getState());
        assertEquals("worker-b", stored.getLeaseOwner());
    }

    @Test
    void staleFenceCannotPublishABackoffHold() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        InMemoryBackoffHoldStore holds = new InMemoryBackoffHoldStore();
        DataPullTask stale = claimed(store, "worker-a", NOW.plusMinutes(1));
        store.claim(
                stale.getId(),
                stale.getVersion(),
                "worker-b",
                NOW.plusMinutes(10),
                NOW.plusMinutes(2)
        ).orElseThrow();
        stale.setLeaseUntil(NOW.plusMinutes(10));
        RuntimeExecutor executor = executor(
                store,
                holds,
                ignored -> AdvanceResult.waitingBackoff(
                        stale.getCheckpoint(),
                        Duration.ofMinutes(15),
                        "HTTP_429"
                ),
                NOW.plusMinutes(3)
        );

        assertFalse(executor.execute(stale, NOW.plusMinutes(3)));
        assertFalse(holds.isHeld(
                com.nuono.next.datapull.runtime.RiskShareLevel.EXACT,
                DataPullBackoffIdentity.from(stale),
                NOW.plusMinutes(4)
        ));
    }

    @Test
    void completionAfterLeaseExpiryCannotCommitWithoutACompetingReclaim() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-a", NOW.plusMinutes(1));
        RuntimeExecutor executor = executor(
                store,
                ignored -> AdvanceResult.succeeded(),
                NOW.plusMinutes(2)
        );

        assertFalse(executor.execute(claimed, NOW.plusSeconds(10)));

        DataPullTask stored = store.find(claimed.getId()).orElseThrow();
        assertEquals(TaskState.RUNNING, stored.getState());
        assertNull(stored.getFinishedAt());
        assertEquals("worker-a", stored.getLeaseOwner());
    }

    @Test
    void missingHandlerFailsClosedBeforeAnyExternalAdvance() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        RuntimeExecutor executor = new RuntimeExecutor(new DataPullJobRegistry(List.of()), store);

        assertThrows(IllegalStateException.class, () -> executor.execute(claimed, NOW.plusSeconds(10)));
        assertEquals(TaskState.RUNNING, store.find(claimed.getId()).orElseThrow().getState());
    }

}
