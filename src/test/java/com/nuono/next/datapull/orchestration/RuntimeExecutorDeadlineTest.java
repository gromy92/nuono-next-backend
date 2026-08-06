package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RuntimeExecutorDeadlineTest extends RuntimeExecutorTestFixture {

    @Test
    void jobDeadlineBecomesShortWaitingRemoteAndClearsItsWorkerInterrupt() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        DataPullJob blocking = job(ignored -> {
            try {
                new CountDownLatch(1).await();
                return AdvanceResult.succeeded();
            } catch (InterruptedException deadline) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("deadline interrupted the handler", deadline);
            }
        });
        RuntimeExecutor executor = deadlineExecutor(
                blocking,
                new RuntimeTransitionCommitter(store, new InMemoryBackoffHoldStore())
        );
        long started = System.nanoTime();

        assertTrue(executor.execute(claimed, NOW.plusSeconds(10)));

        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(
                Duration.ofSeconds(1)) < 0);
        DataPullTask stored = store.find(claimed.getId()).orElseThrow();
        assertEquals(TaskState.WAITING_REMOTE, stored.getState());
        assertEquals("DP_ADVANCE_DEADLINE_EXCEEDED", stored.getSanitizedFailureCode());
        assertEquals(NOW.plusMinutes(1).plusSeconds(10), stored.getRetryNotBefore());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void transitionDeadlineDoesNotRetryTheCommitAndLeavesTheClaimRunning() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        RuntimeTransitionCommitter committer = mock(RuntimeTransitionCommitter.class);
        when(committer.commit(
                org.mockito.ArgumentMatchers.eq(claimed),
                org.mockito.ArgumentMatchers.any(AdvanceResult.class),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(ignored -> {
            try {
                new CountDownLatch(1).await();
                return true;
            } catch (InterruptedException deadline) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("deadline interrupted the commit", deadline);
            }
        });
        RuntimeExecutor executor = deadlineExecutor(
                job(ignored -> AdvanceResult.succeeded()),
                committer
        );

        assertFalse(executor.execute(claimed, NOW.plusSeconds(10)));

        verify(committer).commit(
                org.mockito.ArgumentMatchers.eq(claimed),
                org.mockito.ArgumentMatchers.any(AdvanceResult.class),
                org.mockito.ArgumentMatchers.any()
        );
        assertEquals(TaskState.RUNNING, store.find(claimed.getId()).orElseThrow().getState());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void stoppingBeforeWorkerEntryReleasesTheClaimAndNeverStartsTheJob() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        stopSignal.markStopping();
        AtomicBoolean jobStarted = new AtomicBoolean();
        RuntimeTransitionCommitter committer = new RuntimeTransitionCommitter(
                store, new InMemoryBackoffHoldStore());
        RuntimeExecutor executor = new RuntimeExecutor(
                new DataPullJobRegistry(List.of(job(ignored -> {
                    jobStarted.set(true);
                    return AdvanceResult.succeeded();
                }))),
                committer,
                Clock.fixed(NOW.plusSeconds(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                stopSignal
        );

        assertFalse(executor.execute(claimed, NOW.plusSeconds(10)));

        assertFalse(jobStarted.get());
        assertEquals(TaskState.QUEUED, store.find(claimed.getId()).orElseThrow().getState());
    }

    @Test
    void stoppingAfterDeadlineOpenButBeforeJobInvocationReleasesTheClaim() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        AtomicBoolean jobStarted = new AtomicBoolean();
        RuntimeExecutor executor = new RuntimeExecutor(
                new DataPullJobRegistry(List.of(job(ignored -> {
                    jobStarted.set(true);
                    return AdvanceResult.succeeded();
                }))),
                new RuntimeTransitionCommitter(store, new InMemoryBackoffHoldStore()),
                Clock.fixed(NOW.plusSeconds(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                Duration.ofSeconds(5),
                Duration.ofSeconds(3),
                Duration.ofSeconds(1),
                stopSignal,
                stopSignal::markStopping
        );

        assertFalse(executor.execute(claimed, NOW.plusSeconds(10)));

        assertFalse(jobStarted.get());
        assertEquals(TaskState.QUEUED, store.find(claimed.getId()).orElseThrow().getState());
        assertTrue(Thread.interrupted());
    }

    @Test
    void blockedUnstartedReleaseFallsBackToLeaseRecoveryWithinTransitionBudget() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        stopSignal.markStopping();
        RuntimeTransitionCommitter committer = mock(RuntimeTransitionCommitter.class);
        when(committer.releaseUnstartedClaim(
                org.mockito.ArgumentMatchers.eq(claimed),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(ignored -> {
            try {
                new CountDownLatch(1).await();
                return true;
            } catch (InterruptedException deadline) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("release interrupted", deadline);
            }
        });
        RuntimeExecutor executor = new RuntimeExecutor(
                new DataPullJobRegistry(List.of(job(ignored -> AdvanceResult.succeeded()))),
                committer,
                Clock.fixed(NOW.plusSeconds(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                Duration.ofMillis(500), Duration.ofMillis(100), Duration.ofMillis(100), stopSignal
        );
        long started = System.nanoTime();

        assertFalse(executor.execute(claimed, NOW.plusSeconds(10)));

        assertTrue(Duration.ofNanos(System.nanoTime() - started)
                .compareTo(Duration.ofSeconds(1)) < 0);
        assertEquals(TaskState.RUNNING, store.find(claimed.getId()).orElseThrow().getState());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void stoppingInsideAnActiveJobSuppressesOnlyItsStateTransition() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask claimed = claimed(store, "worker-1");
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        RuntimeTransitionCommitter committer = new RuntimeTransitionCommitter(
                store, new InMemoryBackoffHoldStore());
        RuntimeExecutor executor = new RuntimeExecutor(
                new DataPullJobRegistry(List.of(job(ignored -> {
                    stopSignal.markStopping();
                    return AdvanceResult.succeeded();
                }))),
                committer,
                Clock.fixed(NOW.plusSeconds(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                stopSignal
        );

        assertFalse(executor.execute(claimed, NOW.plusSeconds(10)));

        assertEquals(TaskState.RUNNING, store.find(claimed.getId()).orElseThrow().getState());
        assertTrue(Thread.interrupted());
    }

    private DataPullJob job(java.util.function.Function<ExecutionContext, AdvanceResult> advance) {
        return new TestDataPullJob(
                OperationCode.DP04,
                "noon-partner",
                List.of(),
                advance
        );
    }

    private RuntimeExecutor deadlineExecutor(
            DataPullJob job,
            RuntimeTransitionCommitter committer
    ) {
        return new RuntimeExecutor(
                new DataPullJobRegistry(List.of(job)),
                committer,
                Clock.fixed(NOW.plusSeconds(10).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                Duration.ofMillis(500),
                Duration.ofMillis(100),
                Duration.ofMillis(100)
        );
    }
}
