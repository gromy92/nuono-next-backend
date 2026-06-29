-- Owner-level procurement shipping orders.
-- Scope: merge multiple purchase orders into one shipping order before receiving.

CREATE TABLE IF NOT EXISTS `procurement_shipping_order` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `shipping_order_no` VARCHAR(80) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    `purchase_order_count` INT NOT NULL DEFAULT 0,
    `line_count` INT NOT NULL DEFAULT 0,
    `sku_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `store_summary_json` TEXT DEFAULT NULL,
    `site_summary_json` TEXT DEFAULT NULL,
    `transport_summary_json` TEXT DEFAULT NULL,
    `quote_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING_QUOTE',
    `shipping_submit_status` VARCHAR(40) NOT NULL DEFAULT 'NOT_SUBMITTED',
    `forwarder_code` VARCHAR(80) DEFAULT NULL,
    `forwarder_name` VARCHAR(200) DEFAULT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `route_name` VARCHAR(200) DEFAULT NULL,
    `service_code` VARCHAR(120) DEFAULT NULL,
    `service_name` VARCHAR(200) DEFAULT NULL,
    `submitted_at` DATETIME DEFAULT NULL,
    `submitted_by` BIGINT DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_shipping_order_no` (`shipping_order_no`),
    KEY `idx_procurement_shipping_order_owner` (`owner_user_id`, `status`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_shipping_order_segment` (
    `id` BIGINT NOT NULL,
    `shipping_order_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `segment_no` VARCHAR(120) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `transport_mode` VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
    `forwarder_code` VARCHAR(80) DEFAULT NULL,
    `forwarder_name` VARCHAR(200) DEFAULT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `route_name` VARCHAR(200) DEFAULT NULL,
    `service_code` VARCHAR(120) DEFAULT NULL,
    `service_name` VARCHAR(200) DEFAULT NULL,
    `quote_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING_QUOTE',
    `shipping_submit_status` VARCHAR(40) NOT NULL DEFAULT 'NOT_SUBMITTED',
    `line_count` INT NOT NULL DEFAULT 0,
    `sku_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `missing_yite_material_count` INT NOT NULL DEFAULT 0,
    `submitted_at` DATETIME DEFAULT NULL,
    `submitted_by` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_shipping_order_segment_no` (`segment_no`),
    KEY `idx_procurement_shipping_order_segment_order` (`shipping_order_id`, `is_deleted`, `site_code`, `transport_mode`),
    KEY `idx_procurement_shipping_order_segment_status` (`owner_user_id`, `quote_status`, `shipping_submit_status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_shipping_order_line` (
    `id` BIGINT NOT NULL,
    `shipping_order_id` BIGINT NOT NULL,
    `shipping_order_segment_id` BIGINT DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `source_store_code` VARCHAR(100) NOT NULL,
    `source_store_name` VARCHAR(200) DEFAULT NULL,
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
    `image_url_cache` VARCHAR(1000) DEFAULT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `psku_code` VARCHAR(100) DEFAULT NULL,
    `yite_material` VARCHAR(20) DEFAULT NULL,
    `planned_transport_mode` VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
    `quantity` INT NOT NULL DEFAULT 0,
    `fulfillment_type` VARCHAR(40) NOT NULL DEFAULT 'WAREHOUSE_RECEIPT',
    `quote_line_id` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_item_site_slot` VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(CAST(`shipping_order_id` AS CHAR), ':', CAST(`purchase_order_item_site_id` AS CHAR))
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shipping_order_line_active_item_site` (`active_item_site_slot`),
    KEY `idx_shipping_order_line_order` (`shipping_order_id`, `is_deleted`),
    KEY `idx_shipping_order_line_segment` (`shipping_order_segment_id`, `is_deleted`),
    KEY `idx_shipping_order_line_purchase_order` (`purchase_order_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @shipping_line_add_segment_id := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_shipping_order_line'
              AND COLUMN_NAME = 'shipping_order_segment_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_shipping_order_line` ADD COLUMN `shipping_order_segment_id` BIGINT DEFAULT NULL AFTER `shipping_order_id`'
    )
);
PREPARE stmt FROM @shipping_line_add_segment_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @shipping_line_drop_item_site_unique := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_shipping_order_line'
              AND INDEX_NAME = 'uk_shipping_order_line_active_item_site'
        ),
        'ALTER TABLE `procurement_shipping_order_line` DROP INDEX `uk_shipping_order_line_active_item_site`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @shipping_line_drop_item_site_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `procurement_shipping_order_line`
    MODIFY COLUMN `active_item_site_slot` VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(CAST(`shipping_order_id` AS CHAR), ':', CAST(`purchase_order_item_site_id` AS CHAR))
                ELSE NULL
            END
        ) STORED;

