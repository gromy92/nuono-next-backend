SET @add_product_site_offer_pricing_method := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'pricing_method'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `pricing_method` VARCHAR(40) DEFAULT ''manual'' AFTER `price_max`'
    )
);
PREPARE stmt FROM @add_product_site_offer_pricing_method;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `product_site_offer` MODIFY COLUMN `pricing_method` VARCHAR(40) DEFAULT 'manual';

SET @add_product_site_offer_pricing_rule := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'pricing_rule'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `pricing_rule` VARCHAR(80) DEFAULT NULL AFTER `pricing_method`'
    )
);
PREPARE stmt FROM @add_product_site_offer_pricing_rule;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_site_offer_price_engine_min := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'price_engine_min'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `price_engine_min` DECIMAL(12,2) DEFAULT NULL AFTER `pricing_rule`'
    )
);
PREPARE stmt FROM @add_product_site_offer_price_engine_min;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_site_offer_price_engine_max := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'price_engine_max'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `price_engine_max` DECIMAL(12,2) DEFAULT NULL AFTER `price_engine_min`'
    )
);
PREPARE stmt FROM @add_product_site_offer_price_engine_max;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_site_offer`
SET `pricing_method` = 'manual',
    `pricing_rule` = NULL,
    `price_engine_min` = NULL,
    `price_engine_max` = NULL
WHERE `pricing_method` IS NULL
   OR `pricing_method` <> 'manual'
   OR `pricing_rule` IS NOT NULL
   OR `price_engine_min` IS NOT NULL
   OR `price_engine_max` IS NOT NULL;
