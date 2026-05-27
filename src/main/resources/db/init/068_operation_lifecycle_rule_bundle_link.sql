-- Attach lifecycle rules to operations configuration suite bundles.

SET NAMES utf8mb4;

SET @add_lifecycle_rule_bundle_version_id := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `operation_lifecycle_rule` ADD COLUMN `bundle_version_id` BIGINT DEFAULT NULL AFTER `long_tail_max_monthly_sales`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_lifecycle_rule'
        AND COLUMN_NAME = 'bundle_version_id'
);
PREPARE stmt FROM @add_lifecycle_rule_bundle_version_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_lifecycle_rule_bundle_index := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `operation_lifecycle_rule` ADD KEY `idx_operation_lifecycle_rule_bundle` (`bundle_version_id`, `owner_user_id`, `store_code`, `site_code`, `gmt_updated`, `id`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_lifecycle_rule'
        AND INDEX_NAME = 'idx_operation_lifecycle_rule_bundle'
);
PREPARE stmt FROM @add_lifecycle_rule_bundle_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
