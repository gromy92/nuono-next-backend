SET @proc_ali1688_order_add_deleted_by := (
  SELECT IF(EXISTS(
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_order_header'
      AND COLUMN_NAME = 'deleted_by'
  ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `deleted_by` BIGINT DEFAULT NULL AFTER `raw_snapshot_json`')
);
PREPARE stmt FROM @proc_ali1688_order_add_deleted_by; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_deleted_at := (
  SELECT IF(EXISTS(
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_order_header'
      AND COLUMN_NAME = 'deleted_at'
  ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `deleted_at` DATETIME DEFAULT NULL AFTER `deleted_by`')
);
PREPARE stmt FROM @proc_ali1688_order_add_deleted_at; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_delete_reason := (
  SELECT IF(EXISTS(
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_order_header'
      AND COLUMN_NAME = 'delete_reason'
  ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `delete_reason` VARCHAR(500) DEFAULT NULL AFTER `deleted_at`')
);
PREPARE stmt FROM @proc_ali1688_order_add_delete_reason; EXECUTE stmt; DEALLOCATE PREPARE stmt;
