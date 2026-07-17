package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class NoonAuthRecoverySchedulerTest {

    @Test
    void ownsAnIdempotentPrivateSchedulerLifecycle() {
        NoonAuthRecoveryWorker worker = mock(NoonAuthRecoveryWorker.class);
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setSchedulerInitialDelayMs(1_234L);
        properties.setSchedulerFixedDelayMs(2_345L);
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T06:00:00Z"), ZoneOffset.UTC);
        ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
        @SuppressWarnings("rawtypes")
        ScheduledFuture future = mock(ScheduledFuture.class);
        when(taskScheduler.scheduleWithFixedDelay(
                any(Runnable.class),
                eq(Date.from(clock.instant().plusMillis(1_234L))),
                eq(2_345L)
        )).thenReturn(future);
        NoonAuthRecoveryScheduler scheduler = new NoonAuthRecoveryScheduler(
                worker,
                properties,
                clock,
                () -> taskScheduler
        );

        scheduler.start();
        scheduler.start();

        assertTrue(scheduler.isRunning());
        assertTrue(scheduler.isAutoStartup());
        assertEquals(Integer.MAX_VALUE, scheduler.getPhase());
        verify(worker).validateEnabledConfiguration();
        verify(taskScheduler).setPoolSize(1);
        verify(taskScheduler).setThreadNamePrefix(NoonAuthRecoveryScheduler.THREAD_NAME_PREFIX);
        verify(taskScheduler).setRemoveOnCancelPolicy(true);
        verify(taskScheduler).initialize();
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).scheduleWithFixedDelay(
                runnable.capture(),
                eq(Date.from(clock.instant().plusMillis(1_234L))),
                eq(2_345L)
        );
        runnable.getValue().run();
        verify(worker).runOnce();
        when(worker.runOnce()).thenThrow(new IllegalStateException("must not cancel future ticks"));
        assertDoesNotThrow(() -> runnable.getValue().run());
        verify(worker, times(2)).runOnce();

        scheduler.stop();
        scheduler.stop();

        assertFalse(scheduler.isRunning());
        verify(future, times(1)).cancel(true);
        verify(taskScheduler, times(1)).shutdown();
    }

    @Test
    void workerNoLongerUsesTheApplicationsSharedScheduledExecutor() {
        long scheduledMethods = java.util.Arrays.stream(NoonAuthRecoveryWorker.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .count();

        assertEquals(0L, scheduledMethods);
        assertFalse(org.springframework.scheduling.TaskScheduler.class
                .isAssignableFrom(NoonAuthRecoveryScheduler.class));
    }

    @Test
    void productionConstructorIsExplicitlySelectedForSpringWiring() throws Exception {
        assertTrue(NoonAuthRecoveryScheduler.class.getConstructor(
                NoonAuthRecoveryWorker.class,
                NoonAuthRecoveryProperties.class,
                TaskSchedulerBuilder.class
        ).isAnnotationPresent(Autowired.class));
    }
}
