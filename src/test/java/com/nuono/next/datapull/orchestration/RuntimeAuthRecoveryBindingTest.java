package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RuntimeAuthRecoveryBindingTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 0);

    @Test
    void bindsOnlyAfterTheWaitingAuthFenceCommits() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store);
        AtomicInteger calls = new AtomicInteger();
        AtomicLong waitingVersion = new AtomicLong();
        RuntimeTransitionCommitter committer = new RuntimeTransitionCommitter(
                store,
                new InMemoryBackoffHoldStore(),
                (task, version, committedAt) -> {
                    calls.incrementAndGet();
                    waitingVersion.set(version);
                    assertEquals(NOW.plusSeconds(2), committedAt);
                }
        );

        assertTrue(committer.commit(
                claimed,
                AdvanceResult.waitingAuth("same-checkpoint", "AUTH_REQUIRED"),
                NOW.plusSeconds(2)
        ));

        assertEquals(1, calls.get());
        assertEquals(claimed.getVersion() + 1L, waitingVersion.get());
    }

    @Test
    void staleFenceNeverEnqueuesARecoveryBinding() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask stale = claimed(store);
        store.claim(
                stale.getId(),
                stale.getVersion(),
                "replacement",
                NOW.plusMinutes(20),
                NOW.plusMinutes(11)
        ).orElseThrow();
        stale.setLeaseUntil(NOW.plusMinutes(20));
        AtomicInteger calls = new AtomicInteger();
        RuntimeTransitionCommitter committer = new RuntimeTransitionCommitter(
                store,
                new InMemoryBackoffHoldStore(),
                (task, version, committedAt) -> calls.incrementAndGet()
        );

        assertFalse(committer.commit(
                stale,
                AdvanceResult.waitingAuth("same-checkpoint", "AUTH_REQUIRED"),
                NOW.plusMinutes(12)
        ));
        assertEquals(0, calls.get());
    }

    private DataPullTask claimed(InMemoryDataPullTaskStore store) {
        DataPullTask queued = store.enqueue(DataPullTask.queued(
                store.nextTaskId(),
                OperationCode.DP04,
                "NOON_PARTNER_PRODUCT_LIST",
                307L,
                108065L,
                "account-307",
                "egress-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa",
                NOW,
                "DP04:complete-snapshot:2026-08-02",
                "FETCH",
                NOW.minusMinutes(1)
        ));
        return store.claim(
                queued.getId(),
                queued.getVersion(),
                "worker-1",
                NOW.plusMinutes(10),
                NOW
        ).orElseThrow();
    }
}
