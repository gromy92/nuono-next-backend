package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.leader.DataPullRuntimeLeaderTestFixtures;
import com.nuono.next.datapull.leader.DataPullRuntimeLeadership;
import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

@ExtendWith(MockitoExtension.class)
class DataPullRuntimeCoordinatorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T04:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private DataPullRuntimeReconciler reconcile;
    @Mock
    private DataPullRuntimeCoordinator.DispatchAction dispatch;
    @Mock
    private RuntimeExecutor runtimeExecutor;

    @Test
    void neverClaimsOrSubmitsBeyondConfiguredWorkerCapacity() {
        when(reconcile.reconcileAt(CLOCK.instant())).thenReturn(0);
        whenDispatching().thenReturn(List.of(task(1L), task(2L), task(3L)));
        ManualExecutor workers = new ManualExecutor();
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                reconcile,
                dispatch,
                runtimeExecutor,
                List.of(),
                workers,
                CLOCK,
                leadership(),
                Duration.ofMinutes(5),
                10,
                3
        );

        DataPullRuntimeTickResult result = coordinator.tick();
        assertEquals(3, result.getClaimedTasks());
        assertEquals(3, result.getInFlightTasks());
        assertEquals(3, workers.size());
        assertEquals(0, coordinator.dispatchAvailable());
        verify(dispatch, times(1)).dispatchDue(
                any(LocalDateTime.class), eq(3), any(Duration.class),
                any(DataPullRuntimeLeaderLease.class)
        );
    }

    @Test
    void rejectedWorkerNeverRunsAnExternalAdvanceOnTheSchedulerThread() {
        DataPullTask claimed = new DataPullTask();
        claimed.setId(71L);
        when(reconcile.reconcileAt(CLOCK.instant())).thenReturn(0);
        whenDispatching().thenReturn(List.of(claimed)).thenReturn(List.of());
        Executor rejectingExecutor = ignored -> {
            throw new RejectedExecutionException("shutdown");
        };
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                reconcile,
                dispatch,
                runtimeExecutor,
                List.of(),
                rejectingExecutor,
                CLOCK,
                leadership(),
                Duration.ofMinutes(5),
                1,
                1
        );

        DataPullRuntimeTickResult result = coordinator.tick();

        assertEquals(1, result.getClaimedTasks());
        assertEquals(0, result.getInFlightTasks());
        verify(runtimeExecutor, never()).execute(any(), any());
        verify(dispatch).releaseUnstartedClaim(eq(claimed), eq(CLOCK.instant()));
    }

    @Test
    void phaseExpiryAfterClaimStillSubmitsEveryReturnedLease() {
        when(reconcile.reconcileAt(CLOCK.instant())).thenReturn(0);
        whenDispatching().thenAnswer(ignored -> {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException deadline) {
                // The dispatcher has already committed both claim CAS operations.
            }
            return List.of(task(31L), task(32L));
        });
        ManualExecutor workers = new ManualExecutor();
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                reconcile, dispatch, runtimeExecutor, List.of(), workers, CLOCK,
                leadership(), Duration.ofMinutes(5), 2, 2, stopSignal,
                Duration.ofMillis(100)
        );

        assertThrows(IllegalStateException.class, coordinator::tick);

        assertEquals(2, workers.size());
    }

    @Test
    void stopAfterClaimsSubmitsEveryLeaseOnlyToStopAwareWorkers() {
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        when(reconcile.reconcileAt(CLOCK.instant())).thenReturn(0);
        whenDispatching().thenAnswer(ignored -> {
            stopSignal.markStopping();
            return List.of(task(41L), task(42L));
        });
        ManualExecutor workers = new ManualExecutor();
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                reconcile, dispatch, runtimeExecutor, List.of(), workers, CLOCK,
                leadership(), Duration.ofMinutes(5), 2, 2, stopSignal
        );

        DataPullRuntimeTickResult result = coordinator.tick();

        assertEquals(2, result.getClaimedTasks());
        assertEquals(2, result.getInFlightTasks());
        assertEquals(2, workers.size());
        workers.runNext();
        workers.runNext();
        assertEquals(0, stopSignal.activeWorkerCount());
        verify(runtimeExecutor, times(2)).execute(any(), any());
        verify(dispatch, never()).releaseUnstartedClaim(any(), any());
        assertEquals(true, Thread.interrupted());
    }

    @Test
    void queuedWorkerChecksStopAgainBeforeStartingItsAdvance() {
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        when(reconcile.reconcileAt(CLOCK.instant())).thenReturn(0);
        whenDispatching().thenReturn(List.of(task(43L)));
        ManualExecutor workers = new ManualExecutor();
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                reconcile, dispatch, runtimeExecutor, List.of(), workers, CLOCK,
                leadership(), Duration.ofMinutes(5), 1, 1, stopSignal
        );
        coordinator.tick();
        stopSignal.markStopping();
        workers.runNext();

        assertEquals(0, coordinator.dispatchAvailable());
        verify(runtimeExecutor).execute(
                org.mockito.ArgumentMatchers.argThat(task -> task.getId() == 43L),
                any(LocalDateTime.class)
        );
        verify(dispatch, never()).releaseUnstartedClaim(any(), any());
    }

    @Test
    void workerCompletionContinuouslyRefillsCapacityWithoutRepeatingReconciliation() {
        int taskCount = 100;
        AtomicInteger nextTask = new AtomicInteger();
        when(reconcile.reconcileAt(CLOCK.instant())).thenReturn(0);
        whenDispatching().thenAnswer(ignored -> {
            int taskId = nextTask.incrementAndGet();
            return taskId <= taskCount ? List.of(task(taskId)) : List.of();
        });
        ManualExecutor workers = new ManualExecutor();
        Deque<Runnable> immediateDispatches = new ArrayDeque<>();
        DataPullRuntimeCoordinator coordinator = coordinator(workers);
        coordinator.installImmediateDispatchTrigger(
                () -> immediateDispatches.addLast(coordinator::dispatchAvailable)
        );

        DataPullRuntimeTickResult first = coordinator.tick();
        assertEquals(1, first.getClaimedTasks());

        for (int index = 0; index < taskCount; index++) {
            workers.runNext();
            immediateDispatches.removeFirst().run();
        }

        assertEquals(0, workers.size());
        assertEquals(0, immediateDispatches.size());
        verify(runtimeExecutor, times(taskCount)).execute(any(), any(LocalDateTime.class));
        verify(reconcile, times(1)).reconcileAt(CLOCK.instant());
    }

    @Test
    void failedAdvanceStillReleasesCapacityAndSignalsARefill() {
        DataPullTask claimed = task(81L);
        when(reconcile.reconcileAt(CLOCK.instant())).thenReturn(0);
        whenDispatching().thenReturn(List.of(claimed)).thenReturn(List.of());
        doThrow(new IllegalStateException("boom"))
                .when(runtimeExecutor).execute(any(), any(LocalDateTime.class));
        ManualExecutor workers = new ManualExecutor();
        AtomicInteger refillSignals = new AtomicInteger();
        DataPullRuntimeCoordinator coordinator = coordinator(workers);
        coordinator.installImmediateDispatchTrigger(refillSignals::incrementAndGet);

        coordinator.tick();
        workers.runNext();

        assertEquals(1, refillSignals.get());
        assertEquals(0, coordinator.dispatchAvailable());
        verify(dispatch, never()).releaseUnstartedClaim(any(), any());
    }

    @Test
    void brokenImmediateTriggerCannotEscapeTheWorkerOrDisablePeriodicFallback() {
        DataPullTask claimed = task(91L);
        when(reconcile.reconcileAt(CLOCK.instant())).thenReturn(0);
        whenDispatching().thenReturn(List.of(claimed)).thenReturn(List.of());
        ManualExecutor workers = new ManualExecutor();
        DataPullRuntimeCoordinator coordinator = coordinator(workers);
        coordinator.installImmediateDispatchTrigger(() -> {
            throw new IllegalStateException("scheduler stopped");
        });

        coordinator.tick();
        workers.runNext();

        assertEquals(0, coordinator.tick().getClaimedTasks());
        verify(reconcile, times(2)).reconcileAt(CLOCK.instant());
    }

    private DataPullRuntimeCoordinator coordinator(Executor executor) {
        return new DataPullRuntimeCoordinator(
                reconcile,
                dispatch,
                runtimeExecutor,
                List.of(),
                executor,
                CLOCK,
                leadership(),
                Duration.ofMinutes(5),
                1,
                1
        );
    }

    private DataPullTask task(long id) {
        DataPullTask task = new DataPullTask();
        task.setId(id);
        return task;
    }

    private DataPullRuntimeLeadership leadership() {
        return DataPullRuntimeLeaderTestFixtures.alwaysLeader(
                "dp:test", LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC)
        );
    }

    private OngoingStubbing<List<DataPullTask>> whenDispatching() {
        return when(dispatch.dispatchDue(
                any(LocalDateTime.class), anyInt(), any(Duration.class),
                any(DataPullRuntimeLeaderLease.class)
        ));
    }

    private static final class ManualExecutor implements Executor {
        private final Deque<Runnable> commands = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            commands.addLast(command);
        }

        void runNext() {
            commands.removeFirst().run();
        }

        int size() {
            return commands.size();
        }
    }
}
