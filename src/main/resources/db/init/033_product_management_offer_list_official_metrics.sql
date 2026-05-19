-- Product management offer list official metrics.
-- Noon offer/list carries the current final selling price, barcode and product
-- performance numbers that are not always present in detail pricing payloads.

SET @pso_add_delivery_method := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'delivery_method'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `delivery_method` VARCHAR(40) DEFAULT NULL AFTER `offer_note`'
    )
);
PREPARE stmt FROM @pso_add_delivery_method;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_is_winning_buybox := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'is_winning_buybox'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `is_winning_buybox` BIT(1) DEFAULT NULL AFTER `delivery_method`'
    )
);
PREPARE stmt FROM @pso_add_is_winning_buybox;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_views_count := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'views_count'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `views_count` BIGINT DEFAULT NULL AFTER `fbp_stock`'
    )
);
PREPARE stmt FROM @pso_add_views_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_units_sold := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'units_sold'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `units_sold` BIGINT DEFAULT NULL AFTER `views_count`'
    )
);
PREPARE stmt FROM @pso_add_units_sold;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_sales_amount := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'sales_amount'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `sales_amount` DECIMAL(14,2) DEFAULT NULL AFTER `units_sold`'
    )
);
PREPARE stmt FROM @pso_add_sales_amount;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_sales_currency := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'sales_currency'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `sales_currency` VARCHAR(20) DEFAULT NULL AFTER `sales_amount`'
    )
);
PREPARE stmt FROM @pso_add_sales_currency;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_metrics_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND INDEX_NAME = 'idx_product_site_offer_metrics'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_metrics` (`site_id`, `views_count`, `units_sold`)'
    )
);
PREPARE stmt FROM @pso_add_metrics_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
