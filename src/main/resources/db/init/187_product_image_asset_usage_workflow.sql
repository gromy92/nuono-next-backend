-- Multi-role product image usage, processing notes, and Noon technical metadata.

SET NAMES utf8mb4;

SET @profile_asset_add_horizontal_ppi := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'horizontal_ppi'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `horizontal_ppi` DECIMAL(10,2) DEFAULT NULL AFTER `height_px`'
  )
);
PREPARE stmt FROM @profile_asset_add_horizontal_ppi;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @profile_asset_add_vertical_ppi := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'vertical_ppi'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `vertical_ppi` DECIMAL(10,2) DEFAULT NULL AFTER `horizontal_ppi`'
  )
);
PREPARE stmt FROM @profile_asset_add_vertical_ppi;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @profile_asset_add_color_space := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'color_space'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `color_space` VARCHAR(32) DEFAULT NULL AFTER `vertical_ppi`'
  )
);
PREPARE stmt FROM @profile_asset_add_color_space;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_asset_add_horizontal_ppi := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_asset'
        AND COLUMN_NAME = 'horizontal_ppi'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_asset` ADD COLUMN `horizontal_ppi` DECIMAL(10,2) DEFAULT NULL AFTER `height_px`'
  )
);
PREPARE stmt FROM @product_asset_add_horizontal_ppi;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_asset_add_vertical_ppi := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_asset'
        AND COLUMN_NAME = 'vertical_ppi'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_asset` ADD COLUMN `vertical_ppi` DECIMAL(10,2) DEFAULT NULL AFTER `horizontal_ppi`'
  )
);
PREPARE stmt FROM @product_asset_add_vertical_ppi;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_asset_add_color_space := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_asset'
        AND COLUMN_NAME = 'color_space'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_asset` ADD COLUMN `color_space` VARCHAR(32) DEFAULT NULL AFTER `vertical_ppi`'
  )
);
PREPARE stmt FROM @product_asset_add_color_space;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `product_image_profile_asset_usage` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `profile_id` BIGINT NOT NULL,
  `asset_id` BIGINT NOT NULL,
  `image_role` VARCHAR(32) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `processing_note` VARCHAR(2000) DEFAULT NULL,
  `processing_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `processed_by` BIGINT DEFAULT NULL,
  `processed_at` DATETIME DEFAULT NULL,
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_image_asset_usage_role` (`profile_id`, `asset_id`, `image_role`, `active_unique_key`),
  KEY `idx_product_image_asset_usage_profile` (`profile_id`, `deleted`, `image_role`, `sort_order`, `id`),
  KEY `idx_product_image_asset_usage_asset` (`asset_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `product_image_profile_asset_usage` (
  `profile_id`, `asset_id`, `image_role`, `sort_order`,
  `processing_status`, `created_by`, `updated_by`, `created_at`, `updated_at`, `deleted`
)
SELECT
  asset.`profile_id`,
  asset.`id`,
  asset.`image_role`,
  asset.`sort_order`,
  'PENDING',
  asset.`created_by`,
  asset.`updated_by`,
  asset.`created_at`,
  asset.`updated_at`,
  b'0'
FROM `product_image_profile_asset` asset
WHERE asset.`asset_status` = 'ACTIVE'
  AND asset.`image_role` IN ('MAIN', 'SIZE', 'DETAIL', 'SCENE', 'PACKAGE')
  AND NOT EXISTS (
    SELECT 1
    FROM `product_image_profile_asset_usage` usage_row
    WHERE usage_row.`profile_id` = asset.`profile_id`
      AND usage_row.`asset_id` = asset.`id`
      AND usage_row.`image_role` = asset.`image_role`
  );
