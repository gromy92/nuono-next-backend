-- Product logistics cost ledger.
-- Product-level facts are curated product quote evidence maintained by AI/data correction.

CREATE TABLE IF NOT EXISTS `product_logistics_cost_history` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `partner_sku` VARCHAR(120) NOT NULL,
    `barcode` VARCHAR(120) DEFAULT NULL,
    `site_code` VARCHAR(20) DEFAULT NULL,
    `forwarder_code` VARCHAR(80) NOT NULL,
    `forwarder_name` VARCHAR(200) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `route_name` VARCHAR(200) DEFAULT NULL,
    `service_code` VARCHAR(120) DEFAULT NULL,
    `service_name` VARCHAR(200) DEFAULT NULL,
    `in_transit_batch_id` BIGINT DEFAULT NULL,
    `batch_reference_no` VARCHAR(160) DEFAULT NULL,
    `source_type` VARCHAR(60) NOT NULL,
    `cost_type` VARCHAR(40) NOT NULL,
    `source_actual_bill_id` BIGINT DEFAULT NULL,
    `source_actual_component_id` BIGINT DEFAULT NULL,
    `source_shipping_order_id` BIGINT DEFAULT NULL,
    `source_quote_line_id` BIGINT DEFAULT NULL,
    `fee_type` VARCHAR(80) NOT NULL DEFAULT 'HEADHAUL',
    `raw_fee_name` VARCHAR(160) DEFAULT NULL,
    `cargo_category_code` VARCHAR(80) DEFAULT NULL,
    `cargo_category_name` VARCHAR(160) DEFAULT NULL,
    `quantity` DECIMAL(18, 6) DEFAULT NULL,
    `charge_quantity` DECIMAL(18, 6) DEFAULT NULL,
    `charge_unit` VARCHAR(40) DEFAULT NULL,
    `unit_cost` DECIMAL(18, 6) DEFAULT NULL,
    `total_cost` DECIMAL(18, 6) DEFAULT NULL,
    `currency_code` VARCHAR(20) DEFAULT NULL,
    `exchange_rate_to_cny` DECIMAL(18, 8) NOT NULL DEFAULT 1.00000000,
    `unit_cost_cny` DECIMAL(18, 6) DEFAULT NULL,
    `total_cost_cny` DECIMAL(18, 6) DEFAULT NULL,
    `allocation_basis` VARCHAR(80) DEFAULT NULL,
    `confidence_level` VARCHAR(40) NOT NULL DEFAULT 'CONFIRMED',
    `cost_occurred_at` DATETIME DEFAULT NULL,
    `idempotency_key` VARCHAR(420) NOT NULL,
    `evidence_json` LONGTEXT DEFAULT NULL,
    `raw_snapshot_json` LONGTEXT DEFAULT NULL,
    `review_status` VARCHAR(40) NOT NULL DEFAULT 'ACCEPTED',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_unique_key` TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = b'0' THEN 1 ELSE NULL END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_logistics_cost_history_source` (`owner_user_id`, `idempotency_key`, `active_unique_key`),
    KEY `idx_product_logistics_cost_history_product` (`owner_user_id`, `logical_store_id`, `partner_sku`, `site_code`, `forwarder_code`, `transport_mode`, `is_deleted`),
    KEY `idx_product_logistics_cost_history_batch` (`owner_user_id`, `in_transit_batch_id`, `batch_reference_no`, `is_deleted`),
    KEY `idx_product_logistics_cost_history_actual` (`source_actual_bill_id`, `source_actual_component_id`, `is_deleted`),
    KEY `idx_product_logistics_cost_history_occurred` (`owner_user_id`, `cost_occurred_at`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_logistics_current_cost` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `partner_sku` VARCHAR(120) NOT NULL,
    `barcode` VARCHAR(120) DEFAULT NULL,
    `site_code` VARCHAR(20) DEFAULT NULL,
    `forwarder_code` VARCHAR(80) NOT NULL,
    `forwarder_name` VARCHAR(200) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `route_name` VARCHAR(200) DEFAULT NULL,
    `service_code` VARCHAR(120) DEFAULT NULL,
    `service_name` VARCHAR(200) DEFAULT NULL,
    `current_history_id` BIGINT NOT NULL,
    `source_type` VARCHAR(60) NOT NULL,
    `cost_type` VARCHAR(40) NOT NULL,
    `fee_type` VARCHAR(80) NOT NULL DEFAULT 'HEADHAUL',
    `cargo_category_code` VARCHAR(80) DEFAULT NULL,
    `cargo_category_name` VARCHAR(160) DEFAULT NULL,
    `charge_unit` VARCHAR(40) DEFAULT NULL,
    `unit_cost_cny` DECIMAL(18, 6) DEFAULT NULL,
    `total_cost_cny` DECIMAL(18, 6) DEFAULT NULL,
    `currency_code` VARCHAR(20) DEFAULT NULL,
    `confidence_level` VARCHAR(40) NOT NULL DEFAULT 'CONFIRMED',
    `cost_occurred_at` DATETIME DEFAULT NULL,
    `refreshed_at` DATETIME DEFAULT NULL,
    `evidence_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `current_cost_slot` VARCHAR(420)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(
                    `owner_user_id`, ':',
                    `logical_store_id`, ':',
                    COALESCE(`partner_sku`, ''), ':',
                    COALESCE(`site_code`, ''), ':',
                    COALESCE(`forwarder_code`, ''), ':',
                    COALESCE(`transport_mode`, ''), ':',
                    COALESCE(`fee_type`, '')
                )
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_logistics_current_cost_active` (`current_cost_slot`),
    KEY `idx_product_logistics_current_cost_product` (`owner_user_id`, `logical_store_id`, `partner_sku`, `site_code`, `is_deleted`),
    KEY `idx_product_logistics_current_cost_forwarder` (`owner_user_id`, `forwarder_code`, `transport_mode`, `site_code`, `is_deleted`),
    KEY `idx_product_logistics_current_cost_history` (`current_history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_logistics_rate_card` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `forwarder_code` VARCHAR(80) NOT NULL,
    `forwarder_name` VARCHAR(200) DEFAULT NULL,
    `transport_mode` VARCHAR(20) NOT NULL,
    `fee_type` VARCHAR(80) NOT NULL DEFAULT 'HEADHAUL',
    `cargo_category_code` VARCHAR(80) NOT NULL,
    `cargo_category_name` VARCHAR(160) NOT NULL,
    `charge_unit` VARCHAR(40) NOT NULL,
    `unit_cost_cny` DECIMAL(18, 6) NOT NULL,
    `currency_code` VARCHAR(20) NOT NULL DEFAULT 'CNY',
    `source_type` VARCHAR(60) NOT NULL,
    `source_reference` VARCHAR(200) DEFAULT NULL,
    `effective_at` DATETIME DEFAULT NULL,
    `remark` VARCHAR(500) DEFAULT NULL,
    `evidence_json` LONGTEXT DEFAULT NULL,
    `is_active` BIT(1) NOT NULL DEFAULT b'1',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `rate_card_slot` VARCHAR(420)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' AND `is_active` = b'1' THEN CONCAT(
                    `owner_user_id`, ':',
                    COALESCE(`site_code`, ''), ':',
                    COALESCE(`forwarder_code`, ''), ':',
                    COALESCE(`transport_mode`, ''), ':',
                    COALESCE(`fee_type`, ''), ':',
                    COALESCE(`cargo_category_code`, '')
                )
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_logistics_rate_card_active` (`rate_card_slot`),
    KEY `idx_product_logistics_rate_card_route` (`owner_user_id`, `site_code`, `forwarder_code`, `transport_mode`, `is_active`, `is_deleted`),
    KEY `idx_product_logistics_rate_card_category` (`owner_user_id`, `forwarder_code`, `cargo_category_code`, `is_active`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_logistics_cost_exception` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `in_transit_batch_id` BIGINT DEFAULT NULL,
    `batch_reference_no` VARCHAR(160) DEFAULT NULL,
    `source_type` VARCHAR(60) NOT NULL,
    `source_actual_bill_id` BIGINT DEFAULT NULL,
    `source_actual_component_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(120) DEFAULT NULL,
    `partner_sku` VARCHAR(120) DEFAULT NULL,
    `site_code` VARCHAR(20) DEFAULT NULL,
    `forwarder_code` VARCHAR(80) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `exception_type` VARCHAR(80) NOT NULL,
    `exception_message` VARCHAR(1000) NOT NULL,
    `resolution_status` VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    `resolved_by` BIGINT DEFAULT NULL,
    `resolved_at` DATETIME DEFAULT NULL,
    `evidence_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_logistics_cost_exception_owner_status` (`owner_user_id`, `resolution_status`, `exception_type`, `is_deleted`),
    KEY `idx_product_logistics_cost_exception_source` (`source_actual_bill_id`, `source_actual_component_id`, `is_deleted`),
    KEY `idx_product_logistics_cost_exception_batch` (`owner_user_id`, `in_transit_batch_id`, `batch_reference_no`, `is_deleted`),
    KEY `idx_product_logistics_cost_exception_product` (`owner_user_id`, `store_code`, `partner_sku`, `site_code`, `resolution_status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @drop_product_logistics_current_cost_active_key := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_logistics_current_cost'
              AND INDEX_NAME = 'uk_product_logistics_current_cost_active'
        ),
        'ALTER TABLE `product_logistics_current_cost` DROP INDEX `uk_product_logistics_current_cost_active`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_product_logistics_current_cost_active_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `product_logistics_current_cost`
    MODIFY COLUMN `current_cost_slot` VARCHAR(420)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(
                    `owner_user_id`, ':',
                    `logical_store_id`, ':',
                    COALESCE(`partner_sku`, ''), ':',
                    COALESCE(`site_code`, ''), ':',
                    COALESCE(`forwarder_code`, ''), ':',
                    COALESCE(`transport_mode`, ''), ':',
                    COALESCE(`fee_type`, '')
                )
                ELSE NULL
            END
        ) STORED;

SET @add_product_logistics_current_cost_active_key := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_logistics_current_cost'
              AND INDEX_NAME = 'uk_product_logistics_current_cost_active'
        ),
        'SELECT 1',
        'ALTER TABLE `product_logistics_current_cost` ADD UNIQUE KEY `uk_product_logistics_current_cost_active` (`current_cost_slot`)'
    )
);
PREPARE stmt FROM @add_product_logistics_current_cost_active_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_logistics_history_cargo_category_code := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_logistics_cost_history'
              AND COLUMN_NAME = 'cargo_category_code'
        ),
        'SELECT ''product_logistics_cost_history_cargo_category_code_exists'' AS stage',
        'ALTER TABLE `product_logistics_cost_history` ADD COLUMN `cargo_category_code` VARCHAR(80) DEFAULT NULL AFTER `raw_fee_name`'
    )
);
PREPARE stmt FROM @add_product_logistics_history_cargo_category_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_logistics_history_cargo_category_name := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_logistics_cost_history'
              AND COLUMN_NAME = 'cargo_category_name'
        ),
        'SELECT ''product_logistics_cost_history_cargo_category_name_exists'' AS stage',
        'ALTER TABLE `product_logistics_cost_history` ADD COLUMN `cargo_category_name` VARCHAR(160) DEFAULT NULL AFTER `cargo_category_code`'
    )
);
PREPARE stmt FROM @add_product_logistics_history_cargo_category_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_logistics_current_cargo_category_code := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_logistics_current_cost'
              AND COLUMN_NAME = 'cargo_category_code'
        ),
        'SELECT ''product_logistics_current_cost_cargo_category_code_exists'' AS stage',
        'ALTER TABLE `product_logistics_current_cost` ADD COLUMN `cargo_category_code` VARCHAR(80) DEFAULT NULL AFTER `fee_type`'
    )
);
PREPARE stmt FROM @add_product_logistics_current_cargo_category_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_logistics_current_cargo_category_name := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_logistics_current_cost'
              AND COLUMN_NAME = 'cargo_category_name'
        ),
        'SELECT ''product_logistics_current_cost_cargo_category_name_exists'' AS stage',
        'ALTER TABLE `product_logistics_current_cost` ADD COLUMN `cargo_category_name` VARCHAR(160) DEFAULT NULL AFTER `cargo_category_code`'
    )
);
PREPARE stmt FROM @add_product_logistics_current_cargo_category_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_actual_component_store_code := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'in_transit_freight_actual_component'
              AND COLUMN_NAME = 'store_code'
        ),
        'SELECT ''in_transit_freight_actual_component_store_code_exists'' AS stage',
        IF(
            EXISTS(
                SELECT 1
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'in_transit_freight_actual_component'
            ),
            'ALTER TABLE `in_transit_freight_actual_component` ADD COLUMN `store_code` VARCHAR(120) DEFAULT NULL AFTER `destination_code`',
            'SELECT ''in_transit_freight_actual_component_missing'' AS stage'
        )
    )
);
PREPARE stmt FROM @add_actual_component_store_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_logistics_cost_exception_store_code := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_logistics_cost_exception'
              AND COLUMN_NAME = 'store_code'
        ),
        'SELECT ''product_logistics_cost_exception_store_code_exists'' AS stage',
        'ALTER TABLE `product_logistics_cost_exception` ADD COLUMN `store_code` VARCHAR(120) DEFAULT NULL AFTER `source_actual_component_id`'
    )
);
PREPARE stmt FROM @add_product_logistics_cost_exception_store_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_logistics_cost_exception_product_idx := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_logistics_cost_exception'
              AND INDEX_NAME = 'idx_product_logistics_cost_exception_product'
        ),
        'SELECT ''product_logistics_cost_exception_product_idx_exists'' AS stage',
        'ALTER TABLE `product_logistics_cost_exception` ADD KEY `idx_product_logistics_cost_exception_product` (`owner_user_id`, `store_code`, `partner_sku`, `site_code`, `resolution_status`, `is_deleted`)'
    )
);
PREPARE stmt FROM @add_product_logistics_cost_exception_product_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_logistics_cost_history',
       GREATEST(COALESCE(MAX(`id`) + 1, 370000), 370000),
       NOW(),
       NOW()
FROM `product_logistics_cost_history`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_logistics_current_cost',
       GREATEST(COALESCE(MAX(`id`) + 1, 380000), 380000),
       NOW(),
       NOW()
FROM `product_logistics_current_cost`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_logistics_cost_exception',
       GREATEST(COALESCE(MAX(`id`) + 1, 390000), 390000),
       NOW(),
       NOW()
FROM `product_logistics_cost_exception`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_logistics_rate_card',
       GREATEST(COALESCE(MAX(`id`) + 1, 430000), 430000),
       NOW(),
       NOW()
FROM `product_logistics_rate_card`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
