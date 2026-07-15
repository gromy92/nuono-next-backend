-- Persist public marketplace breadcrumb/category links collected for manual-selection materials.

SET @add_product_selection_category_links := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'category_links_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `category_links_json` TEXT DEFAULT NULL AFTER `spec_hints_json`'
    )
);

PREPARE stmt FROM @add_product_selection_category_links;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