SET @shipping_line_add_item_site_unique := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_shipping_order_line'
              AND INDEX_NAME = 'uk_shipping_order_line_active_item_site'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_shipping_order_line` ADD UNIQUE KEY `uk_shipping_order_line_active_item_site` (`active_item_site_slot`)'
    )
);
PREPARE stmt FROM @shipping_line_add_item_site_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @shipping_line_add_segment_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_shipping_order_line'
              AND INDEX_NAME = 'idx_shipping_order_line_segment'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_shipping_order_line` ADD KEY `idx_shipping_order_line_segment` (`shipping_order_segment_id`, `is_deleted`)'
    )
);
PREPARE stmt FROM @shipping_line_add_segment_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @shipping_line_add_yite_material := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_shipping_order_line'
              AND COLUMN_NAME = 'yite_material'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_shipping_order_line` ADD COLUMN `yite_material` VARCHAR(20) DEFAULT NULL AFTER `psku_code`'
    )
);
PREPARE stmt FROM @shipping_line_add_yite_material;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @quote_add_shipping_order_id := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND COLUMN_NAME = 'shipping_order_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD COLUMN `shipping_order_id` BIGINT DEFAULT NULL AFTER `logical_store_id`'
    )
);
PREPARE stmt FROM @quote_add_shipping_order_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @quote_add_shipping_order_no := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND COLUMN_NAME = 'shipping_order_no'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD COLUMN `shipping_order_no` VARCHAR(80) DEFAULT NULL AFTER `shipping_order_id`'
    )
);
PREPARE stmt FROM @quote_add_shipping_order_no;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @quote_add_shipping_order_segment_id := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND COLUMN_NAME = 'shipping_order_segment_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD COLUMN `shipping_order_segment_id` BIGINT DEFAULT NULL AFTER `shipping_order_no`'
    )
);
PREPARE stmt FROM @quote_add_shipping_order_segment_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @quote_add_shipping_order_line_id := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND COLUMN_NAME = 'shipping_order_line_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD COLUMN `shipping_order_line_id` BIGINT DEFAULT NULL AFTER `shipping_order_segment_id`'
    )
);
PREPARE stmt FROM @quote_add_shipping_order_line_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @quote_drop_item_site_unique := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND INDEX_NAME = 'uk_po_logistics_quote_active_item_site'
        ),
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` DROP INDEX `uk_po_logistics_quote_active_item_site`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @quote_drop_item_site_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `procurement_purchase_order_logistics_quote_line`
    MODIFY COLUMN `active_item_site_slot` VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(CAST(COALESCE(`shipping_order_id`, 0) AS CHAR), ':', CAST(`purchase_order_item_site_id` AS CHAR))
                ELSE NULL
            END
        ) STORED;

