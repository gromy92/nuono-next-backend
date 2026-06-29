-- Product-level forwarder declaration attributes reused by shipping order snapshots.

CREATE TABLE IF NOT EXISTS `product_forwarder_declaration_attribute` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `product_master_id` BIGINT NOT NULL,
  `product_variant_id` BIGINT NOT NULL,
  `barcode` VARCHAR(100) DEFAULT NULL,
  `forwarder_code` VARCHAR(80) NOT NULL,
  `attribute_code` VARCHAR(80) NOT NULL,
  `attribute_value` VARCHAR(200) DEFAULT NULL,
  `source_shipping_order_id` BIGINT DEFAULT NULL,
  `source_shipping_order_line_id` BIGINT DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `active_slot` VARCHAR(255) GENERATED ALWAYS AS (
    CASE
      WHEN `is_deleted` = b'0' THEN CONCAT(
        `owner_user_id`, ':', `product_variant_id`, ':', `forwarder_code`, ':', `attribute_code`
      )
      ELSE NULL
    END
  ) STORED,
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_forwarder_declaration_attribute_active` (
    `owner_user_id`, `product_variant_id`, `forwarder_code`, `attribute_code`, `active_slot`
  ),
  KEY `idx_product_forwarder_declaration_attribute_lookup` (
    `owner_user_id`, `forwarder_code`, `attribute_code`, `product_variant_id`, `is_deleted`
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_forwarder_declaration_attribute` (
  `id`, `owner_user_id`, `product_master_id`, `product_variant_id`, `barcode`,
  `forwarder_code`, `attribute_code`, `attribute_value`,
  `source_shipping_order_id`, `source_shipping_order_line_id`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  310000 + sol.`id`,
  sol.`owner_user_id`,
  sol.`product_master_id`,
  sol.`product_variant_id`,
  (
    SELECT COALESCE(MAX(CASE WHEN pb.`is_primary` = b'1' THEN pb.`barcode` END), MAX(pb.`barcode`))
    FROM `product_barcode` pb
    WHERE pb.`variant_id` = sol.`product_variant_id`
      AND pb.`is_deleted` = b'0'
  ),
  'YT',
  'YITE_MATERIAL',
  TRIM(sol.`yite_material`),
  sol.`shipping_order_id`,
  sol.`id`,
  b'0',
  sol.`created_by`,
  sol.`updated_by`,
  sol.`gmt_create`,
  sol.`gmt_updated`
FROM `procurement_shipping_order_line` sol
WHERE sol.`is_deleted` = b'0'
  AND sol.`product_master_id` IS NOT NULL
  AND sol.`product_variant_id` IS NOT NULL
  AND sol.`yite_material` IS NOT NULL
  AND TRIM(sol.`yite_material`) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM `procurement_shipping_order_line` newer
    WHERE newer.`is_deleted` = b'0'
      AND newer.`owner_user_id` = sol.`owner_user_id`
      AND newer.`product_variant_id` = sol.`product_variant_id`
      AND newer.`yite_material` IS NOT NULL
      AND TRIM(newer.`yite_material`) <> ''
      AND (
        newer.`gmt_updated` > sol.`gmt_updated`
        OR (newer.`gmt_updated` = sol.`gmt_updated` AND newer.`id` > sol.`id`)
      )
  )
ON DUPLICATE KEY UPDATE
  `product_master_id` = VALUES(`product_master_id`),
  `barcode` = VALUES(`barcode`),
  `attribute_value` = VALUES(`attribute_value`),
  `source_shipping_order_id` = VALUES(`source_shipping_order_id`),
  `source_shipping_order_line_id` = VALUES(`source_shipping_order_line_id`),
  `is_deleted` = b'0',
  `updated_by` = VALUES(`updated_by`),
  `gmt_updated` = VALUES(`gmt_updated`);

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT
  'product_forwarder_declaration_attribute',
  GREATEST(COALESCE(MAX(`id`) + 1, 310000), 310000),
  NOW(),
  NOW()
FROM `product_forwarder_declaration_attribute`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(
    `next_id`,
    COALESCE((SELECT MAX(pfda.`id`) + 1 FROM `product_forwarder_declaration_attribute` pfda), 310000)
  ),
  `gmt_updated` = NOW();
