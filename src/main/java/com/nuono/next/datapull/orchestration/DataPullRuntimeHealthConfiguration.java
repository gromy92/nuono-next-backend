package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.leader.DataPullRuntimeLeadership;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Actuator projection of runtime-only technical health. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class DataPullRuntimeHealthConfiguration {

    @Bean
    DataPullRuntimeTechnicalHealth dataPullRuntimeTechnicalHealth() {
        return new DataPullRuntimeTechnicalHealth();
    }

    @Bean(name = "dpRuntime")
    HealthIndicator dataPullRuntimeHealthIndicator(
            DataPullRuntimeScheduler scheduler,
            DataPullRuntimeProperties properties,
            DataPullRuntimeReleaseGate releaseGate,
            DataPullRuntimeLeadership leadership,
            DataPullRuntimeTechnicalHealth technicalHealth
    ) {
        return () -> health(
                scheduler, properties, releaseGate, leadership, technicalHealth
        );
    }

    private Health health(
            DataPullRuntimeScheduler scheduler,
            DataPullRuntimeProperties properties,
            DataPullRuntimeReleaseGate releaseGate,
            DataPullRuntimeLeadership leadership,
            DataPullRuntimeTechnicalHealth technicalHealth
    ) {
        try {
            properties.validate();
            releaseGate.requireReady();
            DataPullRuntimeTechnicalHealth.Snapshot snapshot = technicalHealth.snapshot();
            boolean running = scheduler.isRunning() && leadership.isLeader();
            Health.Builder result = running && snapshot.isHealthy()
                    ? Health.up()
                    : Health.down();
            if (!snapshot.isHealthy()) {
                result.withDetail(
                        "scheduleReconciliationFailures",
                        actuatorDetails(snapshot)
                );
            }
            return result.build();
        } catch (RuntimeException notReady) {
            return Health.down().build();
        }
    }

    private Map<String, Object> actuatorDetails(
            DataPullRuntimeTechnicalHealth.Snapshot snapshot
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        snapshot.getFailures().forEach((operation, failure) -> details.put(
                operation,
                Map.of(
                        "code", failure.getCode(),
                        "consecutiveFailures", failure.getConsecutiveFailures(),
                        "observedAt", failure.getObservedAt().toString()
                )
        ));
        return details;
    }
}
