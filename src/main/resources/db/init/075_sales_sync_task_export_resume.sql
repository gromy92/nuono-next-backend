-- Persist Noon report export state so long-running exports can be resumed without creating duplicates.

SET @sales_sync_task_add_export_code := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_sync_task'
              AND COLUMN_NAME = 'export_code'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_sync_task` ADD COLUMN `export_code` VARCHAR(120) DEFAULT NULL AFTER `latest_fact_date`'
    )
);
PREPARE stmt FROM @sales_sync_task_add_export_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_sync_task_add_export_status := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_sync_task'
              AND COLUMN_NAME = 'export_status'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_sync_task` ADD COLUMN `export_status` VARCHAR(40) DEFAULT NULL AFTER `export_code`'
    )
);
PREPARE stmt FROM @sales_sync_task_add_export_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_sync_task_add_export_download_url := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_sync_task'
              AND COLUMN_NAME = 'export_download_url'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_sync_task` ADD COLUMN `export_download_url` VARCHAR(1000) DEFAULT NULL AFTER `export_status`'
    )
);
PREPARE stmt FROM @sales_sync_task_add_export_download_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_sync_task_add_export_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_sync_task'
              AND INDEX_NAME = 'idx_sales_sync_task_export'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_sync_task` ADD KEY `idx_sales_sync_task_export` (`export_code`)'
    )
);
PREPARE stmt FROM @sales_sync_task_add_export_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
