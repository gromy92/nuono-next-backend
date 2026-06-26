-- Procurement purchase order logistics plan draft.
-- Scope: generated snapshot from purchase-order lines before forwarder recommendation or in-transit conversion.

CREATE TABLE IF NOT EXISTS `procurement_purchase_order_logistics_plan` (
    `id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `plan_no` VARCHAR(80) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    `transport_mode` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    `item_count` INT NOT NULL DEFAULT 0,
    `sku_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `missing_item_count` INT NOT NULL DEFAULT 0,
    `site_summary_json` TEXT DEFAULT NULL,
    `snapshot_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_purchase_order_logistics_plan_no` (`plan_no`),
    KEY `idx_procurement_purchase_order_logistics_plan_order` (`purchase_order_id`, `status`, `is_deleted`, `gmt_updated`),
    KEY `idx_procurement_purchase_order_logistics_plan_store` (`logical_store_id`, `status`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_purchase_order_logistics_plan', GREATEST(COALESCE(MAX(`id`) + 1, 250000), 250000), NOW(), NOW()
FROM `procurement_purchase_order_logistics_plan`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
