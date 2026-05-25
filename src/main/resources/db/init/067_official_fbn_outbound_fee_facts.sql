-- Upgrade the official FBN outbound fee standard from one legacy fee row to calculation-ready facts.
-- `outbound_fee_rule` stays readable for old versions; new parsing should emit the structured item types below.

CREATE TABLE IF NOT EXISTS `official_outbound_size_classification_rule` (
    `id` BIGINT NOT NULL,
    `natural_key` VARCHAR(500) NOT NULL,
    `country` VARCHAR(80) NOT NULL,
    `platform` VARCHAR(80) NOT NULL,
    `fulfillment_type` VARCHAR(80) NOT NULL,
    `classification_name` VARCHAR(160) NOT NULL,
    `longest_side_max_cm` DECIMAL(18,6) DEFAULT NULL,
    `median_side_max_cm` DECIMAL(18,6) DEFAULT NULL,
    `shortest_side_max_cm` DECIMAL(18,6) DEFAULT NULL,
    `max_shipping_weight_grams` DECIMAL(18,6) DEFAULT NULL,
    `packaging_weight_grams` DECIMAL(18,6) DEFAULT NULL,
    `priority` INT DEFAULT NULL,
    `dimension_unit` VARCHAR(40) DEFAULT NULL,
    `weight_unit` VARCHAR(40) DEFAULT NULL,
    `effective_from` DATE DEFAULT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `source_task_id` BIGINT DEFAULT NULL,
    `source_result_id` BIGINT DEFAULT NULL,
    `source_version_id` BIGINT DEFAULT NULL,
    `source_version_item_id` BIGINT DEFAULT NULL,
    `source_file_name` VARCHAR(255) DEFAULT NULL,
    `source_locator` VARCHAR(255) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_official_outbound_size_classification_rule_source_item` (`source_version_item_id`),
    KEY `idx_official_outbound_size_classification_rule_natural_key` (`natural_key`),
    KEY `idx_official_outbound_size_classification_rule_source_version` (`source_version_id`),
    KEY `idx_official_outbound_size_classification_rule_status` (`status`),
    KEY `idx_official_outbound_size_classification_rule_scope` (`country`, `platform`, `fulfillment_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `official_outbound_fee_weight_slab_rule` (
    `id` BIGINT NOT NULL,
    `natural_key` VARCHAR(500) NOT NULL,
    `country` VARCHAR(80) NOT NULL,
    `platform` VARCHAR(80) NOT NULL,
    `fulfillment_type` VARCHAR(80) NOT NULL,
    `classification_name` VARCHAR(160) NOT NULL,
    `weight_min_grams` DECIMAL(18,6) DEFAULT NULL,
    `weight_min_inclusive` BIT(1) DEFAULT NULL,
    `weight_max_grams` DECIMAL(18,6) DEFAULT NULL,
    `weight_max_inclusive` BIT(1) DEFAULT NULL,
    `standard_fee_amount` DECIMAL(18,6) DEFAULT NULL,
    `high_asp_fee_amount` DECIMAL(18,6) DEFAULT NULL,
    `sales_price_threshold_amount` DECIMAL(18,6) DEFAULT NULL,
    `threshold_currency` VARCHAR(30) DEFAULT NULL,
    `extra_weight_step_grams` DECIMAL(18,6) DEFAULT NULL,
    `extra_fee_amount` DECIMAL(18,6) DEFAULT NULL,
    `currency` VARCHAR(30) NOT NULL,
    `effective_from` DATE DEFAULT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `source_task_id` BIGINT DEFAULT NULL,
    `source_result_id` BIGINT DEFAULT NULL,
    `source_version_id` BIGINT DEFAULT NULL,
    `source_version_item_id` BIGINT DEFAULT NULL,
    `source_file_name` VARCHAR(255) DEFAULT NULL,
    `source_locator` VARCHAR(255) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_official_outbound_fee_weight_slab_rule_source_item` (`source_version_item_id`),
    KEY `idx_official_outbound_fee_weight_slab_rule_natural_key` (`natural_key`),
    KEY `idx_official_outbound_fee_weight_slab_rule_source_version` (`source_version_id`),
    KEY `idx_official_outbound_fee_weight_slab_rule_status` (`status`),
    KEY `idx_official_outbound_fee_weight_slab_rule_scope` (`country`, `platform`, `fulfillment_type`, `classification_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `official_outbound_fee_calculation_policy` (
    `id` BIGINT NOT NULL,
    `natural_key` VARCHAR(500) NOT NULL,
    `country` VARCHAR(80) NOT NULL,
    `platform` VARCHAR(80) NOT NULL,
    `fulfillment_type` VARCHAR(80) NOT NULL,
    `policy_name` VARCHAR(160) DEFAULT NULL,
    `shipping_weight_formula` VARCHAR(255) NOT NULL,
    `dimension_sort_rule` VARCHAR(255) DEFAULT NULL,
    `weight_boundary_rule` VARCHAR(255) DEFAULT NULL,
    `rounding_rule` VARCHAR(255) DEFAULT NULL,
    `sales_price_threshold_amount` DECIMAL(18,6) DEFAULT NULL,
    `threshold_currency` VARCHAR(30) DEFAULT NULL,
    `dimension_unit` VARCHAR(40) DEFAULT NULL,
    `weight_unit` VARCHAR(40) DEFAULT NULL,
    `effective_from` DATE DEFAULT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `source_task_id` BIGINT DEFAULT NULL,
    `source_result_id` BIGINT DEFAULT NULL,
    `source_version_id` BIGINT DEFAULT NULL,
    `source_version_item_id` BIGINT DEFAULT NULL,
    `source_file_name` VARCHAR(255) DEFAULT NULL,
    `source_locator` VARCHAR(255) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_official_outbound_fee_calculation_policy_source_item` (`source_version_item_id`),
    KEY `idx_official_outbound_fee_calculation_policy_natural_key` (`natural_key`),
    KEY `idx_official_outbound_fee_calculation_policy_source_version` (`source_version_id`),
    KEY `idx_official_outbound_fee_calculation_policy_status` (`status`),
    KEY `idx_official_outbound_fee_calculation_policy_scope` (`country`, `platform`, `fulfillment_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `file_mgmt_parse_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('file_mgmt_parse_item_standard', 3040, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

UPDATE `file_mgmt_parse_standard_version`
SET
    `result_schema_json` = '{"items":["outbound_fee_rule","outbound_size_classification_rule","outbound_fee_weight_slab_rule","outbound_fee_calculation_policy"]}',
    `normalization_rule_json` = '{"countries":["KSA","UAE"],"platforms":["NOON"],"fulfillmentTypes":["FBN"],"dimensionUnit":"cm","weightUnit":"grams","currency":["SAR","AED"],"shippingWeightFormula":"physical_weight_plus_packaging_weight"}',
    `validation_rule_json` = '{"requiredItemTypes":["outbound_size_classification_rule","outbound_fee_weight_slab_rule","outbound_fee_calculation_policy"],"legacyItemType":"outbound_fee_rule","hardIssueSeverities":["error","blocker"]}',
    `display_config_json` = '{"groupBy":"itemType","overviewColumns":["country","platform","fulfillmentType","classificationName","longestSideMaxCm","maxShippingWeightGrams","packagingWeightGrams","weightMaxGrams","standardFeeAmount","highAspFeeAmount","currency","shippingWeightFormula"]}',
    `diff_rule_json` = '{"compareByItemType":true,"numericFields":["longestSideMaxCm","medianSideMaxCm","shortestSideMaxCm","maxShippingWeightGrams","packagingWeightGrams","weightMinGrams","weightMaxGrams","standardFeeAmount","highAspFeeAmount","salesPriceThresholdAmount","extraWeightStepGrams","extraFeeAmount"],"enumFields":["country","platform","fulfillmentType","classificationName","currency","thresholdCurrency"]}',
    `gmt_updated` = NOW()
WHERE `id` = 2002
  AND `is_deleted` = b'0';

UPDATE `file_mgmt_parse_item_standard`
SET
    `item_label` = '出仓费规则',
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW()
WHERE `standard_version_id` = 2002
  AND `item_type` = 'outbound_fee_rule';

INSERT INTO `file_mgmt_parse_item_standard` (
    `id`, `standard_version_id`, `item_type`, `item_label`, `natural_key_json`, `field_schema_json`,
    `display_config_json`, `validation_rule_json`, `diff_rule_json`, `sort_no`, `status`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (3030, 2002, 'outbound_size_classification_rule', '出仓费规格分级',
     '{"fields":["country","platform","fulfillmentType","classificationName","effectiveDate"]}',
     '{"country":"string","platform":"string","fulfillmentType":"string","classificationName":"string","longestSideMaxCm":"decimal","medianSideMaxCm":"decimal","shortestSideMaxCm":"decimal","maxShippingWeightGrams":"decimal","packagingWeightGrams":"decimal","priority":"integer","dimensionUnit":"string","weightUnit":"string","effectiveDate":"date","sourceVersion":"string"}',
     '{"columns":["country","platform","fulfillmentType","classificationName","longestSideMaxCm","medianSideMaxCm","shortestSideMaxCm","maxShippingWeightGrams","packagingWeightGrams","effectiveDate"]}',
     '{"required":["country","platform","fulfillmentType","classificationName"]}',
     '{"compareFields":["longestSideMaxCm","medianSideMaxCm","shortestSideMaxCm","maxShippingWeightGrams","packagingWeightGrams","priority","dimensionUnit","weightUnit","effectiveDate","sourceVersion"]}',
     21, 'active', b'0', 1, 1),
    (3031, 2002, 'outbound_fee_weight_slab_rule', '出仓费重量费用',
     '{"fields":["country","platform","fulfillmentType","classificationName","weightMinGrams","weightMinInclusive","weightMaxGrams","weightMaxInclusive","currency","effectiveDate"]}',
     '{"country":"string","platform":"string","fulfillmentType":"string","classificationName":"string","weightMinGrams":"decimal","weightMinInclusive":"boolean","weightMaxGrams":"decimal","weightMaxInclusive":"boolean","standardFeeAmount":"decimal","highAspFeeAmount":"decimal","salesPriceThresholdAmount":"decimal","thresholdCurrency":"string","extraWeightStepGrams":"decimal","extraFeeAmount":"decimal","currency":"string","effectiveDate":"date","sourceVersion":"string"}',
     '{"columns":["country","platform","fulfillmentType","classificationName","weightMinGrams","weightMaxGrams","standardFeeAmount","highAspFeeAmount","currency","salesPriceThresholdAmount","thresholdCurrency","extraWeightStepGrams","extraFeeAmount","effectiveDate"]}',
     '{"required":["country","platform","fulfillmentType","classificationName","weightMaxGrams","standardFeeAmount","currency"]}',
     '{"compareFields":["weightMinGrams","weightMinInclusive","weightMaxGrams","weightMaxInclusive","standardFeeAmount","highAspFeeAmount","salesPriceThresholdAmount","thresholdCurrency","extraWeightStepGrams","extraFeeAmount","currency","effectiveDate","sourceVersion"]}',
     22, 'active', b'0', 1, 1),
    (3032, 2002, 'outbound_fee_calculation_policy', '出仓费计算策略',
     '{"fields":["country","platform","fulfillmentType","effectiveDate"]}',
     '{"country":"string","platform":"string","fulfillmentType":"string","policyName":"string","shippingWeightFormula":"string","dimensionSortRule":"string","weightBoundaryRule":"string","roundingRule":"string","salesPriceThresholdAmount":"decimal","thresholdCurrency":"string","dimensionUnit":"string","weightUnit":"string","effectiveDate":"date","sourceVersion":"string"}',
     '{"columns":["country","platform","fulfillmentType","shippingWeightFormula","dimensionSortRule","weightBoundaryRule","roundingRule","salesPriceThresholdAmount","thresholdCurrency","effectiveDate"]}',
     '{"required":["country","platform","fulfillmentType","shippingWeightFormula"]}',
     '{"compareFields":["policyName","shippingWeightFormula","dimensionSortRule","weightBoundaryRule","roundingRule","salesPriceThresholdAmount","thresholdCurrency","dimensionUnit","weightUnit","effectiveDate","sourceVersion"]}',
     23, 'active', b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `natural_key_json` = VALUES(`natural_key_json`),
    `field_schema_json` = VALUES(`field_schema_json`),
    `display_config_json` = VALUES(`display_config_json`),
    `validation_rule_json` = VALUES(`validation_rule_json`),
    `diff_rule_json` = VALUES(`diff_rule_json`),
    `sort_no` = VALUES(`sort_no`),
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW();
