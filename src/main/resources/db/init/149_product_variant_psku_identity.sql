-- Canonical product identity:
--   business PSKU = product_variant.partner_sku
--   stable product key = logical_store_id + partner_sku
-- product_site_offer.psku_code remains the external Noon pskuCode / Noon product code.

CREATE TABLE IF NOT EXISTS `product_variant_identity_merge_map` (
    `duplicate_variant_id` BIGINT NOT NULL,
    `canonical_variant_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `partner_sku` VARCHAR(100) NOT NULL,
    `duplicate_product_master_id` BIGINT NOT NULL,
    `canonical_product_master_id` BIGINT NOT NULL,
    `target_product_master_id` BIGINT NOT NULL,
    `merge_reason` VARCHAR(100) NOT NULL DEFAULT 'same_store_partner_sku',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`duplicate_variant_id`),
    KEY `idx_pv_identity_merge_canonical` (`canonical_variant_id`),
    KEY `idx_pv_identity_merge_store_psku` (`logical_store_id`, `partner_sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_site_offer_identity_merge_map` (
    `duplicate_site_offer_id` BIGINT NOT NULL,
    `canonical_site_offer_id` BIGINT NOT NULL,
    `duplicate_variant_id` BIGINT NOT NULL,
    `canonical_variant_id` BIGINT NOT NULL,
    `site_id` BIGINT NOT NULL,
    `merge_reason` VARCHAR(100) NOT NULL DEFAULT 'variant_identity_merge',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`duplicate_site_offer_id`),
    KEY `idx_pso_identity_merge_canonical` (`canonical_site_offer_id`),
    KEY `idx_pso_identity_merge_variant` (`duplicate_variant_id`, `canonical_variant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @pv_add_logical_store_id := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_variant` ADD COLUMN `logical_store_id` BIGINT DEFAULT NULL AFTER `product_master_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant'
      AND COLUMN_NAME = 'logical_store_id'
);
PREPARE stmt FROM @pv_add_logical_store_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_variant` pv
JOIN `product_master` pm
  ON pm.id = pv.product_master_id
SET pv.logical_store_id = pm.logical_store_id
WHERE pv.logical_store_id IS NULL;

INSERT INTO `product_variant_identity_merge_map` (
    `duplicate_variant_id`,
    `canonical_variant_id`,
    `logical_store_id`,
    `partner_sku`,
    `duplicate_product_master_id`,
    `canonical_product_master_id`,
    `target_product_master_id`,
    `merge_reason`,
    `gmt_create`,
    `gmt_updated`
)
SELECT
    dup.id AS duplicate_variant_id,
    grouped.canonical_variant_id,
    grouped.logical_store_id,
    grouped.partner_sku,
    dup.product_master_id AS duplicate_product_master_id,
    canonical.product_master_id AS canonical_product_master_id,
    canonical.product_master_id AS target_product_master_id,
    'same_store_partner_sku',
    NOW(),
    NOW()
FROM `product_variant` dup
JOIN (
    SELECT
        pv.logical_store_id,
        pv.partner_sku,
        COALESCE(
            MIN(CASE WHEN pv.is_deleted = b'0' THEN pv.id END),
            MIN(pv.id)
        ) AS canonical_variant_id,
        COUNT(1) AS variant_count
    FROM `product_variant` pv
    WHERE pv.logical_store_id IS NOT NULL
      AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL
    GROUP BY pv.logical_store_id, pv.partner_sku
    HAVING COUNT(1) > 1
) grouped
  ON grouped.logical_store_id = dup.logical_store_id
 AND grouped.partner_sku = dup.partner_sku
JOIN `product_variant` canonical
  ON canonical.id = grouped.canonical_variant_id
WHERE dup.id <> grouped.canonical_variant_id
ON DUPLICATE KEY UPDATE
    `canonical_variant_id` = VALUES(`canonical_variant_id`),
    `logical_store_id` = VALUES(`logical_store_id`),
    `partner_sku` = VALUES(`partner_sku`),
    `duplicate_product_master_id` = VALUES(`duplicate_product_master_id`),
    `canonical_product_master_id` = VALUES(`canonical_product_master_id`),
    `target_product_master_id` = VALUES(`target_product_master_id`),
    `gmt_updated` = NOW();

INSERT INTO `product_site_offer_identity_merge_map` (
    `duplicate_site_offer_id`,
    `canonical_site_offer_id`,
    `duplicate_variant_id`,
    `canonical_variant_id`,
    `site_id`,
    `merge_reason`,
    `gmt_create`,
    `gmt_updated`
)
SELECT
    candidate.duplicate_site_offer_id,
    COALESCE(
        candidate.existing_canonical_site_offer_id,
        selected_duplicate_offer.canonical_duplicate_site_offer_id,
        candidate.duplicate_site_offer_id
    ) AS canonical_site_offer_id,
    candidate.duplicate_variant_id,
    candidate.canonical_variant_id,
    candidate.site_id,
    'variant_identity_merge',
    NOW(),
    NOW()
FROM (
    SELECT
        duplicate_pso.id AS duplicate_site_offer_id,
        merge_map.duplicate_variant_id,
        merge_map.canonical_variant_id,
        duplicate_pso.site_id,
        canonical_pso.id AS existing_canonical_site_offer_id
    FROM `product_site_offer` duplicate_pso
    JOIN `product_variant_identity_merge_map` merge_map
      ON merge_map.duplicate_variant_id = duplicate_pso.variant_id
    LEFT JOIN `product_site_offer` canonical_pso
      ON canonical_pso.variant_id = merge_map.canonical_variant_id
     AND canonical_pso.site_id = duplicate_pso.site_id
     AND canonical_pso.is_deleted = b'0'
    WHERE duplicate_pso.is_deleted = b'0'
) candidate
LEFT JOIN (
    SELECT
        inner_map.canonical_variant_id,
        inner_pso.site_id,
        MIN(inner_pso.id) AS canonical_duplicate_site_offer_id
    FROM `product_site_offer` inner_pso
    JOIN `product_variant_identity_merge_map` inner_map
      ON inner_map.duplicate_variant_id = inner_pso.variant_id
    LEFT JOIN `product_site_offer` existing_pso
      ON existing_pso.variant_id = inner_map.canonical_variant_id
     AND existing_pso.site_id = inner_pso.site_id
     AND existing_pso.is_deleted = b'0'
    WHERE inner_pso.is_deleted = b'0'
      AND existing_pso.id IS NULL
    GROUP BY inner_map.canonical_variant_id, inner_pso.site_id
) selected_duplicate_offer
  ON selected_duplicate_offer.canonical_variant_id = candidate.canonical_variant_id
 AND selected_duplicate_offer.site_id = candidate.site_id
ON DUPLICATE KEY UPDATE
    `canonical_site_offer_id` = VALUES(`canonical_site_offer_id`),
    `canonical_variant_id` = VALUES(`canonical_variant_id`),
    `site_id` = VALUES(`site_id`),
    `gmt_updated` = NOW();

UPDATE `product_variant_spec` duplicate_spec
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = duplicate_spec.variant_id
LEFT JOIN `product_variant_spec` canonical_spec
  ON canonical_spec.variant_id = merge_map.canonical_variant_id
 AND canonical_spec.is_deleted = b'0'
SET duplicate_spec.variant_id = CASE WHEN canonical_spec.id IS NULL THEN merge_map.canonical_variant_id ELSE duplicate_spec.variant_id END,
    duplicate_spec.is_deleted = CASE WHEN canonical_spec.id IS NULL THEN duplicate_spec.is_deleted ELSE b'1' END,
    duplicate_spec.gmt_updated = NOW();

UPDATE `product_variant_spec_source` duplicate_source
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = duplicate_source.variant_id
LEFT JOIN `product_variant_spec_source` canonical_source
  ON canonical_source.variant_id = merge_map.canonical_variant_id
 AND canonical_source.source_type = duplicate_source.source_type
 AND canonical_source.is_deleted = b'0'
SET duplicate_source.variant_id = CASE WHEN canonical_source.id IS NULL THEN merge_map.canonical_variant_id ELSE duplicate_source.variant_id END,
    duplicate_source.is_deleted = CASE WHEN canonical_source.id IS NULL THEN duplicate_source.is_deleted ELSE b'1' END,
    duplicate_source.gmt_updated = NOW();

SET @psku_identity_product_variant_logistics_profile_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `product_variant_logistics_profile` duplicate_profile
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = duplicate_profile.variant_id
LEFT JOIN `product_variant_logistics_profile` canonical_profile
  ON canonical_profile.variant_id = merge_map.canonical_variant_id
 AND canonical_profile.is_deleted = b''0''
SET duplicate_profile.variant_id = CASE WHEN canonical_profile.id IS NULL THEN merge_map.canonical_variant_id ELSE duplicate_profile.variant_id END,
    duplicate_profile.is_deleted = CASE WHEN canonical_profile.id IS NULL THEN duplicate_profile.is_deleted ELSE b''1'' END,
    duplicate_profile.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant_logistics_profile'
);
PREPARE stmt FROM @psku_identity_product_variant_logistics_profile_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_product_variant_logistics_profile_source_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `product_variant_logistics_profile_source` duplicate_source
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = duplicate_source.variant_id
LEFT JOIN `product_variant_logistics_profile_source` canonical_source
  ON canonical_source.variant_id = merge_map.canonical_variant_id
 AND canonical_source.source_type = duplicate_source.source_type
 AND canonical_source.is_deleted = b''0''
SET duplicate_source.variant_id = CASE WHEN canonical_source.id IS NULL THEN merge_map.canonical_variant_id ELSE duplicate_source.variant_id END,
    duplicate_source.is_deleted = CASE WHEN canonical_source.id IS NULL THEN duplicate_source.is_deleted ELSE b''1'' END,
    duplicate_source.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant_logistics_profile_source'
);
PREPARE stmt FROM @psku_identity_product_variant_logistics_profile_source_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_barcode` barcode
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = barcode.variant_id
SET barcode.variant_id = merge_map.canonical_variant_id,
    barcode.gmt_updated = NOW();

