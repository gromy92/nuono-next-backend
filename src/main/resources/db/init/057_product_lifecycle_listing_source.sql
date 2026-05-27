-- Product lifecycle listing-date source support.
-- Official listing time is stored at site offer grain; resolved lifecycle listing date is stored in product_lifecycle_current_state.

SET @product_site_offer_add_official_listing_at := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'official_listing_at'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `official_listing_at` DATETIME DEFAULT NULL AFTER `status_code`'
    )
);
PREPARE stmt FROM @product_site_offer_add_official_listing_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_official_listing_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND INDEX_NAME = 'idx_product_site_offer_official_listing'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_official_listing` (`site_id`, `official_listing_at`)'
    )
);
PREPARE stmt FROM @product_site_offer_add_official_listing_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
