-- Active-row uniqueness and assignment write guards for 1688 historical order distribution.

SET NAMES utf8mb4;

SET @ali1688_assignment_add_active_item_id := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'procurement_ali1688_order_item_assignment'
        AND COLUMN_NAME = 'active_item_id'
    ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item_assignment` ADD COLUMN `active_item_id` BIGINT GENERATED ALWAYS AS (CASE WHEN `status` = ''active'' AND `is_deleted` = b''0'' THEN `item_id` ELSE NULL END) STORED AFTER `item_id`'
  )
);
PREPARE stmt FROM @ali1688_assignment_add_active_item_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_assignment_add_active_store_key := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'procurement_ali1688_order_item_assignment'
        AND COLUMN_NAME = 'active_target_store_key'
    ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item_assignment` ADD COLUMN `active_target_store_key` VARCHAR(120) GENERATED ALWAYS AS (CASE WHEN `status` = ''active'' AND `is_deleted` = b''0'' THEN COALESCE(`target_store_code`, ''__CONSUMABLE__'') ELSE NULL END) STORED AFTER `target_store_code`'
  )
);
PREPARE stmt FROM @ali1688_assignment_add_active_store_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_assignment_add_active_site_key := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'procurement_ali1688_order_item_assignment'
        AND COLUMN_NAME = 'active_target_site_key'
    ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item_assignment` ADD COLUMN `active_target_site_key` VARCHAR(40) GENERATED ALWAYS AS (CASE WHEN `status` = ''active'' AND `is_deleted` = b''0'' THEN COALESCE(`target_site_code`, ''__CONSUMABLE__'') ELSE NULL END) STORED AFTER `target_site_code`'
  )
);
PREPARE stmt FROM @ali1688_assignment_add_active_site_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_assignment_add_active_target_unique := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'procurement_ali1688_order_item_assignment'
        AND INDEX_NAME = 'uk_ali1688_assignment_active_target'
    ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item_assignment` ADD UNIQUE KEY `uk_ali1688_assignment_active_target` (`owner_user_id`, `active_item_id`, `target_type`, `active_target_store_key`, `active_target_site_key`)'
  )
);
PREPARE stmt FROM @ali1688_assignment_add_active_target_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_product_link_add_active_assignment_id := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'procurement_ali1688_order_item_product_link'
        AND COLUMN_NAME = 'active_assignment_id'
    ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item_product_link` ADD COLUMN `active_assignment_id` BIGINT GENERATED ALWAYS AS (CASE WHEN `status` = ''active'' AND `is_deleted` = b''0'' THEN `assignment_id` ELSE NULL END) STORED AFTER `assignment_id`'
  )
);
PREPARE stmt FROM @ali1688_product_link_add_active_assignment_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_product_link_add_active_assignment_unique := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'procurement_ali1688_order_item_product_link'
        AND INDEX_NAME = 'uk_ali1688_product_link_active_assignment'
    ),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item_product_link` ADD UNIQUE KEY `uk_ali1688_product_link_active_assignment` (`owner_user_id`, `active_assignment_id`)'
  )
);
PREPARE stmt FROM @ali1688_product_link_add_active_assignment_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
