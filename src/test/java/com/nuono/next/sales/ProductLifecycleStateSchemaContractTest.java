package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductLifecycleStateSchemaContractTest {

    @Test
    void lifecycleStateMigrationDefinesCurrentHistoryAndJobTables() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "056_product_lifecycle_state_engine.sql"
        ));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `product_lifecycle_current_state`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `product_lifecycle_history`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `product_lifecycle_job`"));
        assertTrue(sql.contains("('product_lifecycle_current_state', 70000, NOW(), NOW())"));
        assertTrue(sql.contains("('product_lifecycle_history', 71000, NOW(), NOW())"));
        assertTrue(sql.contains("('product_lifecycle_job', 72000, NOW(), NOW())"));
        assertTrue(sql.contains("UNIQUE KEY `uk_product_lifecycle_current_scope`"));
        assertTrue(sql.contains("KEY `idx_product_lifecycle_current_scope_state`"));
        assertTrue(sql.contains("`owner_user_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`store_code` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`site_code` VARCHAR(20) NOT NULL"));
        assertTrue(sql.contains("`partner_sku` VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("`sku` VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("`rule_version` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`analysis_date` DATE NOT NULL"));
        assertTrue(sql.contains("`listing_date_source` VARCHAR(40) DEFAULT NULL"));
        assertTrue(sql.contains("`quality_state` VARCHAR(40) NOT NULL"));
        assertTrue(sql.contains("`evidence_json` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("`triggered_by_user_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`trigger_source` VARCHAR(80) DEFAULT NULL"));
    }
}
