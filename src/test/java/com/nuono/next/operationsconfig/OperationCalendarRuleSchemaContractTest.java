package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OperationCalendarRuleSchemaContractTest {

    @Test
    void migrationDefinesCalendarRuleTableWithPublishReference() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "061_operation_calendar_rule.sql"
        ));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `operation_calendar_rule`"));
        assertTrue(sql.contains("('operation_calendar_rule', 82000, NOW(), NOW())"));
        assertTrue(sql.contains("`owner_user_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`store_code` VARCHAR(100) NOT NULL"));
        assertTrue(sql.contains("`site_code` VARCHAR(20) NOT NULL"));
        assertTrue(sql.contains("`recurring_expression` VARCHAR(120) DEFAULT NULL"));
        assertTrue(sql.contains("`target_scope_type` VARCHAR(40) NOT NULL"));
        assertTrue(sql.contains("`factor_value` DECIMAL(10,4) NOT NULL"));
        assertTrue(sql.contains("`factor_purpose` VARCHAR(60) NOT NULL"));
        assertTrue(sql.contains("`publish_record_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`publish_status` VARCHAR(40) NOT NULL"));
        assertFalse(sql.contains("sales_activity_window"), "compatibility adapter belongs to the later compatibility issue");
    }

    @Test
    void bundleLinkMigrationAddsNullableCalendarRuleBundleReference() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "067_operation_calendar_rule_bundle_link.sql"
        ));

        assertTrue(sql.contains("ADD COLUMN `bundle_version_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("idx_operation_calendar_rule_bundle"));
    }
}
