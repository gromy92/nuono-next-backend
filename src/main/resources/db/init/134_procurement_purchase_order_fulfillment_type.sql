-- Record whether a purchase order item arrives at the warehouse or at the forwarder.

SET @add_procurement_purchase_order_item_fulfillment_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND COLUMN_NAME = 'fulfillment_type'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD COLUMN `fulfillment_type` VARCHAR(40) NOT NULL DEFAULT ''WAREHOUSE_RECEIPT'' AFTER `source_type`'
    )
);
PREPARE stmt FROM @add_procurement_purchase_order_item_fulfillment_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_procurement_purchase_order_item_fulfillment_source_name := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND COLUMN_NAME = 'fulfillment_source_name'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD COLUMN `fulfillment_source_name` VARCHAR(200) DEFAULT NULL AFTER `fulfillment_type`'
    )
);
PREPARE stmt FROM @add_procurement_purchase_order_item_fulfillment_source_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `procurement_purchase_order_item`
SET `fulfillment_type` = 'WAREHOUSE_RECEIPT'
WHERE `fulfillment_type` IS NULL OR TRIM(`fulfillment_type`) = '';

SET @add_procurement_purchase_order_item_fulfillment_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND INDEX_NAME = 'idx_procurement_purchase_order_item_fulfillment'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD KEY `idx_procurement_purchase_order_item_fulfillment` (`purchase_order_id`, `fulfillment_type`, `is_deleted`)'
    )
);
PREPARE stmt FROM @add_procurement_purchase_order_item_fulfillment_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
