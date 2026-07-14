package com.nuono.next.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.system.schema.DbInitMigrationRegistry;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SqlMigrationNamingTest {

    @Test
    void migrationNamesAndDuplicatePrefixesFollowGovernance() throws IOException, InterruptedException {
        List<String> fileNames = TrackedSqlFiles.initSqlFiles().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toList());

        DbInitMigrationRegistry.MigrationGovernanceReport report =
                DbInitMigrationRegistry.inspectFileNames(fileNames, DbInitMigrationRegistry.legacyDuplicatePrefixes());
        assertTrue(report.isValid(), "Invalid db init migration governance: " + report.getViolations());
    }
}
