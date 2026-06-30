-- Store system-calculated official outbound fee snapshots for profit calculation.

CREATE TABLE IF NOT EXISTS `official_outbound_fee_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `official_outbound_fee_calculation_fact` (
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
    `effective_source_id` BIGINT DEFAULT NULL,
    `effective_source_type` VARCHAR(40) DEFAULT NULL,
    `product_length_cm` DECIMAL(18,6) DEFAULT NULL,
    `product_width_cm` DECIMAL(18,6) DEFAULT NULL,
    `product_height_cm` DECIMAL(18,6) DEFAULT NULL,
    `product_weight_g` DECIMAL(18,6) DEFAULT NULL,
    `sale_price` DECIMAL(18,6) DEFAULT NULL,
    `market_currency` VARCHAR(30) DEFAULT NULL,
    `calculation_date` DATE DEFAULT NULL,
    `fee_amount` DECIMAL(18,6) DEFAULT NULL,
    `currency` VARCHAR(30) DEFAULT NULL,
    `tax_multiplier` DECIMAL(10,4) DEFAULT NULL,
    `tax_included_fee_amount` DECIMAL(18,6) DEFAULT NULL,
    `matched_classification_name` VARCHAR(160) DEFAULT NULL,
    `matched_slab_natural_key` VARCHAR(500) DEFAULT NULL,
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
    KEY `idx_official_outbound_fee_calculation_fact_scope` (`owner_user_id`, `store_code`, `site`, `variant_id`),
    KEY `idx_official_outbound_fee_calculation_fact_sku` (`owner_user_id`, `store_code`, `site`, `sku_id`),
    KEY `idx_official_outbound_fee_calculation_fact_status` (`status`),
    KEY `idx_official_outbound_fee_calculation_fact_source` (`effective_source_id`, `effective_source_type`),
    KEY `idx_official_outbound_fee_calculation_fact_calculated` (`calculated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `official_outbound_fee_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'outbound_fee_calculation_fact', GREATEST(COALESCE(MAX(id), 720000), 720000), NOW(), NOW()
FROM `official_outbound_fee_calculation_fact`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

SET @schema_name = DATABASE();

SET @add_sku_id = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `official_outbound_fee_calculation_fact` ADD COLUMN `sku_id` VARCHAR(160) DEFAULT NULL AFTER `variant_id`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'official_outbound_fee_calculation_fact' AND COLUMN_NAME = 'sku_id'
);
PREPARE stmt FROM @add_sku_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_partner_sku = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `official_outbound_fee_calculation_fact` ADD COLUMN `partner_sku` VARCHAR(160) DEFAULT NULL AFTER `sku_id`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'official_outbound_fee_calculation_fact' AND COLUMN_NAME = 'partner_sku'
);
PREPARE stmt FROM @add_partner_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_child_sku = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `official_outbound_fee_calculation_fact` ADD COLUMN `child_sku` VARCHAR(160) DEFAULT NULL AFTER `partner_sku`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'official_outbound_fee_calculation_fact' AND COLUMN_NAME = 'child_sku'
);
PREPARE stmt FROM @add_child_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_tax_multiplier = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `official_outbound_fee_calculation_fact` ADD COLUMN `tax_multiplier` DECIMAL(10,4) DEFAULT NULL AFTER `currency`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'official_outbound_fee_calculation_fact' AND COLUMN_NAME = 'tax_multiplier'
);
PREPARE stmt FROM @add_tax_multiplier;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_tax_included_fee_amount = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `official_outbound_fee_calculation_fact` ADD COLUMN `tax_included_fee_amount` DECIMAL(18,6) DEFAULT NULL AFTER `tax_multiplier`',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'official_outbound_fee_calculation_fact' AND COLUMN_NAME = 'tax_included_fee_amount'
);
PREPARE stmt FROM @add_tax_included_fee_amount;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_sku_index = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `official_outbound_fee_calculation_fact` ADD KEY `idx_official_outbound_fee_calculation_fact_sku` (`owner_user_id`, `store_code`, `site`, `sku_id`)',
        'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'official_outbound_fee_calculation_fact' AND INDEX_NAME = 'idx_official_outbound_fee_calculation_fact_sku'
);
PREPARE stmt FROM @add_sku_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `official_outbound_fee_calculation_fact` fact
JOIN `product_variant` pv ON pv.id = fact.variant_id
SET fact.partner_sku = COALESCE(fact.partner_sku, pv.partner_sku),
    fact.child_sku = COALESCE(fact.child_sku, pv.child_sku),
    fact.sku_id = COALESCE(fact.sku_id, pv.partner_sku, pv.child_sku, CAST(fact.variant_id AS CHAR))
WHERE fact.sku_id IS NULL OR fact.partner_sku IS NULL OR fact.child_sku IS NULL;

UPDATE `official_outbound_fee_calculation_fact`
SET tax_multiplier = CASE
        WHEN UPPER(site) IN ('SA', 'KSA') THEN 1.15
        WHEN UPPER(site) IN ('AE', 'UAE') THEN 1.05
        ELSE NULL
    END
WHERE tax_multiplier IS NULL;

UPDATE `official_outbound_fee_calculation_fact`
SET tax_included_fee_amount = ROUND(fee_amount * tax_multiplier, 2)
WHERE status = 'CALCULATED'
  AND fee_amount IS NOT NULL
  AND tax_multiplier IS NOT NULL
  AND tax_included_fee_amount IS NULL;
