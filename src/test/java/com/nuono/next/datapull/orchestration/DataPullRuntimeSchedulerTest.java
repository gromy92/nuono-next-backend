package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class DataPullRuntimeSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T04:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void startAttemptsLeadershipBeforeTheDelayedPeriodicTick() {
        Fixture fixture = new Fixture();

        fixture.scheduler.start();

        assertEquals(1, fixture.startupAcquireCalls.get());
        assertEquals(0, fixture.tickCalls.get());
        assertTrue(fixture.periodicTask.get() != null);
    }

    @Test
    void failedStartupAcquireStaysRunningForPeriodicRetry() {
        Fixture fixture = new Fixture();
        fixture.startupBehavior.set(() -> {
            throw new IllegalStateException("database unavailable");
        });

        fixture.scheduler.start();
        fixture.periodicTask.get().run();

        assertTrue(fixture.scheduler.isRunning());
        assertEquals(1, fixture.startupAcquireCalls.get());
        assertEquals(1, fixture.tickCalls.get());
    }

    @Test
    void coalescesCompletionSignalsAndRunsImmediateDispatchOffTheWorker() {
        Fixture fixture = new Fixture();
        fixture.scheduler.start();

        fixture.scheduler.requestImmediateDispatch();
        fixture.scheduler.requestImmediateDispatch();

        assertEquals(1, fixture.immediateTasks.size());
        fixture.immediateTasks.get(0).run();
        assertEquals(1, fixture.dispatchCalls.get());

        fixture.scheduler.requestImmediateDispatch();
        assertEquals(2, fixture.immediateTasks.size());
    }

    @Test
    void signalArrivingDuringDispatchSchedulesOneFollowingPumpWithoutRecursion() {
        Fixture fixture = new Fixture();
        fixture.dispatchBehavior.set(fixture.scheduler::requestImmediateDispatch);
        fixture.scheduler.start();
        fixture.scheduler.requestImmediateDispatch();

        fixture.immediateTasks.get(0).run();

        assertEquals(2, fixture.immediateTasks.size());
        assertEquals(1, fixture.dispatchCalls.get());
    }

    @Test
    void dispatchFailureResetsTheDebounceAndPeriodicFallbackRemainsScheduled() {
        Fixture fixture = new Fixture();
        fixture.dispatchBehavior.set(() -> {
            throw new IllegalStateException("db unavailable");
        });
        fixture.scheduler.start();
        fixture.scheduler.requestImmediateDispatch();

        fixture.immediateTasks.get(0).run();
        fixture.scheduler.requestImmediateDispatch();

        assertEquals(2, fixture.immediateTasks.size());
        assertTrue(fixture.periodicTask.get() != null);
        assertEquals(1, fixture.dispatchCalls.get());

        fixture.periodicTask.get().run();
        assertEquals(1, fixture.tickCalls.get());
    }

    @Test
    void stopRejectsLaterCompletionSignalsAndShutsDownThePrivateScheduler() {
        Fixture fixture = new Fixture();
        fixture.scheduler.start();
        fixture.scheduler.requestImmediateDispatch();
        int scheduledBeforeStop = fixture.immediateTasks.size();
        Runnable pending = fixture.immediateTasks.get(0);

        fixture.scheduler.stop();
        fixture.scheduler.requestImmediateDispatch();
        pending.run();
        fixture.periodicTask.get().run();

        assertEquals(scheduledBeforeStop, fixture.immediateTasks.size());
        assertEquals(0, fixture.dispatchCalls.get());
        assertEquals(0, fixture.tickCalls.get());
        verify(fixture.periodicFuture).cancel(true);
        verify(fixture.taskScheduler).shutdown();
    }

    @Test
    void expiredPeriodicTickReleasesTheSingleSchedulerForItsNextTick() {
        Fixture fixture = new Fixture(Duration.ofMillis(100));
        fixture.tickBehavior.set(() -> awaitForever("deadline"));
        fixture.scheduler.start();
        long started = System.nanoTime();

        fixture.periodicTask.get().run();

        assertTrue(Duration.ofNanos(System.nanoTime() - started)
                .compareTo(Duration.ofSeconds(1)) < 0);
        assertEquals(1, fixture.tickCalls.get());
        assertTrue(!Thread.currentThread().isInterrupted());
        fixture.tickBehavior.set(() -> { });
        fixture.periodicTask.get().run();
        assertEquals(2, fixture.tickCalls.get());
    }

    @Test
    void shutdownInterruptIsNotConsumedByAnExpiringSchedulerPhase() throws Exception {
        Fixture fixture = new Fixture(Duration.ofMillis(100));
        CountDownLatch entered = new CountDownLatch(1);
        fixture.tickBehavior.set(() -> {
            entered.countDown();
            awaitForever("shutdown");
        });
        fixture.scheduler.start();
        Thread stopper = new Thread(() -> {
            await(entered);
            fixture.scheduler.stop();
        });
        stopper.start();

        fixture.periodicTask.get().run();

        assertTrue(Thread.interrupted());
        stopper.join(1_000L);
        assertTrue(!stopper.isAlive());
    }

    @Test
    void stopTimesOutWithoutReportingQuiescenceWhenAnActionIgnoresCancellation()
            throws Exception {
        Fixture fixture = new Fixture(Duration.ofSeconds(5), Duration.ofMillis(100));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fixture.tickBehavior.set(() -> {
            entered.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    release.await();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        });
        fixture.scheduler.start();
        Thread tick = new Thread(fixture.periodicTask.get());
        tick.start();
        await(entered);
        AtomicBoolean callback = new AtomicBoolean();

        assertThrows(IllegalStateException.class, () -> fixture.scheduler.stop(
                () -> callback.set(true)
        ));

        assertTrue(!callback.get());
        assertEquals(1, fixture.stopSignal.activeDeadlineCount());
        release.countDown();
        tick.join(1_000L);
        assertTrue(!tick.isAlive());
        assertEquals(0, fixture.stopSignal.activeDeadlineCount());
    }

    private static void awaitForever(String phase) {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(phase + " interrupted", interrupted);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test coordination interrupted", interrupted);
        }
    }

    private static final class Fixture {
        private final ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
        private final ScheduledFuture<?> periodicFuture = mock(ScheduledFuture.class);
        private final AtomicReference<Runnable> periodicTask = new AtomicReference<>();
        private final List<Runnable> immediateTasks = new ArrayList<>();
        private final AtomicInteger tickCalls = new AtomicInteger();
        private final AtomicInteger dispatchCalls = new AtomicInteger();
        private final AtomicInteger startupAcquireCalls = new AtomicInteger();
        private final AtomicReference<Runnable> dispatchBehavior = new AtomicReference<>(() -> { });
        private final AtomicReference<Runnable> tickBehavior = new AtomicReference<>(() -> { });
        private final AtomicReference<Runnable> startupBehavior = new AtomicReference<>(() -> { });
        private final DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        private final DataPullRuntimeScheduler scheduler;

        private Fixture() {
            this(Duration.ofSeconds(DataPullRuntimeProperties.SCHEDULER_PHASE_BUDGET_SECONDS));
        }

        private Fixture(Duration phaseBudget) {
            this(phaseBudget, Duration.ofSeconds(15));
        }

        private Fixture(Duration phaseBudget, Duration stopBudget) {
            when(taskScheduler.scheduleWithFixedDelay(
                    any(Runnable.class),
                    any(Date.class),
                    anyLong()
            )).thenAnswer(invocation -> {
                periodicTask.set(invocation.getArgument(0));
                return periodicFuture;
            });
            when(taskScheduler.schedule(any(Runnable.class), any(Date.class)))
                    .thenAnswer(invocation -> {
                        immediateTasks.add(invocation.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });
            scheduler = new DataPullRuntimeScheduler(
                    () -> {
                        tickCalls.incrementAndGet();
                        tickBehavior.get().run();
                    },
                    () -> {
                        dispatchCalls.incrementAndGet();
                        dispatchBehavior.get().run();
                    },
                    ignored -> { },
                    new DataPullRuntimeProperties(),
                    CLOCK,
                    () -> taskScheduler,
                    () -> {
                        startupAcquireCalls.incrementAndGet();
                        startupBehavior.get().run();
                    },
                    stopSignal,
                    phaseBudget,
                    stopBudget
            );
        }
    }
}
