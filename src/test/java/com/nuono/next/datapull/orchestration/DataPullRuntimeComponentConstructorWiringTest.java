package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nuono.next.datapull.schedule.ScheduleEpochRetention;
import com.nuono.next.infrastructure.mapper.DataPullScheduleEpochRetentionMapper;
import com.nuono.next.procurement.aliorder.Ali1688Dp10OpenApiExecutionEvidence;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderOpenApiProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.env.MockEnvironment;

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
                .withUserConfiguration(RuntimeComponents.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ScheduleEpochRetention.class);
                    assertThat(context)
                            .hasSingleBean(Ali1688Dp10OpenApiExecutionEvidence.class);
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
            Ali1688Dp10OpenApiExecutionEvidence.class
    })
    static class RuntimeComponents {
    }
}