SET @quote_add_item_site_unique := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND INDEX_NAME = 'uk_po_logistics_quote_active_item_site'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD UNIQUE KEY `uk_po_logistics_quote_active_item_site` (`active_item_site_slot`)'
    )
);
PREPARE stmt FROM @quote_add_item_site_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @quote_add_shipping_order_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND INDEX_NAME = 'idx_po_logistics_quote_shipping_order'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD KEY `idx_po_logistics_quote_shipping_order` (`shipping_order_id`, `quote_status`, `shipping_submit_status`, `is_deleted`)'
    )
);
PREPARE stmt FROM @quote_add_shipping_order_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `procurement_shipping_order_segment` (
    `id`, `shipping_order_id`, `owner_user_id`, `segment_no`, `site_code`, `transport_mode`,
    `forwarder_code`, `forwarder_name`, `route_code`, `route_name`, `service_code`, `service_name`,
    `quote_status`, `shipping_submit_status`, `line_count`, `sku_count`, `total_quantity`,
    `missing_yite_material_count`, `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
    (@shipping_order_segment_backfill_base := @shipping_order_segment_backfill_base + 1) AS `id`,
    grouped.`shipping_order_id`,
    grouped.`owner_user_id`,
    CONCAT(grouped.`shipping_order_no`, '-', grouped.`site_code`, '-', grouped.`transport_mode`) AS `segment_no`,
    grouped.`site_code`,
    grouped.`transport_mode`,
    grouped.`forwarder_code`,
    grouped.`forwarder_name`,
    grouped.`route_code`,
    grouped.`route_name`,
    grouped.`service_code`,
    grouped.`service_name`,
    grouped.`quote_status`,
    grouped.`shipping_submit_status`,
    grouped.`line_count`,
    grouped.`sku_count`,
    grouped.`total_quantity`,
    grouped.`missing_yite_material_count`,
    b'0',
    grouped.`created_by`,
    grouped.`updated_by`,
    NOW(),
    NOW()
FROM (
    SELECT
        so.`id` AS `shipping_order_id`,
        so.`owner_user_id`,
        so.`shipping_order_no`,
        COALESCE(NULLIF(sol.`site_code`, ''), 'UNSPECIFIED') AS `site_code`,
        COALESCE(NULLIF(sol.`planned_transport_mode`, ''), 'UNSPECIFIED') AS `transport_mode`,
        COALESCE(MAX(NULLIF(quote.`forwarder_code`, '')), MAX(NULLIF(so.`forwarder_code`, ''))) AS `forwarder_code`,
        COALESCE(MAX(NULLIF(quote.`forwarder_name`, '')), MAX(NULLIF(so.`forwarder_name`, ''))) AS `forwarder_name`,
        COALESCE(MAX(NULLIF(quote.`route_code`, '')), MAX(NULLIF(so.`route_code`, ''))) AS `route_code`,
        COALESCE(MAX(NULLIF(quote.`route_name`, '')), MAX(NULLIF(so.`route_name`, ''))) AS `route_name`,
        COALESCE(MAX(NULLIF(quote.`service_code`, '')), MAX(NULLIF(so.`service_code`, ''))) AS `service_code`,
        COALESCE(MAX(NULLIF(quote.`service_name`, '')), MAX(NULLIF(so.`service_name`, ''))) AS `service_name`,
        CASE
            WHEN SUM(CASE WHEN quote.`id` IS NULL OR quote.`quote_status` = 'PENDING_QUOTE' THEN 1 ELSE 0 END) > 0 THEN 'PENDING_QUOTE'
            WHEN SUM(CASE WHEN quote.`quote_status` = 'EXPORTED' THEN 1 ELSE 0 END) > 0 THEN 'EXPORTED'
            ELSE 'CONFIRMED'
        END AS `quote_status`,
        CASE
            WHEN SUM(CASE WHEN quote.`shipping_submit_status` = 'SUBMITTED' THEN 1 ELSE 0 END) = COUNT(*) THEN 'SUBMITTED'
            WHEN SUM(CASE WHEN quote.`shipping_submit_status` = 'SUBMITTED' THEN 1 ELSE 0 END) > 0 THEN 'PARTIAL_SUBMITTED'
            ELSE 'NOT_SUBMITTED'
        END AS `shipping_submit_status`,
        COUNT(*) AS `line_count`,
        COUNT(DISTINCT sol.`product_variant_id`) AS `sku_count`,
        SUM(sol.`quantity`) AS `total_quantity`,
        SUM(
            CASE
                WHEN UPPER(COALESCE(quote.`forwarder_code`, so.`forwarder_code`, '')) = 'YT'
                  AND (sol.`yite_material` IS NULL OR TRIM(sol.`yite_material`) = '')
                THEN 1
                ELSE 0
            END
        ) AS `missing_yite_material_count`,
        COALESCE(MIN(sol.`created_by`), so.`created_by`) AS `created_by`,
        COALESCE(MAX(sol.`updated_by`), so.`updated_by`) AS `updated_by`
    FROM `procurement_shipping_order_line` sol
    INNER JOIN `procurement_shipping_order` so
      ON so.`id` = sol.`shipping_order_id`
     AND so.`is_deleted` = b'0'
    LEFT JOIN `procurement_purchase_order_logistics_quote_line` quote
      ON quote.`shipping_order_id` = sol.`shipping_order_id`
     AND quote.`purchase_order_item_site_id` = sol.`purchase_order_item_site_id`
     AND quote.`is_deleted` = b'0'
    LEFT JOIN `procurement_shipping_order_segment` existing_segment
      ON existing_segment.`shipping_order_id` = sol.`shipping_order_id`
     AND existing_segment.`site_code` = COALESCE(NULLIF(sol.`site_code`, ''), 'UNSPECIFIED')
     AND existing_segment.`transport_mode` = COALESCE(NULLIF(sol.`planned_transport_mode`, ''), 'UNSPECIFIED')
     AND existing_segment.`is_deleted` = b'0'
    WHERE sol.`is_deleted` = b'0'
      AND existing_segment.`id` IS NULL
    GROUP BY so.`id`, so.`owner_user_id`, so.`shipping_order_no`,
             COALESCE(NULLIF(sol.`site_code`, ''), 'UNSPECIFIED'),
             COALESCE(NULLIF(sol.`planned_transport_mode`, ''), 'UNSPECIFIED')
) grouped
CROSS JOIN (
    SELECT @shipping_order_segment_backfill_base := GREATEST(COALESCE(MAX(`id`), 292000), 292000)
    FROM `procurement_shipping_order_segment`
) sequence_seed
ORDER BY grouped.`shipping_order_id`, grouped.`site_code`, grouped.`transport_mode`;

UPDATE `procurement_shipping_order_line` sol
INNER JOIN `procurement_shipping_order_segment` segment
  ON segment.`shipping_order_id` = sol.`shipping_order_id`
 AND segment.`site_code` = COALESCE(NULLIF(sol.`site_code`, ''), 'UNSPECIFIED')
 AND segment.`transport_mode` = COALESCE(NULLIF(sol.`planned_transport_mode`, ''), 'UNSPECIFIED')
 AND segment.`is_deleted` = b'0'
SET sol.`shipping_order_segment_id` = segment.`id`,
    sol.`updated_by` = COALESCE(sol.`updated_by`, segment.`updated_by`),
    sol.`gmt_updated` = NOW()
WHERE sol.`is_deleted` = b'0'
  AND sol.`shipping_order_segment_id` IS NULL;

UPDATE `procurement_purchase_order_logistics_quote_line` quote
INNER JOIN `procurement_shipping_order_line` sol
  ON sol.`shipping_order_id` = quote.`shipping_order_id`
 AND sol.`purchase_order_item_site_id` = quote.`purchase_order_item_site_id`
 AND sol.`is_deleted` = b'0'
SET quote.`shipping_order_segment_id` = sol.`shipping_order_segment_id`,
    quote.`shipping_order_line_id` = sol.`id`,
    quote.`gmt_updated` = NOW()
WHERE quote.`is_deleted` = b'0'
  AND quote.`shipping_order_id` IS NOT NULL
  AND sol.`shipping_order_segment_id` IS NOT NULL
  AND (quote.`shipping_order_segment_id` IS NULL OR quote.`shipping_order_line_id` IS NULL);

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_shipping_order',
       GREATEST(COALESCE(MAX(`id`) + 1, 290000), 290000),
       NOW(),
       NOW()
FROM `procurement_shipping_order`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_shipping_order_segment',
       GREATEST(COALESCE(MAX(`id`) + 1, 292000), 292000),
       NOW(),
       NOW()
FROM `procurement_shipping_order_segment`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_shipping_order_line',
       GREATEST(COALESCE(MAX(`id`) + 1, 300000), 300000),
       NOW(),
       NOW()
FROM `procurement_shipping_order_line`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
