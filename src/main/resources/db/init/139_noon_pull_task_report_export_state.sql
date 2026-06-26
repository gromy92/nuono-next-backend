-- Persist Noon report export state on pull tasks so schedulers can resume one export instead of creating duplicates.

SET @noon_pull_add_report_export_id := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'report_export_id'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD COLUMN `report_export_id` VARCHAR(160) DEFAULT NULL AFTER `readiness_state`'
    )
);
PREPARE stmt FROM @noon_pull_add_report_export_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_report_export_status := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'report_export_status'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD COLUMN `report_export_status` VARCHAR(64) DEFAULT NULL AFTER `report_export_id`'
    )
);
PREPARE stmt FROM @noon_pull_add_report_export_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_report_download_url := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'report_download_url'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD COLUMN `report_download_url` VARCHAR(1200) DEFAULT NULL AFTER `report_export_status`'
    )
);
PREPARE stmt FROM @noon_pull_add_report_download_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_report_total_rows := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'report_total_rows'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD COLUMN `report_total_rows` INT DEFAULT NULL AFTER `report_download_url`'
    )
);
PREPARE stmt FROM @noon_pull_add_report_total_rows;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_report_last_poll_at := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'report_last_poll_at'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD COLUMN `report_last_poll_at` DATETIME DEFAULT NULL AFTER `report_total_rows`'
    )
);
PREPARE stmt FROM @noon_pull_add_report_last_poll_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_report_next_poll_at := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'report_next_poll_at'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD COLUMN `report_next_poll_at` DATETIME DEFAULT NULL AFTER `report_last_poll_at`'
    )
);
PREPARE stmt FROM @noon_pull_add_report_next_poll_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_report_poll_attempts := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'report_poll_attempts'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD COLUMN `report_poll_attempts` INT DEFAULT NULL AFTER `report_next_poll_at`'
    )
);
PREPARE stmt FROM @noon_pull_add_report_poll_attempts;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_report_export_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND INDEX_NAME = 'idx_noon_pull_task_report_export'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD KEY `idx_noon_pull_task_report_export` (`report_export_id`)'
    )
);
PREPARE stmt FROM @noon_pull_add_report_export_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_report_next_poll_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND INDEX_NAME = 'idx_noon_pull_task_next_poll'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD KEY `idx_noon_pull_task_next_poll` (`status`, `report_next_poll_at`)'
    )
);
PREPARE stmt FROM @noon_pull_add_report_next_poll_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
