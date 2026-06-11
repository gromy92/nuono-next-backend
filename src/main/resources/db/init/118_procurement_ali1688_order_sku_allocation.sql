SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_sku_allocation` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `authorization_id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL,
  `item_id` BIGINT DEFAULT NULL,
  `assignment_id` BIGINT DEFAULT NULL,
  `source_line_key` VARCHAR(300) NOT NULL,
  `source_line_label` VARCHAR(300) DEFAULT NULL,
  `source_quantity` DECIMAL(18, 4) DEFAULT NULL,
  `source_unit` VARCHAR(60) DEFAULT NULL,
  `source_unit_price` DECIMAL(18, 4) DEFAULT NULL,
  `source_amount` DECIMAL(18, 4) DEFAULT NULL,
  `consumed_source_quantity` DECIMAL(18, 4) NOT NULL,
  `target_store_code` VARCHAR(120) NOT NULL,
  `target_site_code` VARCHAR(40) DEFAULT NULL,
  `sku_parent` VARCHAR(160) NOT NULL,
  `partner_sku` VARCHAR(160) DEFAULT NULL,
  `psku_code` VARCHAR(160) DEFAULT NULL,
  `product_title` VARCHAR(1000) DEFAULT NULL,
  `product_image_url` VARCHAR(1000) DEFAULT NULL,
  `sku_quantity` DECIMAL(18, 4) NOT NULL,
  `sku_unit` VARCHAR(60) DEFAULT NULL,
  `pack_size` DECIMAL(18, 4) DEFAULT NULL,
  `allocated_cost` DECIMAL(18, 4) NOT NULL,
  `allocation_basis` VARCHAR(80) NOT NULL,
  `evidence_text` VARCHAR(1000) DEFAULT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'active',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `active_allocation_hash` CHAR(64)
      GENERATED ALWAYS AS (
        CASE
          WHEN `status` = 'active' AND `is_deleted` = b'0' THEN SHA2(CONCAT_WS(
            '|',
            `owner_user_id`,
            `order_id`,
            COALESCE(`item_id`, 0),
            `source_line_key`,
            `target_store_code`,
            COALESCE(`target_site_code`, '*'),
            `sku_parent`,
            COALESCE(`partner_sku`, ''),
            COALESCE(`psku_code`, '')
          ), 256)
          ELSE NULL
        END
      ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ali1688_order_sku_allocation_active` (`active_allocation_hash`),
  KEY `idx_ali1688_order_sku_alloc_owner_order` (`owner_user_id`, `order_id`, `status`, `is_deleted`),
  KEY `idx_ali1688_order_sku_alloc_owner_item` (`owner_user_id`, `item_id`, `status`, `is_deleted`),
  KEY `idx_ali1688_order_sku_alloc_owner_sku` (`owner_user_id`, `target_store_code`, `target_site_code`, `sku_parent`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='1688 historical order SKU allocation facts';

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_sku_allocation', GREATEST(COALESCE(MAX(`id`), 104000), 104000), NOW(), NOW()
FROM `procurement_ali1688_order_sku_allocation`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();