UPDATE `product_site_offer` duplicate_pso
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = duplicate_pso.id
 AND offer_map.duplicate_site_offer_id = offer_map.canonical_site_offer_id
SET duplicate_pso.variant_id = offer_map.canonical_variant_id,
    duplicate_pso.gmt_updated = NOW();

SET @psku_identity_procurement_purchase_order_item_site_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `procurement_purchase_order_item_site` item_site
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = item_site.product_site_offer_id
SET item_site.product_site_offer_id = offer_map.canonical_site_offer_id,
    item_site.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_purchase_order_item_site'
);
PREPARE stmt FROM @psku_identity_procurement_purchase_order_item_site_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `operations_competitor_watch_product` watch_product
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = watch_product.product_site_offer_id
SET watch_product.product_site_offer_id = offer_map.canonical_site_offer_id,
    watch_product.gmt_updated = NOW();

SET @psku_identity_official_warehouse_asn_line_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_asn_line` asn_line
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = asn_line.product_site_offer_id
SET asn_line.product_site_offer_id = offer_map.canonical_site_offer_id,
    asn_line.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_asn_line'
);
PREPARE stmt FROM @psku_identity_official_warehouse_asn_line_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_official_warehouse_stock_correction_event_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_stock_correction_event` correction
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = correction.product_site_offer_id
SET correction.product_site_offer_id = offer_map.canonical_site_offer_id,
    correction.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_stock_correction_event'
);
PREPARE stmt FROM @psku_identity_official_warehouse_stock_correction_event_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_official_warehouse_inventory_snapshot_line_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_inventory_snapshot_line` inventory_line
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = inventory_line.product_site_offer_id
SET inventory_line.product_site_offer_id = offer_map.canonical_site_offer_id,
    inventory_line.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_inventory_snapshot_line'
);
PREPARE stmt FROM @psku_identity_official_warehouse_inventory_snapshot_line_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_official_warehouse_inbound_receipt_line_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_inbound_receipt_line` receipt_line
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = receipt_line.product_site_offer_id
SET receipt_line.product_site_offer_id = offer_map.canonical_site_offer_id,
    receipt_line.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_inbound_receipt_line'
);
PREPARE stmt FROM @psku_identity_official_warehouse_inbound_receipt_line_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_public_detail_snapshot` snapshot
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = snapshot.product_site_offer_id
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = offer_map.duplicate_variant_id
LEFT JOIN `product_public_detail_snapshot` canonical_snapshot
  ON canonical_snapshot.product_master_id = merge_map.target_product_master_id
 AND canonical_snapshot.product_variant_id = offer_map.canonical_variant_id
 AND canonical_snapshot.site_code = snapshot.site_code
 AND canonical_snapshot.source_platform = snapshot.source_platform
 AND canonical_snapshot.fact_date = snapshot.fact_date
SET snapshot.product_master_id = CASE WHEN canonical_snapshot.id IS NULL THEN merge_map.target_product_master_id ELSE snapshot.product_master_id END,
    snapshot.product_variant_id = CASE WHEN canonical_snapshot.id IS NULL THEN offer_map.canonical_variant_id ELSE snapshot.product_variant_id END,
    snapshot.product_site_offer_id = CASE WHEN canonical_snapshot.id IS NULL THEN offer_map.canonical_site_offer_id ELSE snapshot.product_site_offer_id END,
    snapshot.is_latest = CASE WHEN canonical_snapshot.id IS NULL THEN snapshot.is_latest ELSE b'0' END,
    snapshot.is_deleted = CASE WHEN canonical_snapshot.id IS NULL THEN snapshot.is_deleted ELSE b'1' END,
    snapshot.gmt_updated = NOW();

UPDATE `product_site_offer` duplicate_pso
JOIN `product_site_offer_identity_merge_map` offer_map
  ON offer_map.duplicate_site_offer_id = duplicate_pso.id
 AND offer_map.duplicate_site_offer_id <> offer_map.canonical_site_offer_id
SET duplicate_pso.is_deleted = b'1',
    duplicate_pso.psku_code = CASE
        WHEN duplicate_pso.psku_code IS NULL OR duplicate_pso.psku_code = '' THEN duplicate_pso.psku_code
        WHEN duplicate_pso.psku_code LIKE CONCAT('%#merged#', duplicate_pso.id) THEN duplicate_pso.psku_code
        ELSE CONCAT(duplicate_pso.psku_code, '#merged#', duplicate_pso.id)
    END,
    duplicate_pso.offer_code = CASE
        WHEN duplicate_pso.offer_code IS NULL OR duplicate_pso.offer_code = '' THEN duplicate_pso.offer_code
        WHEN duplicate_pso.offer_code LIKE CONCAT('%#merged#', duplicate_pso.id) THEN duplicate_pso.offer_code
        ELSE CONCAT(duplicate_pso.offer_code, '#merged#', duplicate_pso.id)
    END,
    duplicate_pso.gmt_updated = NOW();

SET @psku_identity_procurement_purchase_order_item_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `procurement_purchase_order_item` item
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = item.product_variant_id
SET item.product_variant_id = merge_map.canonical_variant_id,
    item.product_master_id = merge_map.target_product_master_id,
    item.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_purchase_order_item'
);
PREPARE stmt FROM @psku_identity_procurement_purchase_order_item_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_procurement_fulfillment_balance_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `procurement_fulfillment_balance` balance
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = balance.product_variant_id
SET balance.product_variant_id = merge_map.canonical_variant_id,
    balance.product_master_id = merge_map.target_product_master_id,
    balance.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_fulfillment_balance'
);
PREPARE stmt FROM @psku_identity_procurement_fulfillment_balance_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_warehouse_shipping_batch_source_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `warehouse_shipping_batch_source` source
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = source.product_variant_id
SET source.product_variant_id = merge_map.canonical_variant_id,
    source.product_master_id = merge_map.target_product_master_id,
    source.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_shipping_batch_source'
);
PREPARE stmt FROM @psku_identity_warehouse_shipping_batch_source_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_warehouse_shipping_suggestion_line_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `warehouse_shipping_suggestion_line` line
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = line.product_variant_id
SET line.product_variant_id = merge_map.canonical_variant_id,
    line.product_master_id = merge_map.target_product_master_id,
    line.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_shipping_suggestion_line'
);
PREPARE stmt FROM @psku_identity_warehouse_shipping_suggestion_line_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_warehouse_outbound_order_line_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `warehouse_outbound_order_line` line
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = line.product_variant_id
SET line.product_variant_id = merge_map.canonical_variant_id,
    line.product_master_id = merge_map.target_product_master_id,
    line.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_outbound_order_line'
);
PREPARE stmt FROM @psku_identity_warehouse_outbound_order_line_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_warehouse_packing_box_item_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `warehouse_packing_box_item` item
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = item.product_variant_id
SET item.product_variant_id = merge_map.canonical_variant_id,
    item.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_packing_box_item'
);
PREPARE stmt FROM @psku_identity_warehouse_packing_box_item_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `operations_competitor_watch_product` watch_product
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = watch_product.product_variant_id
SET watch_product.product_variant_id = merge_map.canonical_variant_id,
    watch_product.product_master_id = merge_map.target_product_master_id,
    watch_product.gmt_updated = NOW();

SET @psku_identity_official_warehouse_asn_line_2_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_asn_line` asn_line
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = asn_line.product_variant_id
SET asn_line.product_variant_id = merge_map.canonical_variant_id,
    asn_line.product_master_id = merge_map.target_product_master_id,
    asn_line.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_asn_line'
);
PREPARE stmt FROM @psku_identity_official_warehouse_asn_line_2_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_official_warehouse_stock_correction_event_2_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_stock_correction_event` correction
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = correction.product_variant_id
SET correction.product_variant_id = merge_map.canonical_variant_id,
    correction.product_master_id = merge_map.target_product_master_id,
    correction.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_stock_correction_event'
);
PREPARE stmt FROM @psku_identity_official_warehouse_stock_correction_event_2_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_official_warehouse_inventory_snapshot_line_2_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_inventory_snapshot_line` inventory_line
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = inventory_line.product_variant_id
SET inventory_line.product_variant_id = merge_map.canonical_variant_id,
    inventory_line.product_master_id = merge_map.target_product_master_id,
    inventory_line.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_inventory_snapshot_line'
);
PREPARE stmt FROM @psku_identity_official_warehouse_inventory_snapshot_line_2_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_official_warehouse_inbound_receipt_line_2_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_inbound_receipt_line` receipt_line
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = receipt_line.product_variant_id
SET receipt_line.product_variant_id = merge_map.canonical_variant_id,
    receipt_line.product_master_id = merge_map.target_product_master_id,
    receipt_line.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_inbound_receipt_line'
);
PREPARE stmt FROM @psku_identity_official_warehouse_inbound_receipt_line_2_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_official_warehouse_asn_shipping_batch_link_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `official_warehouse_asn_shipping_batch_link` link
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = link.product_variant_id
SET link.product_variant_id = merge_map.canonical_variant_id,
    link.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_asn_shipping_batch_link'
);
PREPARE stmt FROM @psku_identity_official_warehouse_asn_shipping_batch_link_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_product_psku_lifecycle_archive_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `product_psku_lifecycle_archive` archive
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = archive.product_variant_id
SET archive.product_variant_id = merge_map.canonical_variant_id,
    archive.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_psku_lifecycle_archive'
);
PREPARE stmt FROM @psku_identity_product_psku_lifecycle_archive_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @psku_identity_procurement_archived_purchase_history_1_sql := (
    SELECT IF(
        COUNT(1) > 0,
        'UPDATE `procurement_archived_purchase_history` history
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = history.product_variant_id
SET history.product_variant_id = merge_map.canonical_variant_id,
    history.gmt_updated = NOW()',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_archived_purchase_history'
);
PREPARE stmt FROM @psku_identity_procurement_archived_purchase_history_1_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_variant` duplicate_variant
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.duplicate_variant_id = duplicate_variant.id
SET duplicate_variant.is_deleted = b'1',
    duplicate_variant.partner_sku = CASE
        WHEN duplicate_variant.partner_sku LIKE CONCAT('%#merged#', duplicate_variant.id) THEN duplicate_variant.partner_sku
        ELSE CONCAT(duplicate_variant.partner_sku, '#merged#', duplicate_variant.id)
    END,
    duplicate_variant.child_sku = CASE
        WHEN duplicate_variant.child_sku IS NULL OR duplicate_variant.child_sku = '' THEN duplicate_variant.child_sku
        WHEN duplicate_variant.child_sku LIKE CONCAT('%#merged#', duplicate_variant.id) THEN duplicate_variant.child_sku
        ELSE CONCAT(duplicate_variant.child_sku, '#merged#', duplicate_variant.id)
    END,
    duplicate_variant.gmt_updated = NOW();

