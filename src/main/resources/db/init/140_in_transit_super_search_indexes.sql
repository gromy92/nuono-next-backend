-- Speed up in-transit super search product matching by PSKU.
-- Existing product_variant indexes start with product_master_id, while super search resolves
-- product identity from in_transit_goods_line.psku / legacy PSKU aliases.

SET @index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant'
      AND INDEX_NAME = 'idx_product_variant_partner_sku_lookup'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE product_variant ADD INDEX idx_product_variant_partner_sku_lookup (partner_sku, is_deleted, product_master_id)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
