package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderTestFixtures;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class DataPullRuntimeIndependentPhaseBudgetTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T04:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void eachPeriodicPhaseReceivesItsOwnDeadlineBudget() {
        Duration phaseBudget = Duration.ofMillis(250);
        AtomicInteger reconciled = new AtomicInteger();
        AtomicInteger dispatched = new AtomicInteger();
        AtomicInteger maintained = new AtomicInteger();
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        RuntimeExecutor executor = new RuntimeExecutor(
                new DataPullJobRegistry(List.of()),
                new InMemoryDataPullTaskStore(),
                CLOCK
        );
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                ignored -> {
                    pause(Duration.ofMillis(120));
                    return reconciled.incrementAndGet();
                },
                (now, maximum, lease, leader) -> {
                    pause(Duration.ofMillis(120));
                    dispatched.incrementAndGet();
                    return List.of();
                },
                executor,
                List.of(ignored -> {
                    pause(Duration.ofMillis(120));
                    maintained.incrementAndGet();
                }),
                Runnable::run,
                CLOCK,
                DataPullRuntimeLeaderTestFixtures.alwaysLeader(
                        "dp:phase-budget",
                        LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC)
                ),
                Duration.ofMinutes(5),
                1,
                1,
                stopSignal,
                phaseBudget
        );
        DataPullRuntimeProperties properties = new DataPullRuntimeProperties();
        properties.setSchedulerInitialDelayMs(60_000L);
        properties.setSchedulerFixedDelayMs(60_000L);
        DataPullRuntimeScheduler scheduler = new DataPullRuntimeScheduler(
                coordinator,
                properties,
                CLOCK,
                ThreadPoolTaskScheduler::new,
                stopSignal,
                Duration.ofSeconds(2)
        );

        long started = System.nanoTime();
        scheduler.start();
        try {
            scheduler.runSafely();
        } finally {
            scheduler.stop();
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        assertEquals(1, reconciled.get());
        assertEquals(1, dispatched.get());
        assertEquals(1, maintained.get());
        assertTrue(elapsed.compareTo(phaseBudget) > 0);
        assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) < 0);
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("phase interrupted", interrupted);
        }
    }
}
