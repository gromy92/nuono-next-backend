package com.nuono.next.datapull.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InMemoryDataPullTaskStoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 0);

    @Test
    void enqueueIsIdempotentOnOperationScopeAndBusinessWindow() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask first = task(store.nextTaskId(), "2026-08-02");
        DataPullTask duplicate = task(store.nextTaskId(), "2026-08-02");

        DataPullTask firstResult = store.enqueue(first);
        DataPullTask duplicateResult = store.enqueue(duplicate);

        assertEquals(firstResult.getId(), duplicateResult.getId());
        assertTrue(store.find(first.getId()).isPresent());
        assertFalse(store.find(duplicate.getId()).isPresent());
        assertEquals(Optional.of(NOW), store.latestScheduleSlot(OperationCode.DP04, "store:SA"));
    }

    @Test
    void stableTaskKeyCannotResolveToADifferentScopeSnapshot() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        store.enqueue(task(store.nextTaskId(), "2026-08-02"));
        DataPullTask conflicting = task(store.nextTaskId(), "2026-08-02");
        conflicting.setProjectCode("PRJ-CHANGED-AFTER-SCHEDULE");

        assertThrows(IllegalStateException.class, () -> store.enqueue(conflicting));
    }

    @Test
    void concurrentClaimHasExactlyOneWinner() throws Exception {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask task = store.enqueue(task(store.nextTaskId(), "2026-08-02"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<DataPullTask>> first = pool.submit(() -> {
                start.await();
                return store.claim(task.getId(), task.getVersion(), "worker-a", NOW.plusMinutes(5), NOW);
            });
            Future<Optional<DataPullTask>> second = pool.submit(() -> {
                start.await();
                return store.claim(task.getId(), task.getVersion(), "worker-b", NOW.plusMinutes(5), NOW);
            });

            start.countDown();
            int winners = (first.get().isPresent() ? 1 : 0) + (second.get().isPresent() ? 1 : 0);
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredLeaseCanBeReclaimedWithANewFence() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask queued = store.enqueue(task(store.nextTaskId(), "2026-08-02"));
        DataPullTask firstClaim = store.claim(
                queued.getId(), queued.getVersion(), "worker-a", NOW.plusMinutes(1), NOW
        ).orElseThrow();

        assertFalse(store.claim(
                queued.getId(), firstClaim.getVersion(), "worker-b", NOW.plusMinutes(3), NOW.plusSeconds(30)
        ).isPresent());

        DataPullTask recovered = store.claim(
                queued.getId(),
                firstClaim.getVersion(),
                "worker-b",
                NOW.plusMinutes(7),
                NOW.plusMinutes(2)
        ).orElseThrow();
        assertEquals(firstClaim.getFenceEpoch() + 1L, recovered.getFenceEpoch());
        assertEquals(0, recovered.getAttempt());
    }
    @Test
    void exactUnstartedClaimReleaseRequeuesWithoutErasingProgressEvidence() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask candidate = task(store.nextTaskId(), "2026-08-02");
        candidate.setStepCode("POLL_REPORT");
        candidate.setRemoteHandle("report-17");
        candidate.setCheckpoint("page=4");
        DataPullTask queued = store.enqueue(candidate);
        DataPullTask claimed = store.claim(queued.getId(), queued.getVersion(),
                "worker-a", NOW.plusMinutes(5), NOW).orElseThrow();
        assertTrue(store.releaseUnstartedClaim(DataPullUnstartedClaimRelease.from(
                claimed, NOW.plusSeconds(1))));
        DataPullTask released = store.find(claimed.getId()).orElseThrow();
        assertEquals(TaskState.QUEUED, released.getState());
        assertEquals("POLL_REPORT", released.getStepCode());
        assertEquals("report-17", released.getRemoteHandle());
        assertEquals("page=4", released.getCheckpoint());
        assertEquals(claimed.getFenceEpoch(), released.getFenceEpoch());
        assertEquals(claimed.getVersion() + 1L, released.getVersion());
        assertEquals(null, released.getLeaseOwner());
        assertEquals(null, released.getLeaseUntil());
        assertFalse(store.releaseUnstartedClaim(DataPullUnstartedClaimRelease.from(claimed,
                NOW.plusSeconds(2))));
    }
    @Test
    void staleFenceCannotTransitionAReclaimedTask() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask queued = store.enqueue(task(store.nextTaskId(), "2026-08-02"));
        DataPullTask staleClaim = store.claim(
                queued.getId(), queued.getVersion(), "worker-a", NOW.plusMinutes(1), NOW
        ).orElseThrow();
        DataPullTask currentClaim = store.claim(
                queued.getId(),
                staleClaim.getVersion(),
                "worker-b",
                NOW.plusMinutes(7),
                NOW.plusMinutes(2)
        ).orElseThrow();

        boolean staleChanged = store.transition(new DataPullTaskTransition(
                queued.getId(),
                staleClaim.getFenceEpoch(),
                staleClaim.getVersion(),
                "worker-a",
                TaskState.SUCCEEDED,
                "APPLY",
                null,
                null,
                null,
                null,
                NOW.plusMinutes(2),
                NOW.plusMinutes(2)
        ));

        assertFalse(staleChanged);
        assertEquals(currentClaim.getFenceEpoch(), store.find(queued.getId()).orElseThrow().getFenceEpoch());
        assertEquals(TaskState.RUNNING, store.find(queued.getId()).orElseThrow().getState());
    }
    @Test
    void waitingTaskCannotBeClaimedBeforeRetryTime() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask queued = store.enqueue(task(store.nextTaskId(), "2026-08-02"));
        DataPullTask claimed = store.claim(
                queued.getId(), queued.getVersion(), "worker-a", NOW.plusMinutes(2), NOW
        ).orElseThrow();
        LocalDateTime retryAt = NOW.plusMinutes(10);
        assertTrue(store.transition(new DataPullTaskTransition(
                queued.getId(),
                claimed.getFenceEpoch(),
                claimed.getVersion(),
                "worker-a",
                TaskState.WAITING_BACKOFF,
                "FETCH_PAGE",
                null,
                "page=4",
                retryAt,
                "HTTP_429",
                null,
                NOW.plusSeconds(10)
        )));
        DataPullTask waiting = store.find(queued.getId()).orElseThrow();

        assertTrue(store.dueCandidates(NOW.plusMinutes(9), 10).isEmpty());
        assertFalse(store.claim(
                waiting.getId(),
                waiting.getVersion(),
                "worker-b",
                NOW.plusMinutes(12),
                NOW.plusMinutes(9)
        ).isPresent());
        DataPullTask reclaimed = store.claim(
                waiting.getId(),
                waiting.getVersion(),
                "worker-b",
                NOW.plusMinutes(15),
                retryAt
        ).orElseThrow();
        assertEquals(1, waiting.getAttempt());
        assertEquals(1, reclaimed.getAttempt());
    }

    @Test
    void attemptCountsWaitingCyclesAndResetsAfterNormalProgress() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask queued = store.enqueue(task(store.nextTaskId(), "2026-08-02"));
        DataPullTask firstClaim = store.claim(
                queued.getId(), queued.getVersion(), "worker-a", NOW.plusMinutes(2), NOW
        ).orElseThrow();
        LocalDateTime retryAt = NOW.plusMinutes(5);
        assertTrue(store.transition(new DataPullTaskTransition(
                queued.getId(),
                firstClaim.getFenceEpoch(),
                firstClaim.getVersion(),
                "worker-a",
                TaskState.WAITING_REMOTE,
                "POLL",
                "remote-1",
                "pending",
                retryAt,
                "REMOTE_PENDING",
                null,
                NOW.plusSeconds(10)
        )));
        DataPullTask waiting = store.find(queued.getId()).orElseThrow();
        DataPullTask secondClaim = store.claim(
                waiting.getId(),
                waiting.getVersion(),
                "worker-b",
                retryAt.plusMinutes(2),
                retryAt
        ).orElseThrow();

        assertEquals(1, waiting.getAttempt());
        assertEquals(1, secondClaim.getAttempt());
        assertTrue(store.transition(new DataPullTaskTransition(
                queued.getId(),
                secondClaim.getFenceEpoch(),
                secondClaim.getVersion(),
                "worker-b",
                TaskState.QUEUED,
                "APPLY",
                "remote-1",
                "ready",
                null,
                null,
                null,
                retryAt.plusSeconds(10)
        )));
        assertEquals(0, store.find(queued.getId()).orElseThrow().getAttempt());
    }

    @Test
    void unfinishedPredecessorBlocksButTerminalFailureDoesNotFreezeFutureDays() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask first = store.enqueue(task(store.nextTaskId(), "2026-08-02"));
        DataPullTask later = store.enqueue(task(store.nextTaskId(), "2026-08-03"));

        assertEquals(List.of(first.getId()), store.dueCandidates(NOW, 10).stream()
                .map(DataPullTask::getId)
                .collect(java.util.stream.Collectors.toList()));
        assertFalse(store.claim(
                later.getId(), later.getVersion(), "worker-b", NOW.plusMinutes(5), NOW
        ).isPresent());

        DataPullTask claimed = store.claim(
                first.getId(), first.getVersion(), "worker-a", NOW.plusMinutes(5), NOW
        ).orElseThrow();
        assertTrue(store.transition(new DataPullTaskTransition(
                first.getId(),
                claimed.getFenceEpoch(),
                claimed.getVersion(),
                "worker-a",
                TaskState.FAILED,
                "FETCH_PAGE",
                null,
                null,
                null,
                "TASK_IDENTITY_INVALID",
                NOW.plusMinutes(1),
                NOW.plusMinutes(1)
        )));

        assertEquals(List.of(later.getId()), store.dueCandidates(NOW.plusMinutes(1), 10).stream()
                .map(DataPullTask::getId)
                .collect(java.util.stream.Collectors.toList()));
    }

    private DataPullTask task(long id, String businessWindow) {
        return DataPullTask.queued(
                id,
                OperationCode.DP04,
                "noon-partner",
                307L,
                108065L,
                "PRJ108065",
                "egress-cn-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "store:SA",
                NOW,
                businessWindow,
                "FETCH_PAGE",
                NOW.minusMinutes(1)
        );
    }
}
