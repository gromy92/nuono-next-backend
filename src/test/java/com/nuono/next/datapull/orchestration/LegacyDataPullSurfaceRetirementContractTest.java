package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.permission.access.BusinessCapability;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LegacyDataPullSurfaceRetirementContractTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path TEST_JAVA = Path.of("src", "test", "java");

    @Test
    void readinessCompletenessAndAdministrativeSyncModelsAreAbsent() throws IOException {
        assertThat(javaFiles(MAIN_JAVA.resolve("com/nuono/next/nooncompleteness"))).isEmpty();
        assertThat(javaFiles(MAIN_JAVA.resolve("com/nuono/next/noonsync"))).isEmpty();
        assertThat(MAIN_JAVA.resolve(
                "com/nuono/next/infrastructure/mapper/NoonDataCompletenessMapper.java"
        )).doesNotExist();
        assertThat(MAIN_JAVA.resolve(
                "com/nuono/next/noonpull/NoonBusinessReadinessService.java"
        )).doesNotExist();
        assertThat(MAIN_JAVA.resolve(
                "com/nuono/next/noonpull/NoonPullSmokeController.java"
        )).doesNotExist();
    }

    @Test
    void legacySchedulerConfigurationKeysAreAbsent() throws IOException {
        String application = Files.readString(Path.of("src", "main", "resources", "application.yml"));

        assertThat(application).doesNotContain(
                "NUONO_NOON_PULL_SCHEDULER_ENABLED",
                "NUONO_PROCUREMENT_ALI1688_HISTORICAL_ORDER_SCHEDULER_ENABLED",
                "NUONO_COMPETITOR_ANALYSIS_MONITOR_SCHEDULER_ENABLED",
                "NUONO_PRODUCT_PUBLIC_DETAIL_SCHEDULER_ENABLED"
        );
    }

    @Test
    void fakeAli1688ProviderIsAvailableToTestsButAbsentFromProductionSources() {
        Path relative = Path.of(
                "com/nuono/next/procurement/aliorder/FakeAli1688HistoricalOrderProvider.java"
        );

        assertThat(MAIN_JAVA.resolve(relative)).doesNotExist();
        assertThat(TEST_JAVA.resolve(relative)).exists();
    }

    @Test
    void retiredSystemReportCapabilityAndRoutesAreAbsent() throws IOException {
        Set<String> capabilities = Arrays.stream(BusinessCapability.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        String source = Files.readString(MAIN_JAVA.resolve(
                "com/nuono/next/permission/access/BusinessCapability.java"
        ));

        assertThat(capabilities).doesNotContain("SYSTEM_REPORTS");
        assertThat(source).doesNotContain("/system-reports", "/noon-call");
    }

    @Test
    void ali1688BusinessMapperDoesNotOwnLegacySyncTaskPersistence() {
        Set<String> methods = Arrays.stream(Ali1688HistoricalOrderMapper.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(methods).doesNotContain(
                "nextSyncTaskId",
                "listScheduledOpenApiAuthorizations",
                "insertSyncTask",
                "selectLatestResumableTask",
                "updateSyncTaskCheckpoint",
                "markSyncTaskSuccess",
                "markSyncTaskFailed"
        );
    }

    private Set<Path> javaFiles(Path root) throws IOException {
        if (Files.notExists(root)) {
            return Set.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toSet());
        }
    }
}
