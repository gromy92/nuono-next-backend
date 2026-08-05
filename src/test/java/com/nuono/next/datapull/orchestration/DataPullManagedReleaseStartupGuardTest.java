package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class DataPullManagedReleaseStartupGuardTest {

    @Test
    void managedReleaseCannotBypassRuntimeWithProfileOrExecutionModeOverrides() {
        try (GenericApplicationContext context = readyContext()) {
            MockEnvironment wrongProfile = environment("local", DataPullExecutionMode.RUNTIME);
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(wrongProfile, context, markers()).afterSingletonsInstantiated()
            );

            MockEnvironment legacy = environment("local-db", DataPullExecutionMode.LEGACY);
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(legacy, context, markers()).afterSingletonsInstantiated()
            );
        }
    }

    @Test
    void managedReleaseRejectsRetiredRuntimeToggle() {
        try (GenericApplicationContext context = readyContext()) {
            MockEnvironment retiredProperty = environment(
                    "local-db",
                    DataPullExecutionMode.RUNTIME
            );
            retiredProperty.setProperty(DataPullExecutionMode.RETIRED_ENABLED_PROPERTY, "true");
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(retiredProperty, context, markers()).afterSingletonsInstantiated()
            );

            Map<String, String> retiredEnvironment = new LinkedHashMap<>(markers());
            retiredEnvironment.put(
                    DataPullExecutionMode.RETIRED_ENABLED_ENVIRONMENT_VARIABLE,
                    "true"
            );
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(
                            environment("local-db", DataPullExecutionMode.RUNTIME),
                            context,
                            retiredEnvironment
                    ).afterSingletonsInstantiated()
            );
        }
    }

    @Test
    void managedReleaseRequiresExactProcessMarkerBindingAndAllRuntimeBeans() {
        try (GenericApplicationContext context = readyContext()) {
            MockEnvironment environment = environment(
                    "local-db",
                    DataPullExecutionMode.RUNTIME
            );
            Map<String, String> incomplete = new LinkedHashMap<>(markers());
            incomplete.remove("NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE");
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(environment, context, incomplete).afterSingletonsInstantiated()
            );
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(environment, context, Map.of()).afterSingletonsInstantiated()
            );
            Map<String, String> drifted = new LinkedHashMap<>(markers());
            drifted.put(
                    "NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT",
                    "d".repeat(40)
            );
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(environment, context, drifted).afterSingletonsInstantiated()
            );
            assertDoesNotThrow(
                    () -> guard(environment, context, markers()).afterSingletonsInstantiated()
            );
        }

        try (GenericApplicationContext rawDataSource = readyContext(false)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(
                            environment("local-db", DataPullExecutionMode.RUNTIME),
                            rawDataSource,
                            markers()
                    ).afterSingletonsInstantiated()
            );
        }

        try (GenericApplicationContext missingScheduler = new GenericApplicationContext()) {
            DataPullRuntimeProperties properties = new DataPullRuntimeProperties();
            missingScheduler.registerBean(DataPullRuntimeProperties.class, () -> properties);
            missingScheduler.registerBean(
                    DataPullRuntimeReleaseGate.class,
                    this::readyGate
            );
            missingScheduler.refresh();
            assertThrows(
                    IllegalStateException.class,
                    () -> guard(
                            environment("local-db", DataPullExecutionMode.RUNTIME),
                            missingScheduler,
                            markers()
                    ).afterSingletonsInstantiated()
            );
        }
    }

    @Test
    void unmanagedLocalApplicationDoesNotRequireDpRuntimeBeans() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            MockEnvironment local = new MockEnvironment();
            local.setActiveProfiles("local");
            local.setProperty(
                    DataPullExecutionMode.PROPERTY,
                    DataPullExecutionMode.LEGACY.name()
            );
            assertDoesNotThrow(() -> guard(
                    local,
                    context,
                    Map.of()
            ).afterSingletonsInstantiated());
        }
    }

    private DataPullManagedReleaseStartupGuard guard(
            MockEnvironment environment,
            GenericApplicationContext context,
            Map<String, String> processEnvironment
    ) {
        return new DataPullManagedReleaseStartupGuard(
                environment,
                context,
                processEnvironment
        );
    }

    private GenericApplicationContext readyContext() {
        return readyContext(true);
    }

    private GenericApplicationContext readyContext(boolean deadlineAware) {
        GenericApplicationContext context = new GenericApplicationContext();
        DataPullRuntimeProperties properties = new DataPullRuntimeProperties();
        DataPullRuntimeScheduler scheduler = new DataPullRuntimeScheduler(
                () -> { },
                () -> { },
                ignored -> { },
                properties,
                Clock.systemUTC(),
                ThreadPoolTaskScheduler::new
        );
        context.registerBean(DataPullRuntimeProperties.class, () -> properties);
        context.registerBean(DataPullRuntimeReleaseGate.class, this::readyGate);
        context.registerBean(DataPullRuntimeScheduler.class, () -> scheduler);
        DataSource target = new HikariDataSource();
        context.registerBean(
                "dataSource",
                DataSource.class,
                () -> deadlineAware ? new DataPullDeadlineAwareDataSource(target) : target
        );
        context.refresh();
        return context;
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

    private MockEnvironment environment(String profile, DataPullExecutionMode mode) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        environment.setProperty(DataPullExecutionMode.PROPERTY, mode.name());
        markers().forEach(environment::setProperty);
        return environment;
    }

    private Map<String, String> markers() {
        Map<String, String> markers = new LinkedHashMap<>();
        markers.put("NUONO_MANAGED_DP_RELEASE", "1");
        markers.put(
                DataPullExecutionMode.ENVIRONMENT_VARIABLE,
                DataPullExecutionMode.RUNTIME.name()
        );
        markers.put("NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE", "/slot/evidence.json");
        markers.put("NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_SHA256", "a".repeat(64));
        markers.put("NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT", "c".repeat(40));
        markers.put(
                "NUONO_DP10_OPEN_API_EXECUTION_RUNTIME_ENV_SHA256_FILE",
                "/slot/runtime-env.sha256"
        );
        markers.put(
                DataPullManagedContractEvidence.FILE,
                "/slot/dp-runtime-contract-evidence.json"
        );
        markers.put(DataPullManagedContractEvidence.SHA, "b".repeat(64));
        markers.put(
                DataPullManagedContractEvidence.ENV_ATTESTATION,
                "/slot/runtime-env.sha256"
        );
        markers.put(DataPullManagedReleaseProvenanceEvidence.EXPECTED_COMMIT, "c".repeat(40));
        markers.put(DataPullManagedReleaseProvenanceEvidence.SCHEMA_BINDING, "d".repeat(64));
        markers.put(DataPullManagedReleaseProvenanceEvidence.CUTOVER_BINDING, "e".repeat(64));
        return markers;
    }
}
