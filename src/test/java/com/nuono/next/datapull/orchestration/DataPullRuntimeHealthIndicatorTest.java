package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderTestFixtures;
import com.nuono.next.datapull.leader.DataPullRuntimeLeadership;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class DataPullRuntimeHealthIndicatorTest {

    @Test
    void releaseHealthIsUpOnlyWhileTheVerifiedSchedulerIsRunning() {
        DataPullRuntimeProperties properties = new DataPullRuntimeProperties();
        DataPullRuntimeLeadership leadership = DataPullRuntimeLeaderTestFixtures.alwaysLeader(
                "dp:test", LocalDateTime.of(2026, 8, 3, 4, 0)
        );
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                ignored -> 0,
                (now, limit, duration, lease) -> List.of(),
                mock(RuntimeExecutor.class),
                List.of(),
                Runnable::run,
                Clock.systemUTC(),
                leadership,
                Duration.ofMinutes(5),
                1,
                1
        );
        DataPullRuntimeScheduler scheduler = new DataPullRuntimeScheduler(
                coordinator,
                properties,
                Clock.systemUTC(),
                ThreadPoolTaskScheduler::new
        );
        DataPullRuntimeTechnicalHealth technicalHealth =
                new DataPullRuntimeTechnicalHealth();
        HealthIndicator indicator = new DataPullRuntimeHealthConfiguration()
                .dataPullRuntimeHealthIndicator(
                        scheduler, properties, readyGate(), leadership, technicalHealth
                );

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        try {
            scheduler.start();
            assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

            ScheduleReconciliationOutcome failed = outcome(true);
            technicalHealth.observe(failed, Instant.parse("2026-08-03T04:00:00Z"));
            technicalHealth.observe(failed, Instant.parse("2026-08-03T04:01:00Z"));
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
            assertThat(technicalHealth.snapshot().getFailures().get("DP04")
                    .getConsecutiveFailures()).isEqualTo(2);

            technicalHealth.observe(
                    outcome(false),
                    Instant.parse("2026-08-03T04:02:00Z")
            );
            assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
            properties.setWorkerCount(0);
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        } finally {
            scheduler.stop();
        }
    }

    private ScheduleReconciliationOutcome outcome(boolean failed) {
        ScheduleReconciliationOutcome.OperationOutcome operation = failed
                ? ScheduleReconciliationOutcome.OperationOutcome.failed(OperationCode.DP04)
                : ScheduleReconciliationOutcome.OperationOutcome.succeeded(
                        OperationCode.DP04, 0
                );
        return new ScheduleReconciliationOutcome(List.of(operation), List.of());
    }

    private DataPullRuntimeReleaseGate readyGate() {
        List<DataPullRuntimeReleaseEvidence> evidence = new ArrayList<>();
        Arrays.stream(DataPullRuntimeReleaseRequirement.values()).forEach((requirement) ->
                evidence.add(new DataPullRuntimeReleaseEvidence() {
                    @Override
                    public DataPullRuntimeReleaseRequirement requirement() {
                        return requirement;
                    }

                    @Override
                    public boolean verified() {
                        return true;
                    }
                })
        );
        return new DataPullRuntimeReleaseGate(
                new DataPullRuntimeReleaseEvidenceRegistry(evidence)
        );
    }
}
