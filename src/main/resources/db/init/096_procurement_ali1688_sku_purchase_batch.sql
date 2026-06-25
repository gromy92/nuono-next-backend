-- Manual SKU purchase batch adjustments derived from assigned 1688 historical order lines.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_ali1688_sku_purchase_batch` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `target_store_code` VARCHAR(120) NOT NULL,
  `target_site_code` VARCHAR(40) NOT NULL DEFAULT '*',
  `sku_parent` VARCHAR(160) NOT NULL,
  `partner_sku` VARCHAR(160) DEFAULT NULL,
  `psku_code` VARCHAR(160) DEFAULT NULL,
  `batch_label` VARCHAR(120) NOT NULL,
  `batch_sequence` INT NOT NULL,
  `batch_type` VARCHAR(40) NOT NULL DEFAULT 'manual',
  `counted_quantity` INT NOT NULL,
  `counted_quantity_unit` VARCHAR(40) DEFAULT NULL,
  `counted_cost` DECIMAL(18, 4) NOT NULL,
  `component_count` INT DEFAULT NULL,
  `expected_component_count` INT DEFAULT NULL,
  `note` VARCHAR(500) DEFAULT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'active',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ali1688_sku_batch_owner_sku` (`owner_user_id`, `target_store_code`, `target_site_code`, `sku_parent`, `status`, `is_deleted`),
  KEY `idx_ali1688_sku_batch_updated` (`owner_user_id`, `gmt_updated`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='1688历史订单SKU采购批次人工调整';

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_sku_purchase_batch', GREATEST(COALESCE(MAX(`id`), 102000), 102000), NOW(), NOW()
FROM `procurement_ali1688_sku_purchase_batch`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

CREATE TABLE IF NOT EXISTS `procurement_ali1688_sku_purchase_batch_source` (
  `id` BIGINT NOT NULL,
  `batch_id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL,
  `item_id` BIGINT NOT NULL,
  `assignment_id` BIGINT NOT NULL,
  `component_sequence` INT DEFAULT NULL,
  `component_role` VARCHAR(120) DEFAULT NULL,
  `source_order_no` VARCHAR(120) DEFAULT NULL,
  `source_order_time` DATETIME DEFAULT NULL,
  `supplier_name` VARCHAR(300) DEFAULT NULL,
  `source_offer_id` VARCHAR(80) DEFAULT NULL,
  `source_sku_id` VARCHAR(120) DEFAULT NULL,
  `source_title` VARCHAR(500) DEFAULT NULL,
  `source_spec` VARCHAR(500) DEFAULT NULL,
  `source_quantity` DECIMAL(18, 4) DEFAULT NULL,
  `source_unit` VARCHAR(60) DEFAULT NULL,
  `source_unit_price` DECIMAL(18, 6) DEFAULT NULL,
  `source_amount` DECIMAL(18, 4) DEFAULT NULL,
  `source_quantity_per_counted_unit` DECIMAL(18, 6) DEFAULT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'active',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_ali1688_sku_batch_source_batch` (`owner_user_id`, `batch_id`, `status`, `is_deleted`),
  KEY `idx_ali1688_sku_batch_source_assignment` (`owner_user_id`, `assignment_id`, `status`, `is_deleted`),
  KEY `idx_ali1688_sku_batch_source_order` (`owner_user_id`, `order_id`, `item_id`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='1688历史订单SKU采购批次来源行';

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_sku_purchase_batch_source', GREATEST(COALESCE(MAX(`id`), 103000), 103000), NOW(), NOW()
FROM `procurement_ali1688_sku_purchase_batch_source`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();
