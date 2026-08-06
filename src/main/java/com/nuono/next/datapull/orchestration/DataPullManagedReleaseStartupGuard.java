package com.nuono.next.datapull.orchestration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Prevents a managed production slot from starting without the one daily DP runtime. */
@Component
public final class DataPullManagedReleaseStartupGuard
        implements SmartInitializingSingleton {
    private static final String MANAGED_RELEASE = "NUONO_MANAGED_DP_RELEASE";
    private static final Set<String> MARKERS = Set.of(
            "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_FILE",
            "NUONO_DP10_OPEN_API_EXECUTION_EVIDENCE_SHA256",
            "NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT",
            "NUONO_DP10_OPEN_API_EXECUTION_RUNTIME_ENV_SHA256_FILE",
            DataPullManagedReleaseProvenanceEvidence.EXPECTED_COMMIT,
            DataPullManagedReleaseProvenanceEvidence.SCHEMA_BINDING,
            DataPullManagedReleaseProvenanceEvidence.CUTOVER_BINDING
    );
    private static final Set<String> REQUIRED_PROFILES = Set.of("local-db");

    private final Environment environment;
    private final ApplicationContext applicationContext;
    private final Map<String, String> processEnvironment;

    @Autowired
    public DataPullManagedReleaseStartupGuard(
            Environment environment,
            ApplicationContext applicationContext
    ) {
        this(environment, applicationContext, System.getenv());
    }

    DataPullManagedReleaseStartupGuard(
            Environment environment,
            ApplicationContext applicationContext,
            Map<String, String> processEnvironment
    ) {
        this.environment = environment;
        this.applicationContext = applicationContext;
        this.processEnvironment = Map.copyOf(processEnvironment);
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!managedReleasePresent()) return;
        requireExactMarkers();
        if (!activeProfiles().equals(REQUIRED_PROFILES)) {
            throw blocked("PROFILE");
        }
        if (retiredRuntimeTogglePresent()) {
            throw blocked("RETIRED_RUNTIME_TOGGLE");
        }
        if (!DataPullExecutionMode.RUNTIME.name().equals(
                processEnvironment.get(DataPullExecutionMode.ENVIRONMENT_VARIABLE)
        ) || DataPullExecutionMode.resolve(environment) != DataPullExecutionMode.RUNTIME) {
            throw blocked("EXECUTION_MODE");
        }
        DataPullRuntimeProperties properties = uniqueBean(
                DataPullRuntimeProperties.class,
                "PROPERTIES"
        );
        properties.validate();
        DataSource dataSource = uniqueBean(DataSource.class, "DATA_SOURCE");
        if (!(dataSource instanceof DataPullDeadlineAwareDataSource)
                || !((DataPullDeadlineAwareDataSource) dataSource).hasUnwrappableTarget()) {
            throw blocked("DATA_SOURCE_DEADLINE");
        }
        uniqueBean(DataPullRuntimeReleaseGate.class, "RELEASE_GATE").requireReady();
        uniqueBean(DataPullRuntimeScheduler.class, "SCHEDULER");
    }

    private boolean managedReleasePresent() {
        return hasText(processEnvironment.get(MANAGED_RELEASE))
                || hasText(environment.getProperty(MANAGED_RELEASE))
                || MARKERS.stream().anyMatch((name) ->
                        hasText(processEnvironment.get(name))
                                || hasText(environment.getProperty(name)));
    }

    private void requireExactMarkers() {
        if (!"1".equals(processEnvironment.get(MANAGED_RELEASE))
                || !"1".equals(environment.getProperty(MANAGED_RELEASE))) {
            throw blocked("MANAGED_MARKER");
        }
        for (String name : MARKERS) {
            String processValue = processEnvironment.get(name);
            if (!hasText(processValue)
                    || !processValue.equals(environment.getProperty(name))) {
                throw blocked("ENVIRONMENT_BINDING");
            }
        }
    }

    private Set<String> activeProfiles() {
        return new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
    }

    private boolean retiredRuntimeTogglePresent() {
        return hasText(processEnvironment.get(
                DataPullExecutionMode.RETIRED_ENABLED_ENVIRONMENT_VARIABLE
        )) || hasText(environment.getProperty(DataPullExecutionMode.RETIRED_ENABLED_PROPERTY))
                || hasText(environment.getProperty(
                        DataPullExecutionMode.RETIRED_ENABLED_ENVIRONMENT_VARIABLE
                ));
    }

    private <T> T uniqueBean(Class<T> type, String code) {
        Map<String, T> beans = applicationContext.getBeansOfType(type);
        if (beans.size() != 1) throw blocked(code);
        return beans.values().iterator().next();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private IllegalStateException blocked(String code) {
        return new IllegalStateException("DP_MANAGED_RUNTIME_STARTUP_BLOCKED:" + code);
    }
}
