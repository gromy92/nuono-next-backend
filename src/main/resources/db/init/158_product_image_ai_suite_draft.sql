-- Persist AI suite draft package and prompt before image generation.

SET NAMES utf8mb4;

SET @product_image_suite_add_draft_package_json := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_suite'
        AND COLUMN_NAME = 'draft_package_json'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_suite` ADD COLUMN `draft_package_json` LONGTEXT DEFAULT NULL AFTER `generation_task_id`'
  )
);
PREPARE stmt FROM @product_image_suite_add_draft_package_json;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_suite_add_draft_prompt_text := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_suite'
        AND COLUMN_NAME = 'draft_prompt_text'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_suite` ADD COLUMN `draft_prompt_text` LONGTEXT DEFAULT NULL AFTER `draft_package_json`'
  )
);
PREPARE stmt FROM @product_image_suite_add_draft_prompt_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
