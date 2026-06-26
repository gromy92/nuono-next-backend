-- Add first-class SKU purchase batch component facts for combo/set purchase history.

SET NAMES utf8mb4;

SET @add_sku_batch_batch_type = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch` ADD COLUMN `batch_type` VARCHAR(40) NOT NULL DEFAULT ''manual'' AFTER `batch_sequence`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch'
      AND COLUMN_NAME = 'batch_type'
);
PREPARE stmt FROM @add_sku_batch_batch_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_counted_quantity_unit = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch` ADD COLUMN `counted_quantity_unit` VARCHAR(40) DEFAULT NULL AFTER `counted_quantity`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch'
      AND COLUMN_NAME = 'counted_quantity_unit'
);
PREPARE stmt FROM @add_sku_batch_counted_quantity_unit; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_component_count = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch` ADD COLUMN `component_count` INT DEFAULT NULL AFTER `counted_cost`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch'
      AND COLUMN_NAME = 'component_count'
);
PREPARE stmt FROM @add_sku_batch_component_count; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_expected_component_count = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch` ADD COLUMN `expected_component_count` INT DEFAULT NULL AFTER `component_count`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch'
      AND COLUMN_NAME = 'expected_component_count'
);
PREPARE stmt FROM @add_sku_batch_expected_component_count; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_component_sequence = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `component_sequence` INT DEFAULT NULL AFTER `assignment_id`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'component_sequence'
);
PREPARE stmt FROM @add_sku_batch_source_component_sequence; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_component_role = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `component_role` VARCHAR(120) DEFAULT NULL AFTER `component_sequence`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'component_role'
);
PREPARE stmt FROM @add_sku_batch_source_component_role; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_offer_id = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_offer_id` VARCHAR(80) DEFAULT NULL AFTER `supplier_name`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_offer_id'
);
PREPARE stmt FROM @add_sku_batch_source_offer_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_sku_id = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_sku_id` VARCHAR(120) DEFAULT NULL AFTER `source_offer_id`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_sku_id'
);
PREPARE stmt FROM @add_sku_batch_source_sku_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_title = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_title` VARCHAR(500) DEFAULT NULL AFTER `source_sku_id`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_title'
);
PREPARE stmt FROM @add_sku_batch_source_title; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_spec = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_spec` VARCHAR(500) DEFAULT NULL AFTER `source_title`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_spec'
);
PREPARE stmt FROM @add_sku_batch_source_spec; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_quantity = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_quantity` DECIMAL(18, 4) DEFAULT NULL AFTER `source_spec`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_quantity'
);
PREPARE stmt FROM @add_sku_batch_source_quantity; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_unit = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_unit` VARCHAR(60) DEFAULT NULL AFTER `source_quantity`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_unit'
);
PREPARE stmt FROM @add_sku_batch_source_unit; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_unit_price = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_unit_price` DECIMAL(18, 6) DEFAULT NULL AFTER `source_unit`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_unit_price'
);
PREPARE stmt FROM @add_sku_batch_source_unit_price; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_amount = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_amount` DECIMAL(18, 4) DEFAULT NULL AFTER `source_unit_price`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_amount'
);
PREPARE stmt FROM @add_sku_batch_source_amount; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_sku_batch_source_quantity_per_counted_unit = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `procurement_ali1688_sku_purchase_batch_source` ADD COLUMN `source_quantity_per_counted_unit` DECIMAL(18, 6) DEFAULT NULL AFTER `source_amount`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_ali1688_sku_purchase_batch_source'
      AND COLUMN_NAME = 'source_quantity_per_counted_unit'
);
PREPARE stmt FROM @add_sku_batch_source_quantity_per_counted_unit; EXECUTE stmt; DEALLOCATE PREPARE stmt;
