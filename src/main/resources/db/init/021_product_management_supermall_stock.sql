SET @product_site_offer_add_supermall_stock := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'supermall_stock'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `supermall_stock` INT DEFAULT NULL AFTER `fbn_stock`'
    )
);
PREPARE stmt FROM @product_site_offer_add_supermall_stock;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
