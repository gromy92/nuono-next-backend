SET @product_variant_spec_source_add_storage_type_code := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `product_variant_spec_source` ADD COLUMN `storage_type_code` VARCHAR(60) DEFAULT NULL AFTER `product_weight_g`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant_spec_source'
      AND COLUMN_NAME = 'storage_type_code'
);
PREPARE stmt FROM @product_variant_spec_source_add_storage_type_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
