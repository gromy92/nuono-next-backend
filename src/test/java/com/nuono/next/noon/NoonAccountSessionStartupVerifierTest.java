package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class NoonAccountSessionStartupVerifierTest {

    @Test
    void runsOneAsynchronousAuditAfterTheConfiguredStartupDelay() {
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setStartupAuditEnabled(true);
        properties.setStartupAuditDelayMs(1_234L);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T08:00:00Z"), ZoneOffset.UTC);
        ThreadPoolTaskScheduler taskScheduler = mock(ThreadPoolTaskScheduler.class);
        @SuppressWarnings("rawtypes")
        ScheduledFuture future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(runnable.capture(), eq(Date.from(clock.instant().plusMillis(1_234L)))))
                .thenReturn(future);
        AtomicBoolean audited = new AtomicBoolean();
        NoonAccountSessionStartupVerifier verifier = new NoonAccountSessionStartupVerifier(
                () -> audited.set(true), properties, clock, () -> taskScheduler
        );

        verifier.start();
        runnable.getValue().run();

        assertTrue(verifier.isRunning());
        assertTrue(audited.get());
        verify(taskScheduler).initialize();

        verifier.stop();

        assertFalse(verifier.isRunning());
        verify(future).cancel(true);
        verify(taskScheduler).shutdown();
    }
}
