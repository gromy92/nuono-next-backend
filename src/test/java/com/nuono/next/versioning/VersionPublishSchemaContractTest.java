package com.nuono.next.versioning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VersionPublishSchemaContractTest {

    @Test
    void migrationDefinesGenericPublishTablesWithoutFileManagementReuse() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "059_advanced_operations_config_publish_foundation.sql"
        ));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `version_publish_record`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `version_publish_audit_log`"));
        assertTrue(sql.contains("('version_publish_record', 80000, NOW(), NOW())"));
        assertTrue(sql.contains("('version_publish_audit_log', 81000, NOW(), NOW())"));
        assertTrue(sql.contains("`domain_type` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`domain_ref_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`version_no` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`status` VARCHAR(40) NOT NULL"));
        assertTrue(sql.contains("`scope_summary` VARCHAR(1000) DEFAULT NULL"));
        assertTrue(sql.contains("`previous_version_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`published_by` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`published_at` DATETIME DEFAULT NULL"));
        assertTrue(sql.contains("`before_snapshot` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("`after_snapshot` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_version_publish_current` (`domain_type`, `domain_ref_id`, `status`, `published_at`, `id`)"));
        assertFalse(sql.contains("`rule_json`"));
        assertFalse(sql.contains("`content_json`"));
        assertFalse(sql.contains("`payload_json`"));
        assertFalse(sql.contains("file_mgmt_parse_version"));
        assertFalse(sql.contains("file_mgmt_parse_active_version"));
        assertFalse(sql.contains("file_mgmt_parse_standard_version"));
    }
}
