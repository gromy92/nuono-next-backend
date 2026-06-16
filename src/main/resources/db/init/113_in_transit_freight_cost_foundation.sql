CREATE TABLE IF NOT EXISTS `in_transit_freight_actual_bill` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `owner_user_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `standard_forwarder_id` BIGINT DEFAULT NULL,
    `forwarder_code` VARCHAR(80) DEFAULT NULL,
    `forwarder_name` VARCHAR(160) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `destination_code` VARCHAR(40) DEFAULT NULL,
    `target_site_code` VARCHAR(80) DEFAULT NULL,
    `source_type` ENUM('PLUGIN_SYNC') NOT NULL DEFAULT 'PLUGIN_SYNC',
    `source_system` VARCHAR(40) NOT NULL,
    `bill_no` VARCHAR(120) NOT NULL,
    `bill_status` VARCHAR(60) DEFAULT NULL,
    `business_occurred_at` DATETIME DEFAULT NULL,
    `bill_date` DATETIME DEFAULT NULL,
    `paid_at` DATETIME DEFAULT NULL,
    `currency_code` VARCHAR(12) NOT NULL DEFAULT 'CNY',
    `exchange_rate_to_cny` DECIMAL(18,8) NOT NULL DEFAULT 1.00000000,
    `original_total_amount` DECIMAL(18,6) DEFAULT NULL,
    `cny_total_amount` DECIMAL(18,6) DEFAULT NULL,
    `freight_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `customs_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `storage_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `handling_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `delivery_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `interest_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `posted_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `balance_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `raw_payload_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b'0' THEN 1 ELSE NULL END) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_in_transit_freight_actual_bill_source` (`owner_user_id`, `source_system`, `bill_no`, `batch_id`, `active_unique_key`),
    KEY `idx_in_transit_freight_actual_bill_batch` (`owner_user_id`, `batch_id`, `is_deleted`),
    KEY `idx_in_transit_freight_actual_bill_forwarder_month` (`owner_user_id`, `standard_forwarder_id`, `transport_mode`, `destination_code`, `target_site_code`, `business_occurred_at`, `is_deleted`)
);

CREATE TABLE IF NOT EXISTS `in_transit_freight_actual_component` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `owner_user_id` BIGINT NOT NULL,
    `actual_bill_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `package_id` BIGINT DEFAULT NULL,
    `box_no` VARCHAR(160) DEFAULT NULL,
    `external_box_no` VARCHAR(160) DEFAULT NULL,
    `psku` VARCHAR(160) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `destination_code` VARCHAR(40) DEFAULT NULL,
    `target_site_code` VARCHAR(80) DEFAULT NULL,
    `raw_fee_name` VARCHAR(160) DEFAULT NULL,
    `standard_fee_type` VARCHAR(80) NOT NULL,
    `charge_quantity` DECIMAL(18,6) DEFAULT NULL,
    `charge_unit` VARCHAR(40) DEFAULT NULL,
    `unit_price` DECIMAL(18,6) DEFAULT NULL,
    `currency_code` VARCHAR(12) NOT NULL DEFAULT 'CNY',
    `exchange_rate_to_cny` DECIMAL(18,8) NOT NULL DEFAULT 1.00000000,
    `original_amount` DECIMAL(18,6) DEFAULT NULL,
    `cny_amount` DECIMAL(18,6) DEFAULT NULL,
    `quantity` DECIMAL(18,6) DEFAULT NULL,
    `measured_weight_kg` DECIMAL(18,6) DEFAULT NULL,
    `measured_volume_cbm` DECIMAL(18,6) DEFAULT NULL,
    `volume_weight_kg` DECIMAL(18,6) DEFAULT NULL,
    `chargeable_weight_kg` DECIMAL(18,6) DEFAULT NULL,
    `allocation_basis` VARCHAR(60) DEFAULT NULL,
    `raw_payload_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_in_transit_freight_actual_component_bill` (`owner_user_id`, `actual_bill_id`, `is_deleted`),
    KEY `idx_in_transit_freight_actual_component_batch_type` (`owner_user_id`, `batch_id`, `standard_fee_type`, `is_deleted`),
    KEY `idx_in_transit_freight_actual_component_package` (`owner_user_id`, `batch_id`, `package_id`, `is_deleted`),
    KEY `idx_in_transit_freight_actual_component_psku` (`owner_user_id`, `batch_id`, `psku`, `is_deleted`),
    KEY `idx_in_transit_freight_actual_component_psku_site` (`owner_user_id`, `psku`, `target_site_code`, `standard_fee_type`, `is_deleted`)
);

