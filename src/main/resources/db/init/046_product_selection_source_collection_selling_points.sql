-- Source channel selling points for source collection records.

SET @add_product_selection_source_collection_selling_points_en := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'source_selling_points_en_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `source_selling_points_en_json` TEXT DEFAULT NULL AFTER `source_description_ar`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_selling_points_en;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_selling_points_ar := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'source_selling_points_ar_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `source_selling_points_ar_json` TEXT DEFAULT NULL AFTER `source_selling_points_en_json`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_selling_points_ar;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
