package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class NoonPullExecutionSchedulerTest {

    @Test
    void ownsPrivateSchedulerLifecycleAndKeepsFutureTicksAfterFailure() {
        NoonPullScheduledExecutionService service = mock(NoonPullScheduledExecutionService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T03:00:00Z"), ZoneOffset.UTC);
        ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
        @SuppressWarnings("rawtypes")
        ScheduledFuture future = mock(ScheduledFuture.class);
        when(taskScheduler.scheduleWithFixedDelay(
                any(Runnable.class),
                eq(Date.from(clock.instant().plusMillis(1_234L))),
                eq(2_345L)
        )).thenReturn(future);
        NoonPullExecutionScheduler scheduler = new NoonPullExecutionScheduler(
                service,
                clock,
                1_234L,
                2_345L,
                () -> taskScheduler
        );

        scheduler.start();
        scheduler.start();

        assertTrue(scheduler.isRunning());
        verify(taskScheduler).setPoolSize(1);
        verify(taskScheduler).setThreadNamePrefix(NoonPullExecutionScheduler.THREAD_NAME_PREFIX);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).scheduleWithFixedDelay(
                runnable.capture(),
                eq(Date.from(clock.instant().plusMillis(1_234L))),
                eq(2_345L)
        );
        runnable.getValue().run();
        verify(service).runScheduledTick();
        doThrow(new IllegalStateException("must not cancel later ticks")).when(service).runScheduledTick();
        assertDoesNotThrow(() -> runnable.getValue().run());
        verify(service, times(2)).runScheduledTick();

        scheduler.stop();
        scheduler.stop();

        assertFalse(scheduler.isRunning());
        verify(future).cancel(true);
        verify(taskScheduler).shutdown();
    }

    @Test
    void noonPullWorkerNoLongerUsesSharedScheduledExecutor() {
        long scheduledMethods = java.util.Arrays.stream(NoonPullScheduledExecutionService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .count();

        assertEquals(0L, scheduledMethods);
    }
}
