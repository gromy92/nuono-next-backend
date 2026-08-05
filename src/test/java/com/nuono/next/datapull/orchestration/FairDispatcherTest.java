package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskTransition;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FairDispatcherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 0);
    private static final DataPullRuntimeLeaderLease LEADER = new DataPullRuntimeLeaderLease(
            "worker-1", 7L, NOW.plusMinutes(1), NOW
    );

    @Test
    void roundRobinPreventsOneBusyBucketFromSaturatingTheClaimBatch() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        enqueue(store, "account-a", "scope-a", "a-1", null);
        enqueue(store, "account-a", "scope-a", "a-2", null);
        enqueue(store, "account-a", "scope-a", "a-3", null);
        enqueue(store, "account-b", "scope-b", "b-1", null);
        FairDispatcher dispatcher = dispatcher(store, neverHeld());

        List<DataPullTask> claimed = dispatcher.dispatchDue(
                NOW,
                2,
                Duration.ofMinutes(5),
                LEADER
        );

        assertEquals(
                List.of("account-a", "account-b"),
                claimed.stream().map(DataPullTask::getAccountKey).collect(Collectors.toList())
        );
    }

    @Test
    void durableAccountHoldExcludesOnlyThatAccountBeforeClaim() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        enqueue(store, "account-a", "scope-a", "a-1", null);
        enqueue(store, "account-b", "scope-b", "b-1", null);
        BackoffHoldGate gate = (level, identity, now) -> level == RiskShareLevel.ACCOUNT
                && identity.getAccountKey().equals("account-a");

        List<DataPullTask> claimed = dispatcher(store, gate).dispatchDue(
                NOW,
                2,
                Duration.ofMinutes(5),
                LEADER
        );

        assertEquals(1, claimed.size());
        assertEquals("account-b", claimed.get(0).getAccountKey());
        assertNull(store.find(1L).orElseThrow().getLeaseOwner());
    }

    @Test
    void unknownEgressNeverExpandsLookupIntoAnExitWideHold() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        enqueue(store, "account-a", "scope-a", "a-1", null);
        AtomicInteger exitLookups = new AtomicInteger();
        BackoffHoldGate gate = (level, identity, now) -> {
            if (level == RiskShareLevel.EXIT) {
                exitLookups.incrementAndGet();
            }
            return false;
        };

        List<DataPullTask> claimed = dispatcher(store, gate).dispatchDue(
                NOW,
                1,
                Duration.ofMinutes(5),
                LEADER
        );

        assertEquals(1, claimed.size());
        assertEquals(0, exitLookups.get());
    }

    @Test
    void verifiedEgressCanBeHeldAtExitLevel() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        enqueue(store, "account-a", "scope-a", "a-1", "egress-cn-1");
        AtomicInteger exitLookups = new AtomicInteger();
        BackoffHoldGate gate = (level, identity, now) -> {
            if (level != RiskShareLevel.EXIT) {
                return false;
            }
            exitLookups.incrementAndGet();
            assertEquals("egress-cn-1", identity.requireEgressKey());
            return true;
        };

        List<DataPullTask> claimed = dispatcher(store, gate).dispatchDue(
                NOW,
                1,
                Duration.ofMinutes(5),
                LEADER
        );

        assertEquals(List.of(), claimed);
        assertEquals(1, exitLookups.get());
    }

    @Test
    void oneTickScansPastAFullPageOfHeldScopes() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        for (int index = 0; index < 64; index++) {
            enqueue(store, "held-" + index, "held-scope-" + index, "held-window", null);
        }
        enqueue(store, "free", "free-scope", "free-window", null);
        BackoffHoldGate gate = (level, identity, now) -> level == RiskShareLevel.ACCOUNT
                && identity.getAccountKey().startsWith("held-");
        FairDispatcher dispatcher = dispatcher(store, gate);

        List<DataPullTask> claimed = dispatcher.dispatchDue(
                NOW,
                1,
                Duration.ofMinutes(5),
                LEADER
        );

        assertEquals(1, claimed.size());
        assertEquals("free", claimed.get(0).getAccountKey());
    }

    @Test
    void newerArrivalsCannotMoveTheCursorPastAnOlderUnclaimedTask() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        for (int index = 1; index <= 64; index++) {
            enqueue(store, "old-" + index, "old-scope-" + index, "old-window", null);
        }
        FairDispatcher dispatcher = dispatcher(store, neverHeld());

        List<DataPullTask> first = dispatcher.dispatchDue(
                NOW, 1, Duration.ofMinutes(5), LEADER
        );
        for (int index = 1; index <= 64; index++) {
            enqueue(store, "new-" + index, "new-scope-" + index, "new-window", null);
        }
        List<DataPullTask> second = dispatcher.dispatchDue(
                NOW, 1, Duration.ofMinutes(5), LEADER
        );

        assertEquals(1L, first.get(0).getId());
        assertEquals(2L, second.get(0).getId());
    }

    @Test
    void oneShortMultiStepTaskCannotConsumeEveryTickAheadOfItsPeer() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        enqueue(store, "account-a", "scope-a", "window-a", null);
        enqueue(store, "account-b", "scope-b", "window-b", null);
        FairDispatcher dispatcher = dispatcher(store, neverHeld());

        DataPullTask first = dispatcher.dispatchDue(
                NOW, 1, Duration.ofMinutes(5), LEADER
        ).get(0);
        assertTrue(store.transition(new DataPullTaskTransition(
                first.getId(),
                first.getFenceEpoch(),
                first.getVersion(),
                "worker-1",
                TaskState.QUEUED,
                "NEXT_STEP",
                null,
                "checkpoint",
                null,
                null,
                null,
                NOW.plusSeconds(1)
        )));

        DataPullTask second = dispatcher.dispatchDue(
                NOW.plusSeconds(2), 1, Duration.ofMinutes(5), LEADER
        ).get(0);

        assertEquals("account-a", first.getAccountKey());
        assertEquals("account-b", second.getAccountKey());
    }

    @Test
    void emergencyHoldsAreLoadedOnceForOneDispatchPass() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        enqueue(store, "account-a", "scope-a", "window-a", null);
        AtomicInteger lookups = new AtomicInteger();
        EmergencyClaimHoldStore holds = new EmergencyClaimHoldStore() {
            @Override
            public void place(EmergencyClaimHold hold) {
                throw new UnsupportedOperationException("not used by this race seam");
            }

            @Override
            public EmergencyClaimHoldSnapshot activeAt(LocalDateTime nowUtc) {
                lookups.incrementAndGet();
                return EmergencyClaimHoldSnapshot.from(List.of(), nowUtc);
            }
        };
        FairDispatcher dispatcher = new FairDispatcher(store, neverHeld(), holds);

        List<DataPullTask> claimed = dispatcher.dispatchDue(
                NOW, 1, Duration.ofMinutes(5), LEADER
        );

        assertEquals(1, claimed.size());
        assertEquals(1, lookups.get());
    }

    private DataPullTask enqueue(
            InMemoryDataPullTaskStore store,
            String account,
            String scope,
            String window,
            String egress
    ) {
        return store.enqueue(DataPullTask.queued(
                store.nextTaskId(),
                OperationCode.DP04,
                "noon-partner",
                307L,
                108065L,
                account,
                egress,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                scope,
                NOW,
                window,
                "FETCH",
                NOW.minusMinutes(1)
        ));
    }

    private BackoffHoldGate neverHeld() {
        return (level, identity, now) -> false;
    }

    private FairDispatcher dispatcher(
            InMemoryDataPullTaskStore store,
            BackoffHoldGate backoffHoldGate
    ) {
        return new FairDispatcher(
                store,
                backoffHoldGate,
                new InMemoryEmergencyClaimHoldStore()
        );
    }
}
