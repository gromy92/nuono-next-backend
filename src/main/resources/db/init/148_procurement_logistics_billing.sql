-- Procurement logistics billing loop.
-- Scope: reusable product-channel quotes, shipping-order expected bills, actual bill links and reconciliation.

CREATE TABLE IF NOT EXISTS `product_forwarder_channel_quote` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `barcode` VARCHAR(100) DEFAULT NULL,
    `forwarder_code` VARCHAR(80) NOT NULL,
    `forwarder_name` VARCHAR(200) DEFAULT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `route_name` VARCHAR(200) DEFAULT NULL,
    `service_code` VARCHAR(120) DEFAULT NULL,
    `service_name` VARCHAR(200) DEFAULT NULL,
    `site_code` VARCHAR(20) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `target_platform` VARCHAR(80) DEFAULT NULL,
    `delivery_city` VARCHAR(120) DEFAULT NULL,
    `currency` VARCHAR(20) DEFAULT NULL,
    `unit_price` DECIMAL(18, 6) DEFAULT NULL,
    `billing_unit` VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    `estimated_amount` DECIMAL(18, 6) DEFAULT NULL,
    `source_type` VARCHAR(60) NOT NULL DEFAULT 'SHIPPING_ORDER_QUOTE',
    `source_shipping_order_id` BIGINT DEFAULT NULL,
    `source_shipping_order_line_id` BIGINT DEFAULT NULL,
    `source_quote_line_id` BIGINT DEFAULT NULL,
    `source_actual_bill_id` BIGINT DEFAULT NULL,
    `source_actual_component_id` BIGINT DEFAULT NULL,
    `source_filename` VARCHAR(300) DEFAULT NULL,
    `effective_status` VARCHAR(40) NOT NULL DEFAULT 'CURRENT',
    `confirmed_at` DATETIME DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `raw_snapshot_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_quote_slot` VARCHAR(420)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' AND `effective_status` = 'CURRENT' THEN CONCAT(
                    `owner_user_id`, ':',
                    `product_variant_id`, ':',
                    `forwarder_code`, ':',
                    COALESCE(`route_code`, ''), ':',
                    COALESCE(`service_code`, ''), ':',
                    COALESCE(`billing_unit`, '')
                )
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_forwarder_channel_quote_current` (`active_quote_slot`),
    KEY `idx_product_forwarder_channel_quote_product` (`owner_user_id`, `product_variant_id`, `forwarder_code`, `effective_status`, `is_deleted`),
    KEY `idx_product_forwarder_channel_quote_channel` (`owner_user_id`, `forwarder_code`, `route_code`, `service_code`, `site_code`, `is_deleted`),
    KEY `idx_product_forwarder_channel_quote_source` (`source_shipping_order_id`, `source_quote_line_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `logistics_expected_bill` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `expected_bill_no` VARCHAR(80) NOT NULL,
    `shipping_order_id` BIGINT NOT NULL,
    `shipping_order_no` VARCHAR(80) NOT NULL,
    `shipping_order_segment_id` BIGINT DEFAULT NULL,
    `shipping_order_segment_no` VARCHAR(100) DEFAULT NULL,
    `forwarder_code` VARCHAR(80) DEFAULT NULL,
    `forwarder_name` VARCHAR(200) DEFAULT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `route_name` VARCHAR(200) DEFAULT NULL,
    `service_code` VARCHAR(120) DEFAULT NULL,
    `service_name` VARCHAR(200) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `currency` VARCHAR(20) DEFAULT NULL,
    `exchange_rate_to_cny` DECIMAL(18, 8) NOT NULL DEFAULT 1.00000000,
    `expected_total_amount` DECIMAL(18, 6) DEFAULT NULL,
    `expected_total_cny` DECIMAL(18, 6) DEFAULT NULL,
    `component_count` INT NOT NULL DEFAULT 0,
    `bill_status` VARCHAR(40) NOT NULL DEFAULT 'GENERATED',
    `generated_from` VARCHAR(60) NOT NULL DEFAULT 'SHIPPING_ORDER_QUOTE',
    `generated_at` DATETIME DEFAULT NULL,
    `locked_at` DATETIME DEFAULT NULL,
    `cancelled_at` DATETIME DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `raw_snapshot_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_shipping_order_slot` VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' AND `bill_status` <> 'CANCELLED' THEN CONCAT(CAST(`shipping_order_id` AS CHAR), ':', CAST(COALESCE(`shipping_order_segment_id`, 0) AS CHAR))
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_logistics_expected_bill_no` (`expected_bill_no`),
    UNIQUE KEY `uk_logistics_expected_bill_active_shipping_order` (`active_shipping_order_slot`),
    KEY `idx_logistics_expected_bill_owner_status` (`owner_user_id`, `bill_status`, `is_deleted`, `gmt_updated`),
    KEY `idx_logistics_expected_bill_shipping_order` (`shipping_order_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `logistics_expected_bill_component` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `expected_bill_id` BIGINT NOT NULL,
    `shipping_order_id` BIGINT NOT NULL,
    `shipping_order_segment_id` BIGINT DEFAULT NULL,
    `shipping_order_line_id` BIGINT DEFAULT NULL,
    `quote_line_id` BIGINT DEFAULT NULL,
    `product_master_id` BIGINT DEFAULT NULL,
    `product_variant_id` BIGINT DEFAULT NULL,
    `barcode` VARCHAR(100) DEFAULT NULL,
    `psku_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(20) DEFAULT NULL,
    `box_no` VARCHAR(160) DEFAULT NULL,
    `fee_type` VARCHAR(80) NOT NULL DEFAULT 'HEADHAUL',
    `raw_fee_name` VARCHAR(160) DEFAULT NULL,
    `quantity` DECIMAL(18, 6) DEFAULT NULL,
    `charge_quantity` DECIMAL(18, 6) DEFAULT NULL,
    `charge_unit` VARCHAR(40) DEFAULT NULL,
    `unit_price` DECIMAL(18, 6) DEFAULT NULL,
    `currency` VARCHAR(20) DEFAULT NULL,
    `exchange_rate_to_cny` DECIMAL(18, 8) NOT NULL DEFAULT 1.00000000,
    `expected_amount` DECIMAL(18, 6) DEFAULT NULL,
    `expected_amount_cny` DECIMAL(18, 6) DEFAULT NULL,
    `allocation_basis` VARCHAR(60) DEFAULT NULL,
    `raw_snapshot_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_logistics_expected_bill_component_bill` (`owner_user_id`, `expected_bill_id`, `is_deleted`),
    KEY `idx_logistics_expected_bill_component_shipping_order` (`shipping_order_id`, `shipping_order_line_id`, `is_deleted`),
    KEY `idx_logistics_expected_bill_component_product` (`owner_user_id`, `product_variant_id`, `fee_type`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `logistics_actual_bill_shipping_order_link` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `actual_bill_id` BIGINT NOT NULL,
    `shipping_order_id` BIGINT NOT NULL,
    `shipping_order_no` VARCHAR(80) NOT NULL,
    `link_status` VARCHAR(40) NOT NULL DEFAULT 'LINKED',
    `match_method` VARCHAR(60) NOT NULL DEFAULT 'MANUAL',
    `evidence_json` LONGTEXT DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `confirmed_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_link_slot` VARCHAR(160)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`actual_bill_id`, ':', `shipping_order_id`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_logistics_actual_bill_shipping_order_link_active` (`active_link_slot`),
    KEY `idx_logistics_actual_bill_shipping_order_link_bill` (`owner_user_id`, `actual_bill_id`, `is_deleted`),
    KEY `idx_logistics_actual_bill_shipping_order_link_order` (`owner_user_id`, `shipping_order_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `logistics_bill_reconciliation` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `shipping_order_id` BIGINT NOT NULL,
    `shipping_order_segment_id` BIGINT DEFAULT NULL,
    `expected_bill_id` BIGINT DEFAULT NULL,
    `actual_bill_id` BIGINT DEFAULT NULL,
    `reconciliation_no` VARCHAR(80) NOT NULL,
    `reconciliation_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING_ACTUAL_BILL',
    `expected_total_cny` DECIMAL(18, 6) DEFAULT NULL,
    `actual_total_cny` DECIMAL(18, 6) DEFAULT NULL,
    `diff_amount_cny` DECIMAL(18, 6) DEFAULT NULL,
    `diff_rate` DECIMAL(18, 6) DEFAULT NULL,
    `matched_component_count` INT NOT NULL DEFAULT 0,
    `unmatched_expected_count` INT NOT NULL DEFAULT 0,
    `unmatched_actual_count` INT NOT NULL DEFAULT 0,
    `matched_at` DATETIME DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `confirmed_at` DATETIME DEFAULT NULL,
    `rejected_by` BIGINT DEFAULT NULL,
    `rejected_at` DATETIME DEFAULT NULL,
    `reject_reason` VARCHAR(1000) DEFAULT NULL,
    `summary_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_shipping_order_slot` VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' AND `reconciliation_status` <> 'CANCELLED' THEN CONCAT(CAST(`shipping_order_id` AS CHAR), ':', CAST(COALESCE(`shipping_order_segment_id`, 0) AS CHAR))
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_logistics_bill_reconciliation_no` (`reconciliation_no`),
    UNIQUE KEY `uk_logistics_bill_reconciliation_active_order` (`active_shipping_order_slot`),
    KEY `idx_logistics_bill_reconciliation_owner_status` (`owner_user_id`, `reconciliation_status`, `is_deleted`, `gmt_updated`),
    KEY `idx_logistics_bill_reconciliation_expected` (`expected_bill_id`, `is_deleted`),
    KEY `idx_logistics_bill_reconciliation_actual` (`actual_bill_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `logistics_bill_reconciliation_item` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `reconciliation_id` BIGINT NOT NULL,
    `expected_component_id` BIGINT DEFAULT NULL,
    `actual_component_id` BIGINT DEFAULT NULL,
    `shipping_order_line_id` BIGINT DEFAULT NULL,
    `quote_line_id` BIGINT DEFAULT NULL,
    `product_variant_id` BIGINT DEFAULT NULL,
    `barcode` VARCHAR(100) DEFAULT NULL,
    `box_no` VARCHAR(160) DEFAULT NULL,
    `fee_type` VARCHAR(80) DEFAULT NULL,
    `match_status` VARCHAR(40) NOT NULL DEFAULT 'MATCHED',
    `match_method` VARCHAR(60) DEFAULT NULL,
    `expected_charge_quantity` DECIMAL(18, 6) DEFAULT NULL,
    `actual_charge_quantity` DECIMAL(18, 6) DEFAULT NULL,
    `expected_unit_price` DECIMAL(18, 6) DEFAULT NULL,
    `actual_unit_price` DECIMAL(18, 6) DEFAULT NULL,
    `expected_amount_cny` DECIMAL(18, 6) DEFAULT NULL,
    `actual_amount_cny` DECIMAL(18, 6) DEFAULT NULL,
    `diff_amount_cny` DECIMAL(18, 6) DEFAULT NULL,
    `diff_reason` VARCHAR(500) DEFAULT NULL,
    `review_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    `review_note` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_logistics_bill_reconciliation_item_head` (`owner_user_id`, `reconciliation_id`, `is_deleted`),
    KEY `idx_logistics_bill_reconciliation_item_expected` (`expected_component_id`, `is_deleted`),
    KEY `idx_logistics_bill_reconciliation_item_actual` (`actual_component_id`, `is_deleted`),
    KEY `idx_logistics_bill_reconciliation_item_product` (`owner_user_id`, `product_variant_id`, `fee_type`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @expected_bill_drop_segment_key := (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'logistics_expected_bill'
              AND INDEX_NAME = 'uk_logistics_expected_bill_active_shipping_order'
        ),
        'ALTER TABLE `logistics_expected_bill` DROP INDEX `uk_logistics_expected_bill_active_shipping_order`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @expected_bill_drop_segment_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @expected_bill_add_segment_id := (
    SELECT IF(
        NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'logistics_expected_bill'
              AND COLUMN_NAME = 'shipping_order_segment_id'
        ),
        'ALTER TABLE `logistics_expected_bill` ADD COLUMN `shipping_order_segment_id` BIGINT DEFAULT NULL AFTER `shipping_order_no`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @expected_bill_add_segment_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @expected_bill_add_segment_no := (
    SELECT IF(
        NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'logistics_expected_bill'
              AND COLUMN_NAME = 'shipping_order_segment_no'
        ),
        'ALTER TABLE `logistics_expected_bill` ADD COLUMN `shipping_order_segment_no` VARCHAR(100) DEFAULT NULL AFTER `shipping_order_segment_id`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @expected_bill_add_segment_no;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `logistics_expected_bill`
    MODIFY COLUMN `active_shipping_order_slot` VARCHAR(120)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' AND `bill_status` <> 'CANCELLED' THEN CONCAT(CAST(`shipping_order_id` AS CHAR), ':', CAST(COALESCE(`shipping_order_segment_id`, 0) AS CHAR))
                ELSE NULL
            END
        ) STORED;

ALTER TABLE `logistics_expected_bill`
    ADD UNIQUE KEY `uk_logistics_expected_bill_active_shipping_order` (`active_shipping_order_slot`);

SET @expected_component_add_segment_id := (
    SELECT IF(
        NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'logistics_expected_bill_component'
              AND COLUMN_NAME = 'shipping_order_segment_id'
        ),
        'ALTER TABLE `logistics_expected_bill_component` ADD COLUMN `shipping_order_segment_id` BIGINT DEFAULT NULL AFTER `shipping_order_id`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @expected_component_add_segment_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @reconciliation_drop_segment_key := (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'logistics_bill_reconciliation'
              AND INDEX_NAME = 'uk_logistics_bill_reconciliation_active_order'
        ),
        'ALTER TABLE `logistics_bill_reconciliation` DROP INDEX `uk_logistics_bill_reconciliation_active_order`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @reconciliation_drop_segment_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @reconciliation_add_segment_id := (
    SELECT IF(
        NOT EXISTS (
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'logistics_bill_reconciliation'
              AND COLUMN_NAME = 'shipping_order_segment_id'
        ),
        'ALTER TABLE `logistics_bill_reconciliation` ADD COLUMN `shipping_order_segment_id` BIGINT DEFAULT NULL AFTER `shipping_order_id`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @reconciliation_add_segment_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `logistics_bill_reconciliation`
    MODIFY COLUMN `active_shipping_order_slot` VARCHAR(120)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' AND `reconciliation_status` <> 'CANCELLED' THEN CONCAT(CAST(`shipping_order_id` AS CHAR), ':', CAST(COALESCE(`shipping_order_segment_id`, 0) AS CHAR))
                ELSE NULL
            END
        ) STORED;

ALTER TABLE `logistics_bill_reconciliation`
    ADD UNIQUE KEY `uk_logistics_bill_reconciliation_active_order` (`active_shipping_order_slot`);

ALTER TABLE `in_transit_freight_actual_bill`
    MODIFY COLUMN `source_type` ENUM('PLUGIN_SYNC', 'FILE_IMPORT', 'MANUAL') NOT NULL DEFAULT 'PLUGIN_SYNC';

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_forwarder_channel_quote',
       GREATEST(COALESCE(MAX(`id`) + 1, 320000), 320000),
       NOW(),
       NOW()
FROM `product_forwarder_channel_quote`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'logistics_expected_bill',
       GREATEST(COALESCE(MAX(`id`) + 1, 330000), 330000),
       NOW(),
       NOW()
FROM `logistics_expected_bill`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'logistics_expected_bill_component',
       GREATEST(COALESCE(MAX(`id`) + 1, 340000), 340000),
       NOW(),
       NOW()
FROM `logistics_expected_bill_component`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'logistics_actual_bill_shipping_order_link',
       GREATEST(COALESCE(MAX(`id`) + 1, 350000), 350000),
       NOW(),
       NOW()
FROM `logistics_actual_bill_shipping_order_link`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'logistics_bill_reconciliation',
       GREATEST(COALESCE(MAX(`id`) + 1, 360000), 360000),
       NOW(),
       NOW()
FROM `logistics_bill_reconciliation`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'logistics_bill_reconciliation_item',
       GREATEST(COALESCE(MAX(`id`) + 1, 370000), 370000),
       NOW(),
       NOW()
FROM `logistics_bill_reconciliation_item`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
