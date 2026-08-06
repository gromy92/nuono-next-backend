package com.nuono.next.datapull.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.mock.env.MockEnvironment;

class LegacyDataPullAutoTriggerRetirementTest {
    private static final Path MAIN_SOURCE = Path.of("src", "main", "java");
    private static final List<String> LEGACY_AUTOMATIC_ROOTS = List.of(
            "com.nuono.next.noonpull.NoonPullExecutionScheduler",
            "com.nuono.next.productpublicdetail.ProductPublicDetailSyncScheduler",
            "com.nuono.next.sales.SalesSyncTaskScheduler",
            "com.nuono.next.competitoranalysis.CompetitorAnalysisMonitoringScheduler",
            "com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderWeeklySyncScheduler"
    );
    private static final List<String> RUNTIME_AUTOMATIC_ROOTS = List.of(
            "com.nuono.next.datapull.orchestration.DataPullRuntimeConfiguration",
            "com.nuono.next.datapull.wiring.ScheduleRuntimeConfiguration",
            "com.nuono.next.datapull.wiring.SnapshotRuntimeConfiguration",
            "com.nuono.next.datapull.wiring.Dp04SnapshotRuntimeConfiguration",
            "com.nuono.next.datapull.wiring.Dp05RuntimeConfiguration",
            "com.nuono.next.datapull.wiring.Dp06RuntimeConfiguration",
            "com.nuono.next.datapull.wiring.Dp07SnapshotRuntimeConfiguration",
            "com.nuono.next.competitoranalysis.dp08.Dp08AKeywordRankingJob",
            "com.nuono.next.competitoranalysis.dp08.Dp08BExactListBackfillJob",
            "com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10Job"
    );

    @Test
    void defaultModeSelectsLegacyAutomaticRootsUntilManagedActivation() {
        Set<String> candidates = candidates(null);

        assertThat(candidates).containsAll(LEGACY_AUTOMATIC_ROOTS);
        assertThat(candidates).doesNotContainAnyElementsOf(RUNTIME_AUTOMATIC_ROOTS);
    }

    @Test
    void explicitRuntimeModeSelectsRuntimeAutomaticRootsOnly() {
        Set<String> candidates = candidates(DataPullExecutionMode.RUNTIME);

        assertThat(candidates).containsAll(RUNTIME_AUTOMATIC_ROOTS);
        assertThat(candidates).doesNotContainAnyElementsOf(LEGACY_AUTOMATIC_ROOTS);
    }

    @Test
    void missingOrBlankExecutionModeResolvesToLegacy() {
        assertThat(DataPullExecutionMode.resolve(null)).isEqualTo(DataPullExecutionMode.LEGACY);
        assertThat(DataPullExecutionMode.parse(null)).isEqualTo(DataPullExecutionMode.LEGACY);
        assertThat(DataPullExecutionMode.parse("  ")).isEqualTo(DataPullExecutionMode.LEGACY);
        assertThat(readResource("application.yml"))
                .contains("execution-mode: ${NUONO_DATA_PULL_EXECUTION_MODE:LEGACY}");
    }

    @Test
    void invalidModeFailsClosedInsteadOfSelectingEitherSchedulerFamily() {
        assertThatThrownBy(() -> DataPullExecutionMode.parse("mixed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected LEGACY or RUNTIME");
    }

    @Test
    void everyAutomaticRootDeclaresItsExecutionMode() throws IOException {
        for (String className : LEGACY_AUTOMATIC_ROOTS) {
            assertThat(source(className))
                    .as("legacy root must be deployment-mode fenced: %s", className)
                    .contains("@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)");
        }
        for (String className : RUNTIME_AUTOMATIC_ROOTS) {
            assertThat(source(className))
                    .as("runtime root must be deployment-mode fenced: %s", className)
                    .contains("@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)");
        }
    }

    @Test
    void retiredBooleanToggleExistsOnlyAsManagedReleaseAmbiguityRejection()
            throws IOException {
        List<Path> references = javaSources().stream().filter((source) -> {
            try {
                String content = Files.readString(source);
                return content.contains("RETIRED_ENABLED_PROPERTY")
                        || content.contains("RETIRED_ENABLED_ENVIRONMENT_VARIABLE");
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }).collect(Collectors.toList());

        assertThat(references).containsExactlyInAnyOrder(
                sourcePath(DataPullExecutionMode.class.getName()),
                sourcePath(DataPullManagedReleaseStartupGuard.class.getName())
        );
        assertThat(source(DataPullManagedReleaseStartupGuard.class.getName()))
                .contains("retiredRuntimeTogglePresent()")
                .contains("RETIRED_RUNTIME_TOGGLE");
    }

    @Test
    void legacySalesImportPersistsSalesFactsWithoutListingProjection() throws IOException {
        String source = source("com.nuono.next.sales.SalesSyncTaskService");

        assertThat(source)
                .contains("importService.importCsv")
                .doesNotContain(
                        "markSiteOffersNotListedForEmptyReport",
                        "refreshProductSiteOfferListingStartedAtBySalesFact",
                        "refreshListingStartedAt"
                );
    }

    @Test
    void competitorAndAliCompatibilityConsumersRemainLegacyOnly() throws IOException {
        assertThat(source(
                "com.nuono.next.competitoranalysis.LegacyCompetitorScheduledTaskFactory"))
                .contains("DataPullExecutionMode.LEGACY")
                .contains("accepts scheduled modes only");
        assertThat(source(
                "com.nuono.next.competitoranalysis.CompetitorManualRecoveryScope"))
                .contains("MANUAL_REFRESH", "MANUAL_MONITOR");
        assertThat(source(
                "com.nuono.next.procurement.aliorder.LegacyAli1688HistoricalOrderWeeklySyncService"))
                .contains("DataPullExecutionMode.LEGACY");
    }

    private Set<String> candidates(DataPullExecutionMode mode) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-db");
        if (mode != null) {
            environment.setProperty(DataPullExecutionMode.PROPERTY, mode.name());
        }
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(true, environment);
        Set<String> candidates = new LinkedHashSet<>();
        for (String basePackage : List.of(
                "com.nuono.next.datapull",
                "com.nuono.next.noonpull",
                "com.nuono.next.productpublicdetail",
                "com.nuono.next.sales",
                "com.nuono.next.competitoranalysis",
                "com.nuono.next.procurement.aliorder"
        )) {
            scanner.findCandidateComponents(basePackage).forEach((definition) ->
                    candidates.add(definition.getBeanClassName()));
        }
        return candidates;
    }

    private String source(String className) throws IOException {
        return Files.readString(sourcePath(className));
    }

    private Path sourcePath(String className) {
        return MAIN_SOURCE.resolve(className.replace('.', '/') + ".java");
    }

    private String readResource(String name) {
        try {
            return Files.readString(Path.of("src", "main", "resources", name));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private List<Path> javaSources() throws IOException {
        try (var paths = Files.walk(MAIN_SOURCE)) {
            return paths.filter(Files::isRegularFile)
                    .filter((path) -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }
}