UPDATE `product_variant` canonical_variant
JOIN `product_variant_identity_merge_map` merge_map
  ON merge_map.canonical_variant_id = canonical_variant.id
SET canonical_variant.product_master_id = merge_map.target_product_master_id,
    canonical_variant.logical_store_id = merge_map.logical_store_id,
    canonical_variant.gmt_updated = NOW();

SET @pv_modify_logical_store_id := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_variant` MODIFY COLUMN `logical_store_id` BIGINT NOT NULL',
        'SELECT 1'
    )
    FROM `product_variant`
    WHERE logical_store_id IS NULL
);
PREPARE stmt FROM @pv_modify_logical_store_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pv_drop_master_partner_unique := (
    SELECT IF(
        COUNT(1) > 0,
        'ALTER TABLE `product_variant` DROP INDEX `uk_product_variant_master_partner_sku`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant'
      AND INDEX_NAME = 'uk_product_variant_master_partner_sku'
);
PREPARE stmt FROM @pv_drop_master_partner_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pv_add_store_partner_unique := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_variant` ADD UNIQUE KEY `uk_product_variant_store_partner_sku` (`logical_store_id`, `partner_sku`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant'
      AND INDEX_NAME = 'uk_product_variant_store_partner_sku'
);
PREPARE stmt FROM @pv_add_store_partner_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pv_add_store_index := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_variant` ADD KEY `idx_product_variant_store_id` (`logical_store_id`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant'
      AND INDEX_NAME = 'idx_product_variant_store_id'
);
PREPARE stmt FROM @pv_add_store_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
