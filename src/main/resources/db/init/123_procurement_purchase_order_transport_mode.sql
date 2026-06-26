-- Add per-site transport mode to procurement purchase order quantities.

SET @add_procurement_item_site_transport_mode := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item_site'
              AND COLUMN_NAME = 'transport_mode'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item_site` ADD COLUMN `transport_mode` VARCHAR(20) NOT NULL DEFAULT ''UNSPECIFIED'' AFTER `offer_code`'
    )
);
PREPARE stmt FROM @add_procurement_item_site_transport_mode;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `procurement_purchase_order_item_site`
SET `transport_mode` = 'UNSPECIFIED'
WHERE `transport_mode` IS NULL OR TRIM(`transport_mode`) = '';

SET @drop_procurement_item_site_active_key := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item_site'
              AND INDEX_NAME = 'uk_procurement_purchase_order_item_site_active'
        ),
        'ALTER TABLE `procurement_purchase_order_item_site` DROP INDEX `uk_procurement_purchase_order_item_site_active`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_procurement_item_site_active_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `procurement_purchase_order_item_site`
    MODIFY COLUMN `active_site_slot` VARCHAR(220)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`purchase_order_item_id`, '|', `site_code`, '|', `transport_mode`)
                ELSE NULL
            END
        ) STORED;

SET @add_procurement_item_site_active_key := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item_site'
              AND INDEX_NAME = 'uk_procurement_purchase_order_item_site_active'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item_site` ADD UNIQUE KEY `uk_procurement_purchase_order_item_site_active` (`active_site_slot`)'
    )
);
PREPARE stmt FROM @add_procurement_item_site_active_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
