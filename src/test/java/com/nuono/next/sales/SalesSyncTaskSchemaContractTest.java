package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SalesSyncTaskSchemaContractTest {

    @Test
    void salesDataFoundationMigrationDefinesSalesSyncTaskTable() throws IOException {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "init", "053_sales_data_analysis_foundation.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `sales_sync_task`"));
        assertTrue(sql.contains("`status` VARCHAR(40) NOT NULL DEFAULT 'queued'"));
        assertTrue(sql.contains("`source_batch_id` BIGINT DEFAULT NULL"));
        assertTrue(sql.contains("`failure_reason` VARCHAR(1000) DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_sales_sync_task_scope`"));
        assertTrue(sql.contains("KEY `idx_sales_sync_task_status`"));
    }
}
