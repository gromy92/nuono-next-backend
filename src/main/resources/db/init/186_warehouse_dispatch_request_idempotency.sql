-- Make APP-created dispatch plans idempotent per business owner.

SET @dispatch_client_request_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_dispatch_plan'
      AND COLUMN_NAME = 'client_request_id'
);

SET @dispatch_client_request_column_ddl := IF(
    @dispatch_client_request_column_exists = 0,
    'ALTER TABLE `procurement_dispatch_plan` ADD COLUMN `client_request_id` VARCHAR(100) DEFAULT NULL AFTER `owner_user_id`',
    'SELECT 1'
);

PREPARE stmt FROM @dispatch_client_request_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dispatch_request_fingerprint_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_dispatch_plan'
      AND COLUMN_NAME = 'request_fingerprint'
);

SET @dispatch_request_fingerprint_column_ddl := IF(
    @dispatch_request_fingerprint_column_exists = 0,
    'ALTER TABLE `procurement_dispatch_plan` ADD COLUMN `request_fingerprint` VARCHAR(64) DEFAULT NULL AFTER `client_request_id`',
    'SELECT 1'
);

PREPARE stmt FROM @dispatch_request_fingerprint_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dispatch_client_request_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_dispatch_plan'
      AND INDEX_NAME = 'uk_dispatch_plan_owner_client_request'
);

SET @dispatch_client_request_index_ddl := IF(
    @dispatch_client_request_index_exists = 0,
    'ALTER TABLE `procurement_dispatch_plan` ADD UNIQUE KEY `uk_dispatch_plan_owner_client_request` (`owner_user_id`, `client_request_id`)',
    'SELECT 1'
);

PREPARE stmt FROM @dispatch_client_request_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
