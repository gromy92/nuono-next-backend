-- Purchase order logistics route templates and persisted cost components.
-- Quote facts remain in forwarder_quote_* tables; this layer composes route candidates for planning.

INSERT INTO `forwarder_quote_reference_value` (`id`, `domain`, `code`, `display_name`, `description`, `sort_no`, `created_by`, `updated_by`)
VALUES
    (980108, 'pricing_model', 'PER_CBM_DAY', '按立方每日', 'Warehouse storage charged by CBM per day.', 45, 1, 1),
    (980109, 'pricing_model', 'FIXED_PER_UNIT', '按单位固定费', 'Legacy warehouse fee model charged per concrete business unit.', 46, 1, 1)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `description` = VALUES(`description`),
    `sort_no` = VALUES(`sort_no`),
    `active` = b'1',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

CREATE TABLE IF NOT EXISTS `forwarder_quote_route_template` (
    `id` BIGINT NOT NULL,
    `route_code` VARCHAR(120) NOT NULL,
    `quote_version_id` BIGINT NOT NULL,
    `quote_version_code` VARCHAR(80) NOT NULL,
    `forwarder_code` VARCHAR(50) NOT NULL,
    `route_name` VARCHAR(255) NOT NULL,
    `country` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) DEFAULT NULL,
    `transport_mode` VARCHAR(30) NOT NULL,
    `target_platform` VARCHAR(50) DEFAULT NULL,
    `delivery_city` VARCHAR(100) DEFAULT NULL,
    `destination_node` VARCHAR(150) DEFAULT NULL,
    `route_scope` VARCHAR(120) DEFAULT NULL,
    `active_for_purchase_order` BIT(1) NOT NULL DEFAULT b'1',
    `source_file_name` VARCHAR(255) DEFAULT NULL,
    `source_sheet_or_page` VARCHAR(120) DEFAULT NULL,
    `source_row_or_locator` VARCHAR(120) DEFAULT NULL,
    `source_type` VARCHAR(40) DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fq_route_template_code` (`route_code`),
    KEY `idx_fq_route_template_scope` (`country`, `site_code`, `transport_mode`, `target_platform`, `active_for_purchase_order`),
    KEY `idx_fq_route_template_version` (`quote_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_quote_route_template_segment` (
    `id` BIGINT NOT NULL,
    `route_code` VARCHAR(120) NOT NULL,
    `segment_no` INT NOT NULL,
    `segment_role` VARCHAR(60) NOT NULL,
    `service_code` VARCHAR(100) NOT NULL,
    `cost_policy` VARCHAR(60) NOT NULL DEFAULT 'ESTIMATE',
    `required` BIT(1) NOT NULL DEFAULT b'1',
    `display_name` VARCHAR(255) DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fq_route_template_segment` (`route_code`, `segment_no`),
    KEY `idx_fq_route_template_segment_route` (`route_code`),
    KEY `idx_fq_route_template_segment_service` (`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_purchase_order_logistics_recommendation` (
    `id` BIGINT NOT NULL,
    `logistics_plan_id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `forwarder_code` VARCHAR(50) NOT NULL,
    `service_code` VARCHAR(100) NOT NULL,
    `transport_mode` VARCHAR(30) NOT NULL,
    `rank_no` INT NOT NULL,
    `recommended` BIT(1) NOT NULL DEFAULT b'0',
    `estimate_status` VARCHAR(80) DEFAULT NULL,
    `currency` VARCHAR(20) DEFAULT NULL,
    `estimated_total_amount` DECIMAL(14,4) DEFAULT NULL,
    `recurring_amount_per_day` DECIMAL(14,4) DEFAULT NULL,
    `snapshot_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_po_logistics_recommendation_plan` (`logistics_plan_id`, `rank_no`, `is_deleted`),
    KEY `idx_po_logistics_recommendation_order` (`purchase_order_id`, `transport_mode`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_purchase_order_logistics_cost_component` (
    `id` BIGINT NOT NULL,
    `recommendation_id` BIGINT NOT NULL,
    `logistics_plan_id` BIGINT NOT NULL,
    `component_type` VARCHAR(80) NOT NULL,
    `component_name` VARCHAR(255) NOT NULL,
    `source_table` VARCHAR(80) DEFAULT NULL,
    `source_id` BIGINT DEFAULT NULL,
    `currency` VARCHAR(20) DEFAULT NULL,
    `unit_price` DECIMAL(14,4) DEFAULT NULL,
    `billing_unit` VARCHAR(40) DEFAULT NULL,
    `billable_quantity` DECIMAL(14,6) DEFAULT NULL,
    `amount` DECIMAL(14,4) DEFAULT NULL,
    `amount_status` VARCHAR(60) NOT NULL DEFAULT 'ESTIMATED',
    `included_in_total` BIT(1) NOT NULL DEFAULT b'1',
    `formula_text` VARCHAR(500) DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_po_logistics_cost_component_recommendation` (`recommendation_id`, `component_type`),
    KEY `idx_po_logistics_cost_component_plan` (`logistics_plan_id`, `component_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `forwarder_quote_route_template` (
    `id`, `route_code`, `quote_version_id`, `quote_version_code`, `forwarder_code`, `route_name`,
    `country`, `site_code`, `transport_mode`, `target_platform`, `delivery_city`, `destination_node`,
    `route_scope`, `active_for_purchase_order`, `source_file_name`, `source_sheet_or_page`,
    `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
)
VALUES
    (983001, 'ET-SAU-AIR-FBN-RUH-20260604', 904004, 'ET-20260604', 'ET',
     '易通沙特空运一档 + 海外仓 + FBN利雅得送仓 20260604', '沙特', 'SA', 'AIR', 'FBN',
     '利雅得/RUH', 'FBN利雅得仓', '头程到ET沙特仓+海外仓处理+平台仓送仓', b'1',
     'ET易通天下物流报价-0604.xlsx', '沙特空海运双清/海外仓收费标准/海外仓末端派送费',
     'SA air R6 + WH R5-R21 + last-mile R3', 'file',
     '采购单推荐用组合路线；干线服务线本身为仓到仓。', 1, 1),
    (983002, 'ET-SAU-SEA-FBN-RUH-20260604', 904004, 'ET-20260604', 'ET',
     '易通沙特海运 + 海外仓 + FBN利雅得送仓 20260604', '沙特', 'SA', 'SEA', 'FBN',
     '利雅得/RUH', 'FBN利雅得仓', '头程到ET沙特仓+海外仓处理+平台仓送仓', b'1',
     'ET易通天下物流报价-0604.xlsx', '沙特空海运双清/海外仓收费标准/海外仓末端派送费',
     'SA sea R23-R29 + WH R5-R21 + last-mile R3', 'file',
     '采购单推荐用组合路线；干线服务线本身为仓到仓。', 1, 1),
    (983003, 'YT-SAU-SEA-FBN-RUH', 904002, 'YT-SAU-UNDATED-001', 'YT',
     '义特沙特海运双清包税 + FBN利雅得送仓', '沙特', 'SA', 'SEA', 'FBN',
     '利雅得/RUH', 'FBN利雅得仓', '报价服务线已包含送仓', b'1',
     'forwarder-standardized-saudi-fbn-riyadh-v3-20260507.xlsx', 'standardized',
     'YT sea line', 'file', '直接FBN服务线，不额外叠加ET海外仓组件。', 1, 1),
    (983004, 'ZD-SAU-AIR-FBN-RUH', 904001, 'ZD-20260411', 'ZD',
     '众鸫沙特空运专线 FBN利雅得（含送仓报价）', '沙特', 'SA', 'AIR', 'FBN',
     '利雅得/RUH', 'FBN利雅得仓', '报价服务线已包含送仓', b'1',
     'forwarder-standardized-saudi-fbn-riyadh-v3-20260507.xlsx', 'standardized',
     'ZD air line', 'file', '直接FBN服务线，不额外叠加ET海外仓组件。', 1, 1),
    (983005, 'ZD-SAU-SEA-FBN-RUH', 904001, 'ZD-20260411', 'ZD',
     '众鸫沙特海运专线到海外仓 + FBN利雅得送仓', '沙特', 'SA', 'SEA', 'FBN',
     '利雅得/RUH', '众鸫沙特海外仓/FBN利雅得仓', '头程到海外仓+FBN送仓', b'1',
     'forwarder-standardized-saudi-fbn-riyadh-v3-20260507.xlsx', 'standardized',
     'ZD sea line', 'file', '现有基础报价和FBN_DELIVERY费用继续复用。', 1, 1)
ON DUPLICATE KEY UPDATE
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `route_name` = VALUES(`route_name`),
    `country` = VALUES(`country`),
    `site_code` = VALUES(`site_code`),
    `transport_mode` = VALUES(`transport_mode`),
    `target_platform` = VALUES(`target_platform`),
    `delivery_city` = VALUES(`delivery_city`),
    `destination_node` = VALUES(`destination_node`),
    `route_scope` = VALUES(`route_scope`),
    `active_for_purchase_order` = VALUES(`active_for_purchase_order`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_route_template_segment` (
    `id`, `route_code`, `segment_no`, `segment_role`, `service_code`, `cost_policy`, `required`, `display_name`, `remark`, `created_by`, `updated_by`
)
VALUES
    (983101, 'ET-SAU-AIR-FBN-RUH-20260604', 1, 'HEADHAUL', 'ET-SAU-AIR-TIER1-WH-20260604', 'ESTIMATE', b'1', '空运干线', '按KG计费重计算。', 1, 1),
    (983102, 'ET-SAU-AIR-FBN-RUH-20260604', 2, 'WAREHOUSE_PROCESSING', 'ET-WH-PROCESS-20260604', 'ESTIMATE', b'1', '海外仓处理', '入库、仓储日费、拣货按可判定项估算。', 1, 1),
    (983103, 'ET-SAU-AIR-FBN-RUH-20260604', 3, 'LAST_MILE', 'ET-LAST-MILE-20260604', 'ESTIMATE', b'1', 'FBN利雅得送仓', '按利雅得平台仓送仓CBM费估算。', 1, 1),
    (983104, 'ET-SAU-SEA-FBN-RUH-20260604', 1, 'HEADHAUL', 'ET-SAU-SEA-WH-20260604', 'ESTIMATE', b'1', '海运干线', '按散货CBM估算。', 1, 1),
    (983105, 'ET-SAU-SEA-FBN-RUH-20260604', 2, 'WAREHOUSE_PROCESSING', 'ET-WH-PROCESS-20260604', 'ESTIMATE', b'1', '海外仓处理', '入库、仓储日费、拣货按可判定项估算。', 1, 1),
    (983106, 'ET-SAU-SEA-FBN-RUH-20260604', 3, 'LAST_MILE', 'ET-LAST-MILE-20260604', 'ESTIMATE', b'1', 'FBN利雅得送仓', '按利雅得平台仓送仓CBM费估算。', 1, 1),
    (983107, 'YT-SAU-SEA-FBN-RUH', 1, 'HEADHAUL', 'YT-SAU-SEA-FBN-RUH', 'ESTIMATE', b'1', '海运双清包税含送仓', '不额外叠加ET海外仓组件。', 1, 1),
    (983108, 'ZD-SAU-AIR-FBN-RUH', 1, 'HEADHAUL', 'ZD-SAU-AIR-FBN-RUH', 'ESTIMATE', b'1', '空运专线含送仓', '不额外叠加ET海外仓组件。', 1, 1),
    (983109, 'ZD-SAU-SEA-FBN-RUH', 1, 'HEADHAUL', 'ZD-SAU-SEA-WH-RUH', 'ESTIMATE', b'1', '海运专线到仓', 'FBN送仓费用继续从transport_fee读取。', 1, 1)
ON DUPLICATE KEY UPDATE
    `segment_role` = VALUES(`segment_role`),
    `service_code` = VALUES(`service_code`),
    `cost_policy` = VALUES(`cost_policy`),
    `required` = VALUES(`required`),
    `display_name` = VALUES(`display_name`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

CREATE OR REPLACE VIEW `forwarder_warehouse_processing_fee_quality_issue_v` AS
SELECT
    fee.id AS fee_id,
    fee.service_code,
    fee.processing_fee_code AS fee_code,
    'unknown_pricing_model' AS issue_code,
    'hard_error' AS severity,
    CONCAT('Unknown pricing_model: ', COALESCE(fee.pricing_model, 'NULL')) AS message
FROM `forwarder_warehouse_processing_fee` fee
LEFT JOIN `forwarder_quote_reference_value` ref_value
    ON ref_value.domain = 'pricing_model'
   AND ref_value.code = fee.pricing_model
   AND ref_value.active = b'1'
WHERE fee.pricing_model IS NOT NULL
  AND ref_value.id IS NULL
UNION ALL
SELECT
    fee.id AS fee_id,
    fee.service_code,
    fee.processing_fee_code AS fee_code,
    'unknown_billing_unit' AS issue_code,
    'warning' AS severity,
    CONCAT('Warehouse fee billing_unit should be normalized before automatic calculation: ', COALESCE(fee.billing_unit, 'NULL')) AS message
FROM `forwarder_warehouse_processing_fee` fee
LEFT JOIN `forwarder_quote_reference_value` ref_value
    ON ref_value.domain = 'billing_unit'
   AND ref_value.code = fee.billing_unit
   AND ref_value.active = b'1'
WHERE fee.amount IS NOT NULL
  AND fee.billing_unit IS NOT NULL
  AND ref_value.id IS NULL
UNION ALL
SELECT
    fee.id AS fee_id,
    fee.service_code,
    fee.processing_fee_code AS fee_code,
    'priced_fee_missing_amount' AS issue_code,
    'hard_error' AS severity,
    'Priced warehouse fee must have amount, currency and billing_unit.' AS message
FROM `forwarder_warehouse_processing_fee` fee
WHERE fee.pricing_model IN ('PER_CBM', 'PER_KG', 'PER_PIECE', 'PER_SHIPMENT', 'FIXED', 'PER_CBM_DAY')
  AND (fee.amount IS NULL OR fee.currency IS NULL OR fee.billing_unit IS NULL);

CREATE OR REPLACE VIEW `forwarder_quote_transport_fee_quality_issue_v` AS
SELECT
    fee.id AS fee_id,
    fee.service_code,
    fee.fee_rule_code AS fee_code,
    'unknown_pricing_model' AS issue_code,
    'hard_error' AS severity,
    CONCAT('Unknown pricing_model: ', COALESCE(fee.pricing_model, 'NULL')) AS message
FROM `forwarder_quote_transport_fee` fee
LEFT JOIN `forwarder_quote_reference_value` ref_value
    ON ref_value.domain = 'pricing_model'
   AND ref_value.code = fee.pricing_model
   AND ref_value.active = b'1'
WHERE fee.pricing_model IS NOT NULL
  AND ref_value.id IS NULL
UNION ALL
SELECT
    fee.id AS fee_id,
    fee.service_code,
    fee.fee_rule_code AS fee_code,
    'unknown_billing_unit' AS issue_code,
    'warning' AS severity,
    CONCAT('Transport fee billing_unit should be normalized before automatic calculation: ', COALESCE(fee.billing_unit, 'NULL')) AS message
FROM `forwarder_quote_transport_fee` fee
LEFT JOIN `forwarder_quote_reference_value` ref_value
    ON ref_value.domain = 'billing_unit'
   AND ref_value.code = fee.billing_unit
   AND ref_value.active = b'1'
WHERE COALESCE(fee.amount, fee.rate) IS NOT NULL
  AND fee.billing_unit IS NOT NULL
  AND ref_value.id IS NULL
UNION ALL
SELECT
    fee.id AS fee_id,
    fee.service_code,
    fee.fee_rule_code AS fee_code,
    'priced_fee_missing_amount' AS issue_code,
    'hard_error' AS severity,
    'Priced transport fee must have amount or rate, currency and billing_unit.' AS message
FROM `forwarder_quote_transport_fee` fee
WHERE fee.pricing_model IN ('PER_CBM', 'PER_KG', 'PER_PIECE', 'PER_SHIPMENT', 'FIXED', 'PERCENTAGE')
  AND (COALESCE(fee.amount, fee.rate) IS NULL OR fee.currency IS NULL OR fee.billing_unit IS NULL);
