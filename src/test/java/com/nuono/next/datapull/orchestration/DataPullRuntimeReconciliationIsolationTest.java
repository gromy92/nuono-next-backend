package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderTestFixtures;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataPullRuntimeReconciliationIsolationTest {

    private static final Instant NOW = Instant.parse("2026-08-03T04:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void partialReconciliationStillDispatchesExistingDueTaskAndRunsMaintenance() {
        DataPullRuntimeTechnicalHealth technicalHealth =
                new DataPullRuntimeTechnicalHealth();
        ScheduleReconciliationOutcome partial = new ScheduleReconciliationOutcome(
                List.of(
                        ScheduleReconciliationOutcome.OperationOutcome.succeeded(
                                OperationCode.DP02, 0
                        ),
                        ScheduleReconciliationOutcome.OperationOutcome.failed(
                                OperationCode.DP04
                        )
                ),
                List.of()
        );
        AtomicInteger maintenanceRuns = new AtomicInteger();
        AtomicBoolean firstDispatch = new AtomicBoolean(true);
        DataPullTask existingDueTask = new DataPullTask();
        existingDueTask.setId(500L);
        RuntimeExecutor runtimeExecutor = mock(RuntimeExecutor.class);
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                now -> {
                    technicalHealth.observe(partial, now);
                    return partial.getReconciledTaskCount();
                },
                (now, limit, leaseDuration, leaderLease) ->
                        firstDispatch.getAndSet(false)
                                ? List.of(existingDueTask)
                                : List.of(),
                runtimeExecutor,
                List.of(ignored -> maintenanceRuns.incrementAndGet()),
                Runnable::run,
                CLOCK,
                DataPullRuntimeLeaderTestFixtures.alwaysLeader(
                        "dp:isolation",
                        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
                ),
                Duration.ofMinutes(5),
                1,
                1
        );

        DataPullRuntimeTickResult result = coordinator.tick();

        assertEquals(0, result.getReconciledTasks());
        assertEquals(1, result.getClaimedTasks());
        assertEquals(1, maintenanceRuns.get());
        assertFalse(technicalHealth.snapshot().isHealthy());
        verify(runtimeExecutor).execute(any(DataPullTask.class), any(LocalDateTime.class));
    }
}
