package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OperationConfigBundleSchemaContractTest {

    @Test
    void migrationDefinesBundleRootTableForSuiteVersioning() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "066_operation_config_bundle.sql"
        ));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `operation_config_bundle`"));
        assertTrue(sql.contains("('operation_config_bundle', 86000, NOW(), NOW())"));
        assertTrue(sql.contains("`publish_record_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`version_no` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`publish_source_role` VARCHAR(60) NOT NULL"));
        assertTrue(sql.contains("`publish_source_label` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`scope_summary` VARCHAR(1000) NOT NULL"));
        assertTrue(sql.contains("`affected_store_count` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`activity_rule_count` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`lifecycle_rule_summary` VARCHAR(200) NOT NULL DEFAULT '未配置'"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `operation_config_bundle_scope`"));
        assertTrue(sql.contains("`bundle_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("UNIQUE KEY `uk_operation_config_bundle_scope` (`bundle_id`, `owner_user_id`, `store_code`, `site_code`)"));
    }
}
