-- Link business calendar activity factor rules to operations configuration bundle versions.

SET NAMES utf8mb4;

SET @add_calendar_rule_bundle_version_id := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `operation_calendar_rule` ADD COLUMN `bundle_version_id` BIGINT DEFAULT NULL AFTER `enabled`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_calendar_rule'
        AND COLUMN_NAME = 'bundle_version_id'
);
PREPARE stmt FROM @add_calendar_rule_bundle_version_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_calendar_rule_bundle_index := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `operation_calendar_rule` ADD KEY `idx_operation_calendar_rule_bundle` (`bundle_version_id`, `owner_user_id`, `store_code`, `site_code`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_calendar_rule'
        AND INDEX_NAME = 'idx_operation_calendar_rule_bundle'
);
PREPARE stmt FROM @add_calendar_rule_bundle_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
