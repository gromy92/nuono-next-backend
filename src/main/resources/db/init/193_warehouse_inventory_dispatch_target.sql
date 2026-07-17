-- Persist warehouse inventory dispatch targets.
SET @warehouse_inventory_target_site_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_fulfillment_balance'
    AND COLUMN_NAME = 'target_site_code'
);

SET @warehouse_inventory_target_site_ddl := IF(
    @warehouse_inventory_target_site_exists = 0,
    'ALTER TABLE `procurement_fulfillment_balance` ADD COLUMN `target_site_code` VARCHAR(20) DEFAULT NULL AFTER `planned_transport_mode`',
    'SELECT 1'
);
PREPARE stmt FROM @warehouse_inventory_target_site_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_inventory_target_transport_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_fulfillment_balance'
    AND COLUMN_NAME = 'target_transport_mode'
);

SET @warehouse_inventory_target_transport_ddl := IF(
    @warehouse_inventory_target_transport_exists = 0,
    'ALTER TABLE `procurement_fulfillment_balance` ADD COLUMN `target_transport_mode` VARCHAR(20) DEFAULT NULL AFTER `target_site_code`',
    'SELECT 1'
);
PREPARE stmt FROM @warehouse_inventory_target_transport_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_inventory_target_index_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_fulfillment_balance'
    AND INDEX_NAME = 'idx_fulfillment_balance_target'
);

SET @warehouse_inventory_target_index_ddl := IF(
    @warehouse_inventory_target_index_exists = 0,
    'ALTER TABLE `procurement_fulfillment_balance` ADD KEY `idx_fulfillment_balance_target` (`owner_user_id`, `target_site_code`, `target_transport_mode`, `available_quantity`, `is_deleted`)',
    'SELECT 1'
);
PREPARE stmt FROM @warehouse_inventory_target_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
