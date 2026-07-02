-- Move product image profiles toward logical-store scope while preserving store_code compatibility.

SET NAMES utf8mb4;

SET @product_image_profile_add_logical_store_id := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile'
        AND COLUMN_NAME = 'logical_store_id'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile` ADD COLUMN `logical_store_id` BIGINT DEFAULT NULL AFTER `store_code`'
  )
);
PREPARE stmt FROM @product_image_profile_add_logical_store_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE product_image_profile p
JOIN logical_store_site lss
  ON lss.store_code = p.store_code
 AND lss.is_deleted = b'0'
JOIN logical_store ls
  ON ls.id = lss.logical_store_id
 AND ls.owner_user_id = p.owner_user_id
 AND ls.is_deleted = b'0'
SET p.logical_store_id = lss.logical_store_id
WHERE p.logical_store_id IS NULL;

SET @product_image_profile_add_logical_scope_index := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile'
        AND INDEX_NAME = 'idx_product_image_profile_logical_scope'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile` ADD INDEX `idx_product_image_profile_logical_scope` (`owner_user_id`, `logical_store_id`, `deleted`, `updated_at`)'
  )
);
PREPARE stmt FROM @product_image_profile_add_logical_scope_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_add_logical_identity_index := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile'
        AND INDEX_NAME = 'idx_product_image_profile_logical_identity'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile` ADD INDEX `idx_product_image_profile_logical_identity` (`owner_user_id`, `logical_store_id`, `psku_code`, `product_identity_key`, `deleted`)'
  )
);
PREPARE stmt FROM @product_image_profile_add_logical_identity_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_source_store_code := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'source_store_code'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `source_store_code` VARCHAR(64) DEFAULT NULL AFTER `height_px`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_source_store_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_source_site_code := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'source_site_code'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `source_site_code` VARCHAR(32) DEFAULT NULL AFTER `source_store_code`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_source_site_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_source_snapshot_id := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'source_snapshot_id'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `source_snapshot_id` BIGINT DEFAULT NULL AFTER `source_site_code`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_source_snapshot_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_source_field := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'source_field'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `source_field` VARCHAR(120) DEFAULT NULL AFTER `source_snapshot_id`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_source_field;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_source_kind := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'source_kind'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `source_kind` VARCHAR(120) DEFAULT NULL AFTER `source_field`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_source_kind;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_source_index := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND INDEX_NAME = 'idx_product_image_profile_asset_source'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD INDEX `idx_product_image_profile_asset_source` (`source_store_code`, `source_site_code`, `source_snapshot_id`)'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_source_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
