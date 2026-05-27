package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SalesDataSchemaContractTest {

    @Test
    void salesDataFoundationMigrationDefinesBatchAndIdempotentFactTables() throws IOException {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "init", "053_sales_data_analysis_foundation.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sales_import_batch`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sales_import_exception`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `daily_sales_fact`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_daily_sales_fact_source_scope`"));
        assertTrue(sql.contains("('sales_import_exception', 30000, NOW(), NOW())"));
        assertTrue(sql.contains("`status` VARCHAR(40) NOT NULL DEFAULT 'imported'"));
        assertTrue(sql.contains("`failure_summary_json` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("`exception_type` VARCHAR(80) NOT NULL"));
        assertTrue(sql.contains("`source_context` LONGTEXT DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_sales_import_exception_batch` (`source_batch_id`)"));
        assertTrue(sql.contains("`your_visitors` INT DEFAULT NULL"));
        assertTrue(sql.contains("`cancelled_units` INT DEFAULT NULL"));
        assertTrue(sql.contains("`net_units` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("`revenue_shipped` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`conversion_visitors_percentage` DECIMAL(10,4) DEFAULT NULL"));
    }
}
