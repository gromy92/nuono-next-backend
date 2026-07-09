package com.nuono.next.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.system.schema.DbInitMigrationRegistry;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DbInitMigrationRegistryTest {

    @Test
    void registryListsEveryTrackedInitSqlAsClasspathLocation() throws Exception {
        List<String> expectedLocations = TrackedSqlFiles.initSqlFiles().stream()
                .map(path -> path.getFileName().toString())
                .sorted(migrationFileComparator())
                .map(fileName -> "classpath:db/init/" + fileName)
                .collect(Collectors.toList());

        assertThat(DbInitMigrationRegistry.initScriptResourceLocations())
                .containsExactlyElementsOf(expectedLocations);
    }

    @Test
    void governanceReportRejectsDuplicatePrefixWithoutLegacyLedgerEntry() {
        List<String> fileNames = List.of(
                "200_feature_alpha.sql",
                "200_feature_beta.sql"
        );

        DbInitMigrationRegistry.MigrationGovernanceReport report =
                DbInitMigrationRegistry.inspectFileNames(fileNames, List.of());

        assertThat(report.isValid()).isFalse();
        assertThat(report.getViolations())
                .contains("Duplicate migration prefix 200 is not registered in db/init/migration-governance.tsv");
    }

    @Test
    void governanceReportRejectsLegacyDuplicatePrefixWhenFileSetDrifts() {
        List<String> fileNames = List.of(
                "200_feature_alpha.sql",
                "200_feature_beta.sql"
        );
        List<DbInitMigrationRegistry.LegacyDuplicatePrefix> legacyDuplicates = List.of(
                new DbInitMigrationRegistry.LegacyDuplicatePrefix(
                        "200",
                        "fixture",
                        "documents a legacy collision",
                        List.of("200_feature_alpha.sql", "200_feature_gamma.sql")
                )
        );

        DbInitMigrationRegistry.MigrationGovernanceReport report =
                DbInitMigrationRegistry.inspectFileNames(fileNames, legacyDuplicates);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getViolations())
                .contains("Duplicate migration prefix 200 registered files do not match actual files: "
                        + "registered=[200_feature_alpha.sql, 200_feature_gamma.sql], "
                        + "actual=[200_feature_alpha.sql, 200_feature_beta.sql]");
    }

    @Test
    void legacyLedgerDocumentsAllExistingDuplicatePrefixes() throws Exception {
        List<String> fileNames = TrackedSqlFiles.initSqlFiles().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toList());

        DbInitMigrationRegistry.MigrationGovernanceReport report =
                DbInitMigrationRegistry.inspectFileNames(fileNames, DbInitMigrationRegistry.legacyDuplicatePrefixes());

        assertThat(report.getViolations()).isEmpty();
        assertThat(duplicatePrefixes(fileNames))
                .containsExactlyInAnyOrder("101", "102", "103", "104", "133", "134", "135", "136", "137", "138");
    }

    private static Comparator<String> migrationFileComparator() {
        return Comparator
                .comparingInt(DbInitMigrationRegistryTest::migrationNumber)
                .thenComparing(fileName -> fileName);
    }

    private static int migrationNumber(String fileName) {
        return Integer.parseInt(fileName.substring(0, fileName.indexOf('_')));
    }

    private static Collection<String> duplicatePrefixes(List<String> fileNames) {
        return fileNames.stream()
                .collect(Collectors.groupingBy(
                        fileName -> fileName.substring(0, fileName.indexOf('_')),
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }
}