CREATE TABLE IF NOT EXISTS `in_transit_freight_rate_card_version` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `owner_user_id` BIGINT NOT NULL,
    `standard_forwarder_id` BIGINT DEFAULT NULL,
    `forwarder_code` VARCHAR(80) DEFAULT NULL,
    `forwarder_name` VARCHAR(160) DEFAULT NULL,
    `version_no` VARCHAR(120) DEFAULT NULL,
    `version_name` VARCHAR(160) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `destination_code` VARCHAR(40) DEFAULT NULL,
    `target_site_code` VARCHAR(80) DEFAULT NULL,
    `currency_code` VARCHAR(12) NOT NULL DEFAULT 'CNY',
    `exchange_rate_to_cny` DECIMAL(18,8) NOT NULL DEFAULT 1.00000000,
    `effective_from` DATETIME NOT NULL,
    `effective_to` DATETIME DEFAULT NULL,
    `version_status` VARCHAR(40) NOT NULL DEFAULT 'draft',
    `source_type` VARCHAR(40) NOT NULL DEFAULT 'INTERNAL',
    `raw_payload_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_in_transit_freight_rate_card_version_forwarder` (`owner_user_id`, `standard_forwarder_id`, `transport_mode`, `destination_code`, `target_site_code`, `effective_from`, `is_deleted`)
);

CREATE TABLE IF NOT EXISTS `in_transit_freight_rate_card_rule` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `owner_user_id` BIGINT NOT NULL,
    `rate_card_version_id` BIGINT NOT NULL,
    `standard_fee_type` VARCHAR(80) NOT NULL,
    `raw_fee_name` VARCHAR(160) DEFAULT NULL,
    `product_category` VARCHAR(80) DEFAULT NULL,
    `box_category` VARCHAR(80) DEFAULT NULL,
    `charge_unit` VARCHAR(40) DEFAULT NULL,
    `unit_price` DECIMAL(18,6) DEFAULT NULL,
    `min_charge_quantity` DECIMAL(18,6) DEFAULT NULL,
    `min_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `currency_code` VARCHAR(12) NOT NULL DEFAULT 'CNY',
    `exchange_rate_to_cny` DECIMAL(18,8) NOT NULL DEFAULT 1.00000000,
    `rule_status` VARCHAR(40) NOT NULL DEFAULT 'active',
    `formula_json` LONGTEXT DEFAULT NULL,
    `raw_payload_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_in_transit_freight_rate_card_rule_version` (`owner_user_id`, `rate_card_version_id`, `standard_fee_type`, `is_deleted`)
);

CREATE TABLE IF NOT EXISTS `in_transit_freight_estimate_snapshot` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `owner_user_id` BIGINT NOT NULL,
    `batch_id` BIGINT DEFAULT NULL,
    `source_estimate_type` VARCHAR(60) NOT NULL,
    `source_estimate_id` BIGINT DEFAULT NULL,
    `source_estimate_no` VARCHAR(120) DEFAULT NULL,
    `source_recommendation_id` BIGINT DEFAULT NULL,
    `rate_card_version_id` BIGINT DEFAULT NULL,
    `standard_forwarder_id` BIGINT DEFAULT NULL,
    `forwarder_code` VARCHAR(80) DEFAULT NULL,
    `forwarder_name` VARCHAR(160) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `destination_code` VARCHAR(40) DEFAULT NULL,
    `target_site_code` VARCHAR(80) DEFAULT NULL,
    `recommended` BIT(1) NOT NULL DEFAULT b'0',
    `estimate_status` VARCHAR(40) NOT NULL DEFAULT 'snapshot',
    `currency_code` VARCHAR(12) NOT NULL DEFAULT 'CNY',
    `exchange_rate_to_cny` DECIMAL(18,8) NOT NULL DEFAULT 1.00000000,
    `estimated_total_amount` DECIMAL(18,6) DEFAULT NULL,
    `estimated_total_cny` DECIMAL(18,6) DEFAULT NULL,
    `generated_at` DATETIME DEFAULT NULL,
    `raw_payload_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_in_transit_freight_estimate_snapshot_batch` (`owner_user_id`, `batch_id`, `is_deleted`),
    KEY `idx_in_transit_freight_estimate_snapshot_source` (`owner_user_id`, `source_estimate_type`, `source_estimate_id`, `source_recommendation_id`, `is_deleted`)
);

CREATE TABLE IF NOT EXISTS `in_transit_freight_estimate_component` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `owner_user_id` BIGINT NOT NULL,
    `estimate_snapshot_id` BIGINT NOT NULL,
    `target_site_code` VARCHAR(80) DEFAULT NULL,
    `psku` VARCHAR(160) DEFAULT NULL,
    `component_type` VARCHAR(80) NOT NULL,
    `raw_fee_name` VARCHAR(160) DEFAULT NULL,
    `quantity` DECIMAL(18,6) DEFAULT NULL,
    `chargeable_weight_kg` DECIMAL(18,6) DEFAULT NULL,
    `charge_quantity` DECIMAL(18,6) DEFAULT NULL,
    `charge_unit` VARCHAR(40) DEFAULT NULL,
    `unit_price` DECIMAL(18,6) DEFAULT NULL,
    `currency_code` VARCHAR(12) NOT NULL DEFAULT 'CNY',
    `exchange_rate_to_cny` DECIMAL(18,8) NOT NULL DEFAULT 1.00000000,
    `estimated_amount` DECIMAL(18,6) DEFAULT NULL,
    `estimated_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `raw_payload_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_in_transit_freight_estimate_component_snapshot` (`owner_user_id`, `estimate_snapshot_id`, `is_deleted`),
    KEY `idx_in_transit_freight_estimate_component_type` (`owner_user_id`, `component_type`, `is_deleted`),
    KEY `idx_in_transit_freight_estimate_component_psku_site` (`owner_user_id`, `psku`, `target_site_code`, `component_type`, `is_deleted`)
);

