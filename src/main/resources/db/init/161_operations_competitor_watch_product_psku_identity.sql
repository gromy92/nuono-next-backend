-- Align competitor watch product identity to owner/store/site + partner_sku.
-- psku_code is the external Noon pskuCode snapshot, not the system PSKU.

SET @ops_comp_watch_add_psku_code := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_watch_product'
              AND COLUMN_NAME = 'psku_code'
        ),
        'SELECT 1',
        'ALTER TABLE `operations_competitor_watch_product` ADD COLUMN `psku_code` VARCHAR(100) DEFAULT NULL AFTER `child_sku`'
    )
);
PREPARE stmt FROM @ops_comp_watch_add_psku_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE operations_competitor_watch_product wp
JOIN logical_store ls
  ON ls.owner_user_id = wp.owner_user_id
 AND ls.is_deleted = b'0'
JOIN logical_store_site lss
  ON lss.logical_store_id = ls.id
 AND lss.store_code = wp.store_code COLLATE utf8mb4_0900_ai_ci
 AND UPPER(lss.site) = UPPER(wp.site_code COLLATE utf8mb4_0900_ai_ci)
 AND lss.is_deleted = b'0'
JOIN product_master pm
  ON pm.logical_store_id = ls.id
 AND pm.sku_parent = wp.sku_parent COLLATE utf8mb4_0900_ai_ci
 AND pm.is_deleted = b'0'
JOIN product_variant pv
  ON pv.product_master_id = pm.id
 AND pv.partner_sku = wp.partner_sku COLLATE utf8mb4_0900_ai_ci
 AND pv.is_deleted = b'0'
JOIN product_site_offer pso
  ON pso.variant_id = pv.id
 AND pso.site_id = lss.id
 AND pso.is_deleted = b'0'
SET wp.psku_code = pso.psku_code
WHERE wp.is_deleted = b'0'
  AND NULLIF(TRIM(wp.psku_code), '') IS NULL
  AND NULLIF(TRIM(pso.psku_code), '') IS NOT NULL;

SET @ops_comp_watch_drop_active_psku_key := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_watch_product'
              AND INDEX_NAME = 'uk_ops_comp_watch_active_psku'
        ),
        'ALTER TABLE `operations_competitor_watch_product` DROP INDEX `uk_ops_comp_watch_active_psku`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_watch_drop_active_psku_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_watch_drop_active_psku_slot := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_watch_product'
              AND COLUMN_NAME = 'active_psku_slot'
        ),
        'ALTER TABLE `operations_competitor_watch_product` DROP COLUMN `active_psku_slot`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_watch_drop_active_psku_slot;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_watch_align_active_natural_slot := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_watch_product'
              AND COLUMN_NAME = 'active_natural_slot'
        ),
        'ALTER TABLE `operations_competitor_watch_product` MODIFY COLUMN `active_natural_slot` VARCHAR(512) GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b''0'' THEN CONCAT(`owner_user_id`, ''|'', `store_code`, ''|'', `site_code`, ''|'', `partner_sku`) ELSE NULL END) STORED',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_watch_align_active_natural_slot;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_watch_add_psku_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_watch_product'
              AND INDEX_NAME = 'idx_ops_comp_watch_psku'
        ),
        'SELECT 1',
        'ALTER TABLE `operations_competitor_watch_product` ADD KEY `idx_ops_comp_watch_psku` (`owner_user_id`, `store_code`, `site_code`, `psku_code`)'
    )
);
PREPARE stmt FROM @ops_comp_watch_add_psku_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
