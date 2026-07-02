-- Persist image metadata for current product images and product image profile assets.

SET NAMES utf8mb4;

SET @product_image_asset_add_content_type := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_asset'
        AND COLUMN_NAME = 'content_type'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_asset` ADD COLUMN `content_type` VARCHAR(120) DEFAULT NULL AFTER `original_filename`'
  )
);
PREPARE stmt FROM @product_image_asset_add_content_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_asset_add_size_bytes := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_asset'
        AND COLUMN_NAME = 'size_bytes'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_asset` ADD COLUMN `size_bytes` BIGINT DEFAULT NULL AFTER `content_type`'
  )
);
PREPARE stmt FROM @product_image_asset_add_size_bytes;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_asset_add_width_px := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_asset'
        AND COLUMN_NAME = 'width_px'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_asset` ADD COLUMN `width_px` INT DEFAULT NULL AFTER `size_bytes`'
  )
);
PREPARE stmt FROM @product_image_asset_add_width_px;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_asset_add_height_px := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_asset'
        AND COLUMN_NAME = 'height_px'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_asset` ADD COLUMN `height_px` INT DEFAULT NULL AFTER `width_px`'
  )
);
PREPARE stmt FROM @product_image_asset_add_height_px;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_content_type := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'content_type'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `content_type` VARCHAR(120) DEFAULT NULL AFTER `image_url`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_content_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_size_bytes := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'size_bytes'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `size_bytes` BIGINT DEFAULT NULL AFTER `content_type`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_size_bytes;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_width_px := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'width_px'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `width_px` INT DEFAULT NULL AFTER `size_bytes`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_width_px;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_height_px := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'height_px'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `height_px` INT DEFAULT NULL AFTER `width_px`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_height_px;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
