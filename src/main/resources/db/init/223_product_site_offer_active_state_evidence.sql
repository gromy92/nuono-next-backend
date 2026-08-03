SET @add_product_site_offer_active_state_source := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'active_state_source'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `active_state_source` VARCHAR(80) DEFAULT NULL AFTER `is_active`'
    )
);
PREPARE stmt FROM @add_product_site_offer_active_state_source;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_site_offer_active_state_synced_at := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'active_state_synced_at'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `active_state_synced_at` DATETIME DEFAULT NULL AFTER `active_state_source`'
    )
);
PREPARE stmt FROM @add_product_site_offer_active_state_synced_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_site_offer_replenishment_coverage_index := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND INDEX_NAME = 'idx_product_site_offer_replenishment_coverage'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_replenishment_coverage` (`logical_store_id`, `site_id`, `maintenance_enabled`, `is_active`)'
    )
);
PREPARE stmt FROM @add_product_site_offer_replenishment_coverage_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
