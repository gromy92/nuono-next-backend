-- Remove product-level competitor confirmation fields.

SET NAMES utf8mb4;

SET @ops_comp_watch_product_drop_competitors_confirmed_at := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_watch_product'
              AND COLUMN_NAME = 'competitors_confirmed_at'
        ),
        'ALTER TABLE `operations_competitor_watch_product` DROP COLUMN `competitors_confirmed_at`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_watch_product_drop_competitors_confirmed_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_watch_product_drop_competitors_confirmed_by := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_watch_product'
              AND COLUMN_NAME = 'competitors_confirmed_by'
        ),
        'ALTER TABLE `operations_competitor_watch_product` DROP COLUMN `competitors_confirmed_by`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_watch_product_drop_competitors_confirmed_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
