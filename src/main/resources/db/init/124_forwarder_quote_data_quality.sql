-- Forwarder quote data quality baseline.
-- Scope: standardize quote types and expose coverage gaps before purchase-order logistics planning consumes rates.

CREATE TABLE IF NOT EXISTS `forwarder_quote_reference_value` (
    `id` BIGINT NOT NULL,
    `domain` VARCHAR(80) NOT NULL,
    `code` VARCHAR(80) NOT NULL,
    `display_name` VARCHAR(160) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `sort_no` INT NOT NULL DEFAULT 0,
    `active` BIT(1) NOT NULL DEFAULT b'1',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fq_ref_domain_code` (`domain`, `code`),
    KEY `idx_fq_ref_domain_active` (`domain`, `active`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_quote_required_lane` (
    `id` BIGINT NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `country` VARCHAR(80) NOT NULL,
    `transport_mode` VARCHAR(30) NOT NULL,
    `target_platform` VARCHAR(50) DEFAULT NULL,
    `delivery_city` VARCHAR(100) DEFAULT NULL,
    `destination_node` VARCHAR(150) DEFAULT NULL,
    `priority` INT NOT NULL DEFAULT 0,
    `required_for_purchase_order` BIT(1) NOT NULL DEFAULT b'1',
    `status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    `remark` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fq_required_lane` (`site_code`, `transport_mode`, `target_platform`, `delivery_city`),
    KEY `idx_fq_required_lane_scope` (`country`, `transport_mode`, `target_platform`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_quote_required_price_unit` (
    `id` BIGINT NOT NULL,
    `required_lane_id` BIGINT NOT NULL,
    `billing_unit` VARCHAR(40) NOT NULL,
    `required_level` VARCHAR(40) NOT NULL DEFAULT 'REQUIRED',
    `sort_no` INT NOT NULL DEFAULT 0,
    `remark` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fq_required_price_unit` (`required_lane_id`, `billing_unit`),
    KEY `idx_fq_required_price_unit_lane` (`required_lane_id`, `required_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `forwarder_quote_reference_value` (`id`, `domain`, `code`, `display_name`, `description`, `sort_no`, `created_by`, `updated_by`)
VALUES
    (980001, 'transport_mode', 'SEA', '海运', 'Sea freight service line.', 10, 1, 1),
    (980002, 'transport_mode', 'AIR', '空运', 'Air freight service line.', 20, 1, 1),
    (980003, 'transport_mode', 'WAREHOUSE', '海外仓', 'Warehouse processing or warehouse-only service.', 30, 1, 1),
    (980004, 'transport_mode', 'EXPRESS', '快递', 'Express or courier service line.', 40, 1, 1),
    (980101, 'pricing_model', 'PER_CBM', '按体积', 'Price is charged by CBM or volume unit.', 10, 1, 1),
    (980102, 'pricing_model', 'PER_KG', '按重量', 'Price is charged by KG or chargeable weight.', 20, 1, 1),
    (980103, 'pricing_model', 'PER_PIECE', '按件', 'Price is charged by piece or SKU count.', 30, 1, 1),
    (980104, 'pricing_model', 'PER_SHIPMENT', '按票', 'Price is charged per shipment.', 40, 1, 1),
    (980105, 'pricing_model', 'FIXED', '固定费用', 'Fixed amount fee.', 50, 1, 1),
    (980106, 'pricing_model', 'PERCENTAGE', '按比例', 'Rate-based fee.', 60, 1, 1),
    (980107, 'pricing_model', 'INQUIRY', '询价', 'No fixed unit price; manual inquiry required.', 90, 1, 1),
    (980201, 'billing_unit', 'CBM', '立方', 'Cubic meter billing unit.', 10, 1, 1),
    (980202, 'billing_unit', 'KG', '公斤', 'Kilogram billing unit.', 20, 1, 1),
    (980203, 'billing_unit', 'PIECE', '件', 'Piece billing unit.', 30, 1, 1),
    (980204, 'billing_unit', 'BOX', '箱', 'Box billing unit.', 40, 1, 1),
    (980205, 'billing_unit', 'SHIPMENT', '票', 'Shipment billing unit.', 50, 1, 1),
    (980206, 'billing_unit', 'ORDER', '订单', 'Order billing unit.', 60, 1, 1),
    (980207, 'billing_unit', 'PAGE', '页', 'Document page billing unit.', 70, 1, 1),
    (980301, 'price_status', 'NORMAL', '正常报价', 'Concrete unit price can be used for estimate.', 10, 1, 1),
    (980302, 'price_status', 'INQUIRY', '需询价', 'No direct price; business confirmation required.', 20, 1, 1),
    (980303, 'price_status', 'ASK_QUOTE', '需报价', 'Forwarder quotation required.', 30, 1, 1),
    (980304, 'price_status', 'STARTING_PRICE', '起步价', 'Starting price, not final automatic estimate.', 40, 1, 1),
    (980305, 'price_status', 'INCLUDED', '已包含', 'Fee is included in base price.', 50, 1, 1),
    (980306, 'price_status', 'FREE', '免费', 'No charge.', 60, 1, 1),
    (980307, 'price_status', 'MANUAL_CONFIRM', '人工确认', 'Manual confirmation required before estimate.', 70, 1, 1)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `description` = VALUES(`description`),
    `sort_no` = VALUES(`sort_no`),
    `active` = b'1',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_required_lane` (`id`, `site_code`, `country`, `transport_mode`, `target_platform`, `delivery_city`, `destination_node`, `priority`, `remark`, `created_by`, `updated_by`)
VALUES
    (981001, 'SA', '沙特', 'SEA', 'FBN', '利雅得/RUH', 'FBN利雅得仓', 10, 'Purchase order sea planning baseline for Saudi FBN Riyadh.', 1, 1),
    (981002, 'SA', '沙特', 'AIR', 'FBN', '利雅得/RUH', 'FBN利雅得仓', 20, 'Purchase order air fallback baseline for Saudi FBN Riyadh.', 1, 1),
    (981003, 'AE', '阿联酋', 'SEA', NULL, '阿联酋仓', NULL, 30, 'Purchase order sea planning baseline for UAE warehouse-to-warehouse routes. Do not bind to FBN unless source quote says FBN.', 1, 1),
    (981004, 'AE', '阿联酋', 'AIR', NULL, '阿联酋仓', NULL, 40, 'Purchase order air fallback baseline for UAE warehouse-to-warehouse routes. Do not bind to FBN unless source quote says FBN.', 1, 1)
ON DUPLICATE KEY UPDATE
    `country` = VALUES(`country`),
    `target_platform` = VALUES(`target_platform`),
    `delivery_city` = VALUES(`delivery_city`),
    `destination_node` = VALUES(`destination_node`),
    `priority` = VALUES(`priority`),
    `required_for_purchase_order` = b'1',
    `status` = 'ACTIVE',
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_required_price_unit` (`id`, `required_lane_id`, `billing_unit`, `required_level`, `sort_no`, `remark`, `created_by`, `updated_by`)
VALUES
    (982001, 981001, 'CBM', 'REQUIRED', 10, 'Sea freight must have volume price for planning.', 1, 1),
    (982002, 981001, 'KG', 'OPTIONAL', 20, 'Some sea services also provide weight price for special cargo.', 1, 1),
    (982003, 981002, 'KG', 'REQUIRED', 10, 'Air freight must have KG price for planning.', 1, 1),
    (982004, 981003, 'CBM', 'REQUIRED', 10, 'UAE sea freight must have volume price before purchase-order recommendation is complete.', 1, 1),
    (982005, 981003, 'KG', 'OPTIONAL', 20, 'UAE sea freight may provide weight price for special cargo.', 1, 1),
    (982006, 981004, 'KG', 'REQUIRED', 10, 'UAE air freight must have KG price before purchase-order recommendation is complete.', 1, 1)
ON DUPLICATE KEY UPDATE
    `required_level` = VALUES(`required_level`),
    `sort_no` = VALUES(`sort_no`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

CREATE OR REPLACE VIEW `forwarder_quote_required_lane_coverage_v` AS
SELECT
    lane.id AS required_lane_id,
    lane.site_code,
    lane.country,
    lane.transport_mode,
    lane.target_platform,
    lane.delivery_city,
    COUNT(DISTINCT line.forwarder_code) AS forwarder_count,
    COUNT(DISTINCT line.service_code) AS service_line_count,
    COUNT(DISTINCT price.id) AS price_rule_count,
    COUNT(DISTINCT CASE WHEN price.price_status = 'NORMAL' THEN price.id END) AS normal_price_rule_count,
    GROUP_CONCAT(DISTINCT line.forwarder_code ORDER BY line.forwarder_code SEPARATOR ',') AS forwarder_codes,
    CASE
        WHEN COUNT(DISTINCT line.service_code) = 0 THEN 'missing_service_line'
        WHEN COUNT(DISTINCT price.id) = 0 THEN 'missing_price_rule'
        WHEN COUNT(DISTINCT CASE WHEN price.price_status = 'NORMAL' THEN price.id END) = 0 THEN 'missing_normal_price'
        ELSE 'ready'
    END AS coverage_status
FROM `forwarder_quote_required_lane` lane
LEFT JOIN `forwarder_quote_service_line` line
    ON line.country = lane.country
   AND line.transport_mode = lane.transport_mode
   AND (lane.target_platform IS NULL OR line.target_platform = lane.target_platform)
   AND line.active_for_mvp = b'1'
LEFT JOIN `forwarder_quote_base_price` price
    ON price.service_code = line.service_code
   AND price.quote_version_id = line.quote_version_id
WHERE lane.status = 'ACTIVE'
  AND lane.required_for_purchase_order = b'1'
GROUP BY lane.id, lane.site_code, lane.country, lane.transport_mode, lane.target_platform, lane.delivery_city;

CREATE OR REPLACE VIEW `forwarder_quote_required_price_unit_coverage_v` AS
SELECT
    lane.id AS required_lane_id,
    lane.site_code,
    lane.country,
    lane.transport_mode,
    lane.target_platform,
    unit.billing_unit,
    unit.required_level,
    COUNT(DISTINCT line.service_code) AS service_line_count,
    COUNT(DISTINCT price.id) AS price_rule_count,
    COUNT(DISTINCT CASE WHEN price.price_status = 'NORMAL' THEN price.id END) AS normal_price_rule_count,
    MIN(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END) AS min_normal_unit_price,
    MAX(CASE WHEN price.price_status = 'NORMAL' THEN price.unit_price END) AS max_normal_unit_price,
    GROUP_CONCAT(DISTINCT CONCAT(line.forwarder_code, ':', line.service_code) ORDER BY line.forwarder_code, line.service_code SEPARATOR ',') AS service_lines,
    CASE
        WHEN COUNT(DISTINCT line.service_code) = 0 THEN 'missing_service_line'
        WHEN COUNT(DISTINCT price.id) = 0 AND unit.required_level = 'REQUIRED' THEN 'missing_required_unit_price'
        WHEN COUNT(DISTINCT CASE WHEN price.price_status = 'NORMAL' THEN price.id END) = 0 AND unit.required_level = 'REQUIRED' THEN 'missing_required_normal_price'
        WHEN COUNT(DISTINCT price.id) = 0 THEN 'missing_optional_unit_price'
        ELSE 'ready'
    END AS coverage_status
FROM `forwarder_quote_required_price_unit` unit
JOIN `forwarder_quote_required_lane` lane
    ON lane.id = unit.required_lane_id
   AND lane.status = 'ACTIVE'
   AND lane.required_for_purchase_order = b'1'
LEFT JOIN `forwarder_quote_service_line` line
    ON line.country = lane.country
   AND line.transport_mode = lane.transport_mode
   AND (lane.target_platform IS NULL OR line.target_platform = lane.target_platform)
   AND line.active_for_mvp = b'1'
LEFT JOIN `forwarder_quote_base_price` price
    ON price.service_code = line.service_code
   AND price.quote_version_id = line.quote_version_id
   AND UPPER(COALESCE(price.billing_unit, '')) = unit.billing_unit
GROUP BY lane.id, lane.site_code, lane.country, lane.transport_mode, lane.target_platform, unit.billing_unit, unit.required_level;

CREATE OR REPLACE VIEW `forwarder_quote_base_price_quality_issue_v` AS
SELECT
    price.id AS price_rule_id,
    price.service_code,
    price.price_rule_code,
    'unknown_pricing_model' AS issue_code,
    'hard_error' AS severity,
    CONCAT('Unknown pricing_model: ', COALESCE(price.pricing_model, 'NULL')) AS message
FROM `forwarder_quote_base_price` price
LEFT JOIN `forwarder_quote_reference_value` ref_value
    ON ref_value.domain = 'pricing_model'
   AND ref_value.code = price.pricing_model
   AND ref_value.active = b'1'
WHERE ref_value.id IS NULL
UNION ALL
SELECT
    price.id AS price_rule_id,
    price.service_code,
    price.price_rule_code,
    'unknown_billing_unit' AS issue_code,
    'hard_error' AS severity,
    CONCAT('Unknown billing_unit: ', COALESCE(price.billing_unit, 'NULL')) AS message
FROM `forwarder_quote_base_price` price
LEFT JOIN `forwarder_quote_reference_value` ref_value
    ON ref_value.domain = 'billing_unit'
   AND ref_value.code = price.billing_unit
   AND ref_value.active = b'1'
WHERE price.price_status = 'NORMAL'
  AND ref_value.id IS NULL
UNION ALL
SELECT
    price.id AS price_rule_id,
    price.service_code,
    price.price_rule_code,
    'unknown_price_status' AS issue_code,
    'hard_error' AS severity,
    CONCAT('Unknown price_status: ', COALESCE(price.price_status, 'NULL')) AS message
FROM `forwarder_quote_base_price` price
LEFT JOIN `forwarder_quote_reference_value` ref_value
    ON ref_value.domain = 'price_status'
   AND ref_value.code = price.price_status
   AND ref_value.active = b'1'
WHERE ref_value.id IS NULL
UNION ALL
SELECT
    price.id AS price_rule_id,
    price.service_code,
    price.price_rule_code,
    'normal_price_missing_amount' AS issue_code,
    'hard_error' AS severity,
    'NORMAL price must have unit_price, currency and billing_unit.' AS message
FROM `forwarder_quote_base_price` price
WHERE price.price_status = 'NORMAL'
  AND (price.unit_price IS NULL OR price.currency IS NULL OR price.billing_unit IS NULL)
UNION ALL
SELECT
    price.id AS price_rule_id,
    price.service_code,
    price.price_rule_code,
    'inquiry_price_has_amount' AS issue_code,
    'warning' AS severity,
    'INQUIRY price should not be mixed with a concrete unit_price.' AS message
FROM `forwarder_quote_base_price` price
WHERE price.price_status = 'INQUIRY'
  AND price.unit_price IS NOT NULL;
