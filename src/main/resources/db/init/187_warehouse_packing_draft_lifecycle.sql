-- Warehouse packing drafts may omit box specs until final submission.
-- Active-only generated keys keep writes idempotent while allowing soft-deleted boxes to be recreated.

ALTER TABLE `warehouse_packing_box`
    MODIFY COLUMN `length_cm` DECIMAL(12, 3) DEFAULT NULL,
    MODIFY COLUMN `width_cm` DECIMAL(12, 3) DEFAULT NULL,
    MODIFY COLUMN `height_cm` DECIMAL(12, 3) DEFAULT NULL,
    MODIFY COLUMN `gross_weight_kg` DECIMAL(12, 3) DEFAULT NULL;

SET @warehouse_packing_active_outbound_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_packing_list'
      AND COLUMN_NAME = 'active_outbound_order_id'
);

SET @warehouse_packing_active_outbound_column_ddl := IF(
    @warehouse_packing_active_outbound_column_exists = 0,
    'ALTER TABLE `warehouse_packing_list` ADD COLUMN `active_outbound_order_id` BIGINT GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN `outbound_order_id` ELSE NULL END) STORED AFTER `is_deleted`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_packing_active_outbound_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_packing_active_outbound_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_packing_list'
      AND INDEX_NAME = 'uk_warehouse_packing_active_outbound'
);

SET @warehouse_packing_active_outbound_index_ddl := IF(
    @warehouse_packing_active_outbound_index_exists = 0,
    'ALTER TABLE `warehouse_packing_list` ADD UNIQUE KEY `uk_warehouse_packing_active_outbound` (`active_outbound_order_id`)',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_packing_active_outbound_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_packing_active_box_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_packing_box'
      AND COLUMN_NAME = 'active_box_no'
);

SET @warehouse_packing_active_box_column_ddl := IF(
    @warehouse_packing_active_box_column_exists = 0,
    'ALTER TABLE `warehouse_packing_box` ADD COLUMN `active_box_no` VARCHAR(80) GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN `box_no` ELSE NULL END) STORED AFTER `is_deleted`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_packing_active_box_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_packing_legacy_box_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_packing_box'
      AND INDEX_NAME = 'uk_warehouse_packing_box_no'
);

SET @warehouse_packing_legacy_box_index_ddl := IF(
    @warehouse_packing_legacy_box_index_exists > 0,
    'ALTER TABLE `warehouse_packing_box` DROP INDEX `uk_warehouse_packing_box_no`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_packing_legacy_box_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_packing_active_box_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_packing_box'
      AND INDEX_NAME = 'uk_warehouse_packing_active_box_no'
);

SET @warehouse_packing_active_box_index_ddl := IF(
    @warehouse_packing_active_box_index_exists = 0,
    'ALTER TABLE `warehouse_packing_box` ADD UNIQUE KEY `uk_warehouse_packing_active_box_no` (`packing_list_id`, `active_box_no`)',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_packing_active_box_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
