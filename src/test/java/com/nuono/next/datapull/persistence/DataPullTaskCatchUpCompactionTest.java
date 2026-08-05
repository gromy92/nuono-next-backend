package com.nuono.next.datapull.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class DataPullTaskCatchUpCompactionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 0, 0);

    @Test
    void currentCompactionIsRestartIdempotent() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask old = store.enqueue(currentTask(
                store.nextTaskId(), NOW.minusDays(3), "DP04:complete-snapshot:2026-08-02"
        ));
        DataPullTask proposed = currentTask(
                store.nextTaskId(), NOW, "DP04:complete-snapshot:2026-08-05"
        );

        DataPullTask first = store.enqueueCatchUp(
                proposed, DataPullTaskCatchUpMode.LATEST_CURRENT, NOW
        );
        DataPullTask replay = store.enqueueCatchUp(
                currentTask(store.nextTaskId(), NOW, proposed.getBusinessWindowKey()),
                DataPullTaskCatchUpMode.LATEST_CURRENT,
                NOW.plusSeconds(1)
        );

        assertEquals(first.getId(), replay.getId());
        assertEquals(TaskState.SUPERSEDED, store.find(old.getId()).orElseThrow().getState());
        assertEquals(TaskState.QUEUED, store.find(first.getId()).orElseThrow().getState());
        assertEquals(Optional.of(NOW), store.latestScheduleSlot(OperationCode.DP04, "store:SA"));
    }

    @Test
    void remoteHandleAndCheckpointPreventSupersede() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask old = store.enqueue(currentTask(
                store.nextTaskId(), NOW.minusDays(1), "DP04:complete-snapshot:2026-08-04"
        ));
        DataPullTask claimed = store.claim(
                old.getId(), old.getVersion(), "worker-a", NOW.plusMinutes(5), NOW
        ).orElseThrow();
        assertTrue(store.transition(new DataPullTaskTransition(
                claimed.getId(),
                claimed.getFenceEpoch(),
                claimed.getVersion(),
                "worker-a",
                TaskState.WAITING_REMOTE,
                "FETCH_PAGE",
                "remote-page-handle",
                "page=2",
                NOW.plusMinutes(10),
                "REMOTE_PENDING",
                null,
                NOW.plusSeconds(1)
        )));

        DataPullTask latest = store.enqueueCatchUp(
                currentTask(
                        store.nextTaskId(), NOW, "DP04:complete-snapshot:2026-08-05"
                ),
                DataPullTaskCatchUpMode.LATEST_CURRENT,
                NOW.plusSeconds(2)
        );

        DataPullTask preserved = store.find(old.getId()).orElseThrow();
        assertEquals(TaskState.WAITING_REMOTE, preserved.getState());
        assertEquals("remote-page-handle", preserved.getRemoteHandle());
        assertEquals("page=2", preserved.getCheckpoint());
        assertEquals(TaskState.QUEUED, store.find(latest.getId()).orElseThrow().getState());
    }

    @Test
    void rollingCompactionUnionsExistingAndNewNeverStartedRanges() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask old = store.enqueue(rollingTask(
                store.nextTaskId(),
                NOW.minusDays(3),
                "DP01:date-range:2026-07-03..2026-08-01"
        ));

        DataPullTask replacement = store.enqueueCatchUp(
                rollingTask(
                        store.nextTaskId(),
                        NOW,
                        "DP01:date-range:2026-07-04..2026-08-04"
                ),
                DataPullTaskCatchUpMode.ROLLING_DATE_UNION,
                NOW
        );

        assertEquals("DP01:date-range:2026-07-03..2026-08-04", replacement.getBusinessWindowKey());
        assertEquals(TaskState.SUPERSEDED, store.find(old.getId()).orElseThrow().getState());
    }

    @Test
    void claimAndCompactionRaceHasOneCasWinnerWithoutLosingReplacement() throws Exception {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask old = store.enqueue(currentTask(
                store.nextTaskId(), NOW.minusDays(1), "DP04:complete-snapshot:2026-08-04"
        ));
        DataPullTask proposed = currentTask(
                store.nextTaskId(), NOW, "DP04:complete-snapshot:2026-08-05"
        );
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<DataPullTask>> claim = pool.submit(() -> {
                start.await();
                return store.claim(
                        old.getId(), old.getVersion(), "worker-a", NOW.plusMinutes(5), NOW
                );
            });
            Future<DataPullTask> compact = pool.submit(() -> {
                start.await();
                return store.enqueueCatchUp(
                        proposed, DataPullTaskCatchUpMode.LATEST_CURRENT, NOW
                );
            });

            start.countDown();
            boolean claimWon = claim.get().isPresent();
            DataPullTask replacement = compact.get();
            TaskState oldState = store.find(old.getId()).orElseThrow().getState();

            assertEquals(claimWon ? TaskState.RUNNING : TaskState.SUPERSEDED, oldState);
            assertEquals(TaskState.QUEUED, store.find(replacement.getId()).orElseThrow().getState());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void workerCannotTransitionToSupersededAndExactOperationCannotUseCompaction() {
        assertThrows(IllegalArgumentException.class, () -> new DataPullTaskTransition(
                1L, 1L, 1L, "worker-a", TaskState.SUPERSEDED, "FETCH", null, null,
                null, null, NOW, NOW
        ));

        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask exact = task(
                store.nextTaskId(),
                OperationCode.DP02,
                NOW,
                "DP02:date-range:2026-08-04..2026-08-04"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> store.enqueueCatchUp(exact, DataPullTaskCatchUpMode.ROLLING_DATE_UNION, NOW)
        );
        assertFalse(store.find(exact.getId()).isPresent());
    }

    private DataPullTask currentTask(long id, LocalDateTime slot, String window) {
        return task(id, OperationCode.DP04, slot, window);
    }

    private DataPullTask rollingTask(long id, LocalDateTime slot, String window) {
        return task(id, OperationCode.DP01, slot, window);
    }

    private DataPullTask task(
            long id,
            OperationCode operation,
            LocalDateTime slot,
            String window
    ) {
        return DataPullTask.queued(
                id,
                operation,
                "noon-partner",
                307L,
                108065L,
                "PRJ108065",
                "egress-cn-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "store:SA",
                slot,
                window,
                "FETCH_PAGE",
                NOW.minusMinutes(1)
        );
    }
}
