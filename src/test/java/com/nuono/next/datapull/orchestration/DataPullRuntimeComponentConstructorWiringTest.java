package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nuono.next.datapull.schedule.ScheduleEpochRetention;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.infrastructure.mapper.DataPullScheduleEpochRetentionMapper;
import com.nuono.next.procurement.aliorder.Ali1688Dp10OpenApiExecutionEvidence;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderOpenApiProperties;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderManualSync;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderManualSyncController;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ManualSync;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Component;

class DataPullRuntimeComponentConstructorWiringTest {

    @Test
    void runtimeComponentsWithTestSeamsSelectTheirProductionConstructors() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local-db",
                        DataPullExecutionMode.PROPERTY + "=RUNTIME"
                )
                .withBean(DataPullScheduleEpochRetentionMapper.class,
                        () -> mock(DataPullScheduleEpochRetentionMapper.class))
                .withBean(Ali1688HistoricalOrderOpenApiProperties.class,
                        Ali1688HistoricalOrderOpenApiProperties::new)
                .withBean(DataPullRuntimeProperties.class, DataPullRuntimeProperties::new)
                .withBean(Ali1688HistoricalOrderMapper.class,
                        () -> mock(Ali1688HistoricalOrderMapper.class))
                .withBean(DataPullTaskStore.class, () -> mock(DataPullTaskStore.class))
                .withBean(BusinessAccessResolver.class, () -> mock(BusinessAccessResolver.class))
                .withUserConfiguration(RuntimeComponents.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ScheduleEpochRetention.class);
                    assertThat(context)
                            .hasSingleBean(Ali1688Dp10OpenApiExecutionEvidence.class);
                    assertThat(context).hasSingleBean(Ali1688HistoricalOrderManualSync.class);
                    assertThat(context).hasSingleBean(Ali1688Dp10ManualSync.class);
                    assertThat(context)
                            .hasSingleBean(Ali1688HistoricalOrderManualSyncController.class);
                });
    }

    @Test
    void everyRuntimeComponentWithMultipleConstructorsSelectsExactlyOne() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-db");
        environment.setProperty(DataPullExecutionMode.PROPERTY, DataPullExecutionMode.RUNTIME.name());
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(
                ConditionalOnDataPullExecutionMode.class));
        List<String> violations = new ArrayList<>();

        scanner.findCandidateComponents("com.nuono.next").forEach(definition -> {
            Class<?> component = load(definition.getBeanClassName());
            ConditionalOnDataPullExecutionMode executionMode =
                    AnnotatedElementUtils.findMergedAnnotation(
                            component, ConditionalOnDataPullExecutionMode.class);
            if (executionMode == null || executionMode.value() != DataPullExecutionMode.RUNTIME) {
                return;
            }
            if (component.getDeclaredConstructors().length < 2) return;
            long selected = Arrays.stream(component.getDeclaredConstructors())
                    .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                    .count();
            if (selected != 1) {
                violations.add(component.getName() + " has " + selected
                        + " @Autowired constructors among "
                        + component.getDeclaredConstructors().length);
            }
        });

        assertThat(violations).isEmpty();
    }

    @Test
    void runtimeComponentsHaveNoDependencyThatOnlyLegacyComponentsProvide() {
        Set<Class<?>> legacy = modeComponents(DataPullExecutionMode.LEGACY, false);
        Set<Class<?>> runtime = modeComponents(DataPullExecutionMode.RUNTIME, true);
        List<String> violations = new ArrayList<>();

        for (Class<?> consumer : runtime) {
            Arrays.stream(consumer.getDeclaredConstructors())
                    .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                    .filter(parameter -> hasProvider(parameter, legacy))
                    .filter(parameter -> !hasProvider(parameter, runtime))
                    .forEach(parameter -> violations.add(
                            consumer.getName() + " requires legacy-only " + parameter.getName()));
        }

        assertThat(violations).isEmpty();
    }

    private static Set<Class<?>> modeComponents(
            DataPullExecutionMode mode,
            boolean includeUnconditionalComponents
    ) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-db");
        environment.setProperty(DataPullExecutionMode.PROPERTY, mode.name());
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(
                includeUnconditionalComponents
                        ? Component.class
                        : ConditionalOnDataPullExecutionMode.class,
                true,
                true
        ));
        Set<Class<?>> components = new LinkedHashSet<>();
        for (String basePackage : List.of(
                "com.nuono.next.datapull",
                "com.nuono.next.noonpull",
                "com.nuono.next.productpublicdetail",
                "com.nuono.next.sales",
                "com.nuono.next.competitoranalysis",
                "com.nuono.next.procurement.aliorder"
        )) {
            scanner.findCandidateComponents(basePackage).forEach(definition ->
                    addProductionComponent(components, definition.getBeanClassName()));
        }
        return components;
    }

    private static void addProductionComponent(Set<Class<?>> components, String className) {
        Class<?> component = load(className);
        String location = component.getProtectionDomain().getCodeSource()
                .getLocation().toExternalForm();
        if (location.endsWith("/target/classes/")) {
            components.add(component);
        }
    }

    private static boolean hasProvider(Class<?> dependency, Set<Class<?>> components) {
        return components.stream().anyMatch(dependency::isAssignableFrom);
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Cannot load Spring component " + className, failure);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            ScheduleEpochRetention.class,
            Ali1688Dp10OpenApiExecutionEvidence.class,
            Ali1688Dp10ManualSync.class,
            Ali1688HistoricalOrderManualSyncController.class
    })
    static class RuntimeComponents {
    }
}
