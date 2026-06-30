-- Store system-calculated official commission snapshots for profit calculation.

CREATE TABLE IF NOT EXISTS `official_commission_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `official_commission_calculation_fact` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site` VARCHAR(40) NOT NULL,
    `country` VARCHAR(80) DEFAULT NULL,
    `platform` VARCHAR(80) NOT NULL DEFAULT 'NOON',
    `fulfillment_type` VARCHAR(80) NOT NULL DEFAULT 'FBN',
    `variant_id` BIGINT NOT NULL,
    `sku_id` VARCHAR(160) DEFAULT NULL,
    `partner_sku` VARCHAR(160) DEFAULT NULL,
    `child_sku` VARCHAR(160) DEFAULT NULL,
    `brand` VARCHAR(200) DEFAULT NULL,
    `product_fulltype` VARCHAR(500) DEFAULT NULL,
    `sale_price` DECIMAL(18,6) DEFAULT NULL,
    `market_currency` VARCHAR(30) DEFAULT NULL,
    `calculation_date` DATE DEFAULT NULL,
    `category_path` VARCHAR(500) DEFAULT NULL,
    `category_name` VARCHAR(500) DEFAULT NULL,
    `brand_restriction` VARCHAR(200) DEFAULT NULL,
    `amount_range_label` VARCHAR(200) DEFAULT NULL,
    `amount_min` DECIMAL(18,6) DEFAULT NULL,
    `amount_max` DECIMAL(18,6) DEFAULT NULL,
    `amount_currency` VARCHAR(30) DEFAULT NULL,
    `commission_rate` DECIMAL(18,8) DEFAULT NULL,
    `commission_amount` DECIMAL(18,6) DEFAULT NULL,
    `currency` VARCHAR(30) DEFAULT NULL,
    `tax_multiplier` DECIMAL(10,4) DEFAULT NULL,
    `tax_included_commission_amount` DECIMAL(18,6) DEFAULT NULL,
    `matched_rule_natural_key` VARCHAR(500) DEFAULT NULL,
    `source_version_id` BIGINT DEFAULT NULL,
    `evidence_json` JSON DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL,
    `failure_code` VARCHAR(120) DEFAULT NULL,
    `message` VARCHAR(500) DEFAULT NULL,
    `calculated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_official_commission_calculation_fact_scope` (`owner_user_id`, `store_code`, `site`, `variant_id`),
    KEY `idx_official_commission_calculation_fact_sku` (`owner_user_id`, `store_code`, `site`, `sku_id`),
    KEY `idx_official_commission_calculation_fact_status` (`status`),
    KEY `idx_official_commission_calculation_fact_calculated` (`calculated_at`),
    KEY `idx_official_commission_calculation_fact_source` (`source_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `official_commission_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'commission_calculation_fact', GREATEST(COALESCE(MAX(id), 740000), 740000), NOW(), NOW()
FROM `official_commission_calculation_fact`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
