-- Store the structured dynamic-spec count for source collection records.

SET @add_product_selection_source_collection_spec_attribute_count := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_source_collection'
              AND COLUMN_NAME = 'spec_attribute_count'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_source_collection` ADD COLUMN `spec_attribute_count` INT NOT NULL DEFAULT 0 AFTER `spec_hints_json`'
    )
);
PREPARE stmt FROM @add_product_selection_source_collection_spec_attribute_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
