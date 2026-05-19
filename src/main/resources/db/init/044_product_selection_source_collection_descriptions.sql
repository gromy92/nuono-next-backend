-- Source channel long descriptions for source collection records.

SET @add_product_selection_source_collection_description_en := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'source_description_en'
              AND DATA_TYPE IN ('text', 'mediumtext', 'longtext')
        ),
        'SELECT 1',
        IF(
            EXISTS(
                SELECT 1
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'product_selection_source_collection'
                  AND COLUMN_NAME = 'source_description_en'
            ),
            'ALTER TABLE `product_selection_source_collection` MODIFY COLUMN `source_description_en` TEXT DEFAULT NULL',
            'ALTER TABLE `product_selection_source_collection` ADD COLUMN `source_description_en` TEXT DEFAULT NULL AFTER `spec_attribute_count`'
        )
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_description_en;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_description_ar := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'source_description_ar'
              AND DATA_TYPE IN ('text', 'mediumtext', 'longtext')
        ),
        'SELECT 1',
        IF(
            EXISTS(
                SELECT 1
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'product_selection_source_collection'
                  AND COLUMN_NAME = 'source_description_ar'
            ),
            'ALTER TABLE `product_selection_source_collection` MODIFY COLUMN `source_description_ar` TEXT DEFAULT NULL',
            'ALTER TABLE `product_selection_source_collection` ADD COLUMN `source_description_ar` TEXT DEFAULT NULL AFTER `source_description_en`'
        )
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_description_ar;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_selection_source_collection`
SET `source_description_en` = `selected_text`
WHERE (`source_description_en` IS NULL OR TRIM(`source_description_en`) = '')
  AND `selected_text` IS NOT NULL
  AND TRIM(`selected_text`) <> ''
  AND `selected_text` REGEXP '[A-Za-z]{3,}'
  AND (
      `source_title_cn` IS NULL
      OR TRIM(`source_title_cn`) = ''
      OR TRIM(`source_title_cn`) <> TRIM(`selected_text`)
  );

UPDATE `product_selection_source_collection`
SET `source_description_ar` = `selected_text_ar`
WHERE (`source_description_ar` IS NULL OR TRIM(`source_description_ar`) = '')
  AND `selected_text_ar` IS NOT NULL
  AND TRIM(`selected_text_ar`) <> '';
