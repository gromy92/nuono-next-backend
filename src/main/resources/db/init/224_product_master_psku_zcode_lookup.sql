-- Product identity is logical_store_id + partner_sku.
-- Multiple PSKU records may truthfully point at the same current Noon Z code,
-- so the legacy store + sku_parent unique key must only remain a lookup index.

SET @pm_add_store_sku_parent_lookup := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND INDEX_NAME = 'idx_product_master_store_sku_parent_lookup'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD INDEX `idx_product_master_store_sku_parent_lookup` (`logical_store_id`, `sku_parent`, `is_deleted`)'
    )
);
PREPARE stmt FROM @pm_add_store_sku_parent_lookup;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pm_drop_legacy_store_sku_parent_unique := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND INDEX_NAME = 'uk_product_master_store_sku_parent'
              AND NON_UNIQUE = 0
        ),
        'ALTER TABLE `product_master` DROP INDEX `uk_product_master_store_sku_parent`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @pm_drop_legacy_store_sku_parent_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
