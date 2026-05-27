package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OperationLifecycleRuleSchemaContractTest {

    @Test
    void bundleLinkMigrationAddsNullableLifecycleRuleBundleReference() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "068_operation_lifecycle_rule_bundle_link.sql"
        ));

        assertTrue(sql.contains("ADD COLUMN `bundle_version_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("idx_operation_lifecycle_rule_bundle"));
        assertTrue(sql.contains("INFORMATION_SCHEMA.COLUMNS"));
        assertTrue(sql.contains("INFORMATION_SCHEMA.STATISTICS"));
        assertTrue(sql.contains("PREPARE stmt FROM"));
        assertTrue(sql.contains("'SELECT 1'"));
    }
}
