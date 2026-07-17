-- Mobile receipt confirmation retries must not add inventory twice.

SET @warehouse_receipt_client_request_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_fulfillment_confirmation'
      AND COLUMN_NAME = 'client_request_id'
);

SET @warehouse_receipt_client_request_column_ddl := IF(
    @warehouse_receipt_client_request_column_exists = 0,
    'ALTER TABLE `procurement_fulfillment_confirmation` ADD COLUMN `client_request_id` VARCHAR(100) DEFAULT NULL AFTER `owner_user_id`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_receipt_client_request_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_receipt_request_fingerprint_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_fulfillment_confirmation'
      AND COLUMN_NAME = 'request_fingerprint'
);

SET @warehouse_receipt_request_fingerprint_column_ddl := IF(
    @warehouse_receipt_request_fingerprint_column_exists = 0,
    'ALTER TABLE `procurement_fulfillment_confirmation` ADD COLUMN `request_fingerprint` CHAR(64) DEFAULT NULL AFTER `client_request_id`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_receipt_request_fingerprint_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_receipt_request_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_fulfillment_confirmation'
      AND INDEX_NAME = 'uk_fulfillment_confirmation_owner_client_request'
);

SET @warehouse_receipt_request_index_ddl := IF(
    @warehouse_receipt_request_index_exists = 0,
    'ALTER TABLE `procurement_fulfillment_confirmation` ADD UNIQUE KEY `uk_fulfillment_confirmation_owner_client_request` (`owner_user_id`, `client_request_id`)',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_receipt_request_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
