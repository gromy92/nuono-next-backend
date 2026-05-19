-- Fixed common source specs for source collection records.

SET @add_product_selection_source_collection_brand_name := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'brand_name'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `brand_name` VARCHAR(200) DEFAULT NULL AFTER `shipping_from`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_brand_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_unit_count := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'unit_count'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `unit_count` VARCHAR(100) DEFAULT NULL AFTER `brand_name`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_unit_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_color_name := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'color_name'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `color_name` VARCHAR(200) DEFAULT NULL AFTER `unit_count`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_color_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