CREATE TABLE IF NOT EXISTS `in_transit_freight_estimate_match` (
    `id` BIGINT NOT NULL PRIMARY KEY,
    `owner_user_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `actual_bill_id` BIGINT DEFAULT NULL,
    `estimate_snapshot_id` BIGINT DEFAULT NULL,
    `match_status` ENUM('matched','unmatched','ambiguous') NOT NULL DEFAULT 'unmatched',
    `actual_total_cny` DECIMAL(18,6) DEFAULT NULL,
    `estimated_total_cny` DECIMAL(18,6) DEFAULT NULL,
    `diff_amount_cny` DECIMAL(18,6) DEFAULT NULL,
    `diff_rate` DECIMAL(18,8) DEFAULT NULL,
    `matched_at` DATETIME DEFAULT NULL,
    `reason` VARCHAR(500) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_in_transit_freight_estimate_match_batch` (`owner_user_id`, `batch_id`, `match_status`, `is_deleted`),
    KEY `idx_in_transit_freight_estimate_match_actual` (`owner_user_id`, `actual_bill_id`, `is_deleted`),
    KEY `idx_in_transit_freight_estimate_match_estimate` (`owner_user_id`, `estimate_snapshot_id`, `is_deleted`)
);

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'in_transit_freight_actual_bill', GREATEST(COALESCE(MAX(`id`) + 1, 59000), 59000), NOW(), NOW()
FROM `in_transit_freight_actual_bill`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'in_transit_freight_actual_component', GREATEST(COALESCE(MAX(`id`) + 1, 60000), 60000), NOW(), NOW()
FROM `in_transit_freight_actual_component`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'in_transit_freight_estimate_snapshot', GREATEST(COALESCE(MAX(`id`) + 1, 61000), 61000), NOW(), NOW()
FROM `in_transit_freight_estimate_snapshot`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'in_transit_freight_estimate_component', GREATEST(COALESCE(MAX(`id`) + 1, 62000), 62000), NOW(), NOW()
FROM `in_transit_freight_estimate_component`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'in_transit_freight_estimate_match', GREATEST(COALESCE(MAX(`id`) + 1, 63000), 63000), NOW(), NOW()
FROM `in_transit_freight_estimate_match`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'in_transit_freight_rate_card_version', GREATEST(COALESCE(MAX(`id`) + 1, 64000), 64000), NOW(), NOW()
FROM `in_transit_freight_rate_card_version`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'in_transit_freight_rate_card_rule', GREATEST(COALESCE(MAX(`id`) + 1, 65000), 65000), NOW(), NOW()
FROM `in_transit_freight_rate_card_rule`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
