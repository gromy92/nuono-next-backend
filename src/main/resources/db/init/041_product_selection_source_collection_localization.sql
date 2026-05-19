-- Product selection source collection localized title/detail fields.
-- Keeps the original source title as English and adds Chinese/Arabic detail fields.

SET NAMES utf8mb4;

SET @add_product_selection_source_collection_title_cn := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'source_title_cn'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `source_title_cn` VARCHAR(500) DEFAULT NULL AFTER `source_title`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_title_cn;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_title_ar := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'source_title_ar'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `source_title_ar` VARCHAR(500) DEFAULT NULL AFTER `source_title_cn`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_title_ar;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_selected_text_ar := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'selected_text_ar'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `selected_text_ar` VARCHAR(2000) DEFAULT NULL AFTER `selected_text`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_selected_text_ar;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_selection_source_collection`
SET `source_title_cn` = `selected_text`,
    `gmt_updated` = NOW()
WHERE `is_deleted` = b'0'
  AND (`source_title_cn` IS NULL OR TRIM(`source_title_cn`) = '')
  AND `selected_text` REGEXP '[一-龥]';
