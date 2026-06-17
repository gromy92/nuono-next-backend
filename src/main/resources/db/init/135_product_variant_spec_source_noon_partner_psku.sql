SET @product_variant_spec_source_add_noon_partner_psku_code := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `product_variant_spec_source` ADD COLUMN `noon_partner_psku_code` VARCHAR(100) DEFAULT NULL AFTER `storage_type_code`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant_spec_source'
      AND COLUMN_NAME = 'noon_partner_psku_code'
);
PREPARE stmt FROM @product_variant_spec_source_add_noon_partner_psku_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
