-- Procurement purchase-order logistics quote confirmation.
-- Scope: logistics quote round-trip on purchase orders and warehouse packing gate.

CREATE TABLE IF NOT EXISTS `procurement_purchase_order_logistics_quote_line` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `shipping_order_id` BIGINT DEFAULT NULL,
    `shipping_order_no` VARCHAR(80) DEFAULT NULL,
    `shipping_order_segment_id` BIGINT DEFAULT NULL,
    `shipping_order_line_id` BIGINT DEFAULT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `purchase_order_no` VARCHAR(60) NOT NULL,
    `purchase_order_title` VARCHAR(200) NOT NULL,
    `purchase_order_item_id` BIGINT NOT NULL,
    `purchase_order_item_site_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `sku_parent` VARCHAR(100) DEFAULT NULL,
    `partner_sku` VARCHAR(100) NOT NULL,
    `title_cache` VARCHAR(500) DEFAULT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `psku_code` VARCHAR(100) DEFAULT NULL,
    `yite_material` VARCHAR(20) DEFAULT NULL,
    `planned_transport_mode` VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
    `quantity` INT NOT NULL DEFAULT 0,
    `fulfillment_type` VARCHAR(40) NOT NULL DEFAULT 'WAREHOUSE_RECEIPT',
    `is_new_product` BIT(1) NOT NULL DEFAULT b'0',
    `quote_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING_QUOTE',
    `shipping_submit_status` VARCHAR(40) NOT NULL DEFAULT 'NOT_SUBMITTED',
    `forwarder_code` VARCHAR(80) DEFAULT NULL,
    `forwarder_name` VARCHAR(200) DEFAULT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `route_name` VARCHAR(200) DEFAULT NULL,
    `service_code` VARCHAR(120) DEFAULT NULL,
    `service_name` VARCHAR(200) DEFAULT NULL,
    `currency` VARCHAR(20) DEFAULT NULL,
    `unit_price` DECIMAL(18, 4) DEFAULT NULL,
    `billing_unit` VARCHAR(40) DEFAULT NULL,
    `estimated_amount` DECIMAL(18, 4) DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `exported_at` DATETIME DEFAULT NULL,
    `exported_by` BIGINT DEFAULT NULL,
    `confirmed_at` DATETIME DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `shipping_submitted_at` DATETIME DEFAULT NULL,
    `shipping_submitted_by` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_item_site_slot` VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(CAST(COALESCE(`shipping_order_id`, 0) AS CHAR), ':', CAST(`purchase_order_item_site_id` AS CHAR))
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_po_logistics_quote_active_item_site` (`active_item_site_slot`),
    KEY `idx_po_logistics_quote_order` (`purchase_order_id`, `quote_status`, `shipping_submit_status`, `is_deleted`),
    KEY `idx_po_logistics_quote_item_site` (`purchase_order_item_site_id`, `is_deleted`),
    KEY `idx_po_logistics_quote_owner` (`owner_user_id`, `shipping_submit_status`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @quote_add_yite_material := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND COLUMN_NAME = 'yite_material'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD COLUMN `yite_material` VARCHAR(20) DEFAULT NULL AFTER `psku_code`'
    )
);
PREPARE stmt FROM @quote_add_yite_material;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_purchase_order_logistics_quote_line',
       GREATEST(COALESCE(MAX(`id`) + 1, 280000), 280000),
       NOW(),
       NOW()
FROM `procurement_purchase_order_logistics_quote_line`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
