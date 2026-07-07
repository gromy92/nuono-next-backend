SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_logistics_shipment_allocation` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `source_type` VARCHAR(60) NOT NULL,
  `source_id` VARCHAR(128) NOT NULL,
  `source_line_id` BIGINT DEFAULT NULL,
  `target_store_code` VARCHAR(120) NOT NULL,
  `target_site_code` VARCHAR(40) NOT NULL,
  `partner_sku` VARCHAR(160) NOT NULL,
  `sku_parent` VARCHAR(160) DEFAULT NULL,
  `product_variant_id` BIGINT DEFAULT NULL,
  `purchase_batch_id` BIGINT DEFAULT NULL,
  `warehouse_shipping_batch_id` BIGINT DEFAULT NULL,
  `warehouse_shipping_batch_source_id` BIGINT DEFAULT NULL,
  `in_transit_batch_id` BIGINT NOT NULL,
  `in_transit_goods_line_id` BIGINT NOT NULL,
  `allocated_quantity` DECIMAL(18,4) NOT NULL,
  `allocation_unit` VARCHAR(40) NOT NULL DEFAULT 'piece',
  `match_method` VARCHAR(60) NOT NULL,
  `confirmation_status` VARCHAR(30) NOT NULL,
  `confidence_score` INT NOT NULL DEFAULT 0,
  `evidence_json` LONGTEXT DEFAULT NULL,
  `reject_reason` VARCHAR(500) DEFAULT NULL,
  `confirmed_by` BIGINT DEFAULT NULL,
  `confirmed_at` DATETIME DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_proc_logistics_alloc_source` (`owner_user_id`, `source_type`, `source_id`, `confirmation_status`, `is_deleted`),
  KEY `idx_proc_logistics_alloc_purchase_batch` (`owner_user_id`, `purchase_batch_id`, `confirmation_status`, `is_deleted`),
  KEY `idx_proc_logistics_alloc_in_transit_line` (`owner_user_id`, `in_transit_goods_line_id`, `confirmation_status`, `is_deleted`),
  KEY `idx_proc_logistics_alloc_store_sku` (`owner_user_id`, `target_store_code`, `target_site_code`, `partner_sku`, `confirmation_status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Confirmed procurement-to-in-transit logistics allocation facts';

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_logistics_shipment_allocation',
       GREATEST(COALESCE(MAX(`id`) + 1, 120000), 120000),
       NOW(),
       NOW()
FROM `procurement_logistics_shipment_allocation`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();
