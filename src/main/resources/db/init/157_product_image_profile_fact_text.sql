-- Persist the long-form product facts used as AI image generation reference text.

SET NAMES utf8mb4;

SET @product_image_profile_add_product_fact_text := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile'
        AND COLUMN_NAME = 'product_fact_text'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile` ADD COLUMN `product_fact_text` LONGTEXT DEFAULT NULL AFTER `spec_summary`'
  )
);
PREPARE stmt FROM @product_image_profile_add_product_fact_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
