-- Upgrade file-management logistics output from one legacy channel row to a structured quote package.
-- This script keeps `logistics_channel_rule` readable while adding the item types required for ET/Yite coverage.

INSERT INTO `file_mgmt_parse_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('file_mgmt_parse_item_standard', 3020, NOW(), NOW()),
    ('file_mgmt_parse_target_plan', 4010, NOW(), NOW()),
    ('file_mgmt_parse_target_plan_scope', 5070, NOW(), NOW()),
    ('file_mgmt_parse_active_version', 72000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

UPDATE `file_mgmt_parse_standard`
SET
    `document_name` = '结构化物流报价方案',
    `description` = 'Yite/ET/FBN 运输与海外仓结构化报价包标准',
    `gmt_updated` = NOW()
WHERE `id` = 1003
  AND `is_deleted` = b'0';

UPDATE `file_mgmt_parse_standard_version`
SET
    `result_schema_json` = '{"items":["logistics_channel_rule","logistics_service_line","logistics_cargo_category","logistics_base_price","logistics_surcharge","logistics_billing_rule","logistics_warehouse_service_fee","logistics_restriction"]}',
    `normalization_rule_json` = '{"forwarder":["yite","et"],"countryAliases":{"UAE":"AE","KSA":"SA"},"transportModes":["air","sea","express","cargo_air","warehouse"],"currency":["CNY","RMB","AED","SAR","USD"],"units":["KG","CBM","piece","shipment"]}',
    `validation_rule_json` = '{"requiredItemTypes":["logistics_service_line","logistics_base_price"],"hardIssueSeverities":["error","blocker"],"manualConfirmField":"manualConfirmRequired"}',
    `display_config_json` = '{"groupBy":"itemType","overviewColumns":["forwarderName","country","transportMode","serviceScope","categoryName","unitPrice","currency","billingUnit","serviceName","restrictionType","severity"]}',
    `diff_rule_json` = '{"compareByItemType":true,"numericFields":["unitPrice","amount","rate","minimumBillableUnit","volumeDivisor"],"enumFields":["country","transportMode","serviceScope","currency","billingUnit","priceStatus","severity"]}',
    `gmt_updated` = NOW()
WHERE `id` = 2003
  AND `is_deleted` = b'0';

UPDATE `file_mgmt_parse_item_standard`
SET
    `item_label` = '物流渠道规则',
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW()
WHERE `standard_version_id` = 2003
  AND `item_type` = 'logistics_channel_rule';

INSERT INTO `file_mgmt_parse_item_standard` (
    `id`, `standard_version_id`, `item_type`, `item_label`, `natural_key_json`, `field_schema_json`,
    `display_config_json`, `validation_rule_json`, `diff_rule_json`, `sort_no`, `status`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (3010, 2003, 'logistics_service_line', '物流服务线路',
     '{"fields":["forwarderCode","country","fulfillmentMode","transportMode","serviceScope","destinationNode"]}',
     '{"forwarderCode":"string","forwarderName":"string","country":"string","fulfillmentMode":"string","destinationNode":"string","transportMode":"string","serviceScope":"string","originWarehouse":"string","destinationWarehouse":"string","departureFrequency":"string","leadTimeText":"string","leadTimeMinDays":"integer","leadTimeMaxDays":"integer","effectiveDate":"date","sourceVersion":"string"}',
     '{"columns":["forwarderName","country","destinationNode","transportMode","serviceScope","originWarehouse","destinationWarehouse","departureFrequency","leadTimeText","effectiveDate"]}',
     '{"required":["forwarderCode","country","transportMode","serviceScope","destinationNode"]}',
     '{"compareFields":["forwarderName","country","destinationNode","transportMode","serviceScope","originWarehouse","destinationWarehouse","departureFrequency","leadTimeText","leadTimeMinDays","leadTimeMaxDays","effectiveDate","sourceVersion"]}',
     31, 'active', b'0', 1, 1),
    (3011, 2003, 'logistics_cargo_category', '物流货物分类',
     '{"fields":["forwarderCode","serviceLineKey","categoryCode","categoryName"]}',
     '{"forwarderCode":"string","serviceLineKey":"string","categoryCode":"string","categoryName":"string","productExamples":"string","keywords":"string","electricType":"string","sensitiveTags":"string","packingPolicy":"string","manualConfirmRequired":"boolean"}',
     '{"columns":["serviceLineKey","categoryCode","categoryName","productExamples","electricType","sensitiveTags","packingPolicy","manualConfirmRequired"]}',
     '{"required":["forwarderCode","categoryName"]}',
     '{"compareFields":["categoryName","productExamples","keywords","electricType","sensitiveTags","packingPolicy","manualConfirmRequired"]}',
     32, 'active', b'0', 1, 1),
    (3012, 2003, 'logistics_base_price', '物流基础价格',
     '{"fields":["forwarderCode","serviceLineKey","cargoCategoryKey","pricingModel","billingUnit","priceStatus"]}',
     '{"forwarderCode":"string","serviceLineKey":"string","cargoCategoryKey":"string","unitPrice":"decimal","currency":"string","billingUnit":"string","pricingModel":"string","minimumBillableUnit":"decimal","minimumBillableUnitType":"string","volumeDivisor":"integer","seaWeightRatio":"string","roundingRule":"string","priceStatus":"string","effectiveDate":"date"}',
     '{"columns":["serviceLineKey","cargoCategoryKey","unitPrice","currency","billingUnit","pricingModel","minimumBillableUnit","minimumBillableUnitType","volumeDivisor","seaWeightRatio","roundingRule","priceStatus","effectiveDate"]}',
     '{"required":["forwarderCode","serviceLineKey","pricingModel","billingUnit","currency"]}',
     '{"compareFields":["unitPrice","currency","billingUnit","pricingModel","minimumBillableUnit","minimumBillableUnitType","volumeDivisor","seaWeightRatio","roundingRule","priceStatus","effectiveDate"]}',
     33, 'active', b'0', 1, 1),
    (3013, 2003, 'logistics_surcharge', '物流附加费',
     '{"fields":["forwarderCode","serviceLineKey","surchargeName","triggerCondition"]}',
     '{"forwarderCode":"string","serviceLineKey":"string","surchargeName":"string","surchargeType":"string","triggerCondition":"string","amount":"decimal","rate":"decimal","currency":"string","billingUnit":"string","includedInBasePrice":"boolean"}',
     '{"columns":["serviceLineKey","surchargeName","surchargeType","triggerCondition","amount","rate","currency","billingUnit","includedInBasePrice"]}',
     '{"required":["forwarderCode","surchargeName","triggerCondition"]}',
     '{"compareFields":["surchargeType","triggerCondition","amount","rate","currency","billingUnit","includedInBasePrice"]}',
     34, 'active', b'0', 1, 1),
    (3014, 2003, 'logistics_billing_rule', '物流计费规则',
     '{"fields":["forwarderCode","serviceLineKey","ruleName","conditionText","actionText"]}',
     '{"forwarderCode":"string","serviceLineKey":"string","ruleName":"string","conditionText":"string","actionText":"string","operator":"string","thresholdValue":"decimal","thresholdUnit":"string","severity":"string"}',
     '{"columns":["serviceLineKey","ruleName","conditionText","operator","thresholdValue","thresholdUnit","actionText","severity"]}',
     '{"required":["forwarderCode","ruleName","conditionText","actionText"]}',
     '{"compareFields":["conditionText","actionText","operator","thresholdValue","thresholdUnit","severity"]}',
     35, 'active', b'0', 1, 1),
    (3015, 2003, 'logistics_warehouse_service_fee', '海外仓服务费',
     '{"fields":["forwarderCode","warehouseNode","serviceName","feeType"]}',
     '{"forwarderCode":"string","country":"string","warehouseNode":"string","serviceName":"string","serviceType":"string","processingScope":"string","feeType":"string","amount":"decimal","rate":"decimal","currency":"string","billingUnit":"string","conditionText":"string","freeCondition":"string"}',
     '{"columns":["country","warehouseNode","serviceName","serviceType","processingScope","feeType","amount","rate","currency","billingUnit","conditionText","freeCondition"]}',
     '{"required":["forwarderCode","warehouseNode","serviceName","feeType"]}',
     '{"compareFields":["serviceType","processingScope","feeType","amount","rate","currency","billingUnit","conditionText","freeCondition"]}',
     36, 'active', b'0', 1, 1),
    (3016, 2003, 'logistics_restriction', '物流禁限运与合规',
     '{"fields":["forwarderCode","serviceLineKey","restrictionType","itemText","requirementText"]}',
     '{"forwarderCode":"string","serviceLineKey":"string","restrictionType":"string","itemText":"string","requirementText":"string","applicabilityScope":"string","severity":"string","manualConfirmRequired":"boolean"}',
     '{"columns":["serviceLineKey","restrictionType","itemText","requirementText","applicabilityScope","severity","manualConfirmRequired"]}',
     '{"required":["forwarderCode","restrictionType","itemText"]}',
     '{"compareFields":["requirementText","applicabilityScope","severity","manualConfirmRequired"]}',
     37, 'active', b'0', 1, 1)
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

UPDATE `file_mgmt_parse_target_plan`
SET
    `plan_label` = '物流-义特',
    `standard_id` = 1003,
    `standard_version_id` = 2003,
    `document_type` = 'logistics_rule',
    `document_name` = '结构化物流报价方案',
    `business_scope_code` = 'yite_ae_sa_fbn_logistics',
    `business_scope_label` = '义特物流，阿联酋/沙特，FBN 运输与海外仓',
    `publish_adapter` = 'logistics_rule',
    `description` = '义特物流结构化报价包输出，forwarderCode=yite',
    `gmt_updated` = NOW()
WHERE `plan_code` = 'logistics_yite'
  AND `is_deleted` = b'0';

INSERT INTO `file_mgmt_parse_target_plan` (
    `id`, `plan_code`, `plan_label`, `standard_id`, `standard_version_id`,
    `document_type`, `document_name`, `business_scope_code`, `business_scope_label`,
    `publish_adapter`, `description`, `sort_no`, `status`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (4006, 'logistics_et', '物流-易通', 1003, 2003, 'logistics_rule', '结构化物流报价方案',
     'et_ae_sa_fbn_logistics', '易通物流，阿联酋/沙特，FBN 运输与海外仓', 'logistics_rule',
     'ET/易通结构化报价包输出，forwarderCode=et', 60, 'active', b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `plan_label` = VALUES(`plan_label`),
    `standard_id` = VALUES(`standard_id`),
    `standard_version_id` = VALUES(`standard_version_id`),
    `document_type` = VALUES(`document_type`),
    `document_name` = VALUES(`document_name`),
    `business_scope_code` = VALUES(`business_scope_code`),
    `business_scope_label` = VALUES(`business_scope_label`),
    `publish_adapter` = VALUES(`publish_adapter`),
    `description` = VALUES(`description`),
    `sort_no` = VALUES(`sort_no`),
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_target_plan_scope` (
    `id`, `target_plan_id`, `scope_type`, `scope_value`, `scope_label`,
    `status`, `is_deleted`, `created_by`, `updated_by`
)
SELECT
    5060 + role_scope.`role_level`,
    4006,
    'role_level',
    CAST(role_scope.`role_level` AS CHAR),
    role_scope.`role_label`,
    'active',
    b'0',
    1,
    1
FROM (
    SELECT 0 AS `role_level`, '系统管理员' AS `role_label`
    UNION ALL SELECT 1, '老板'
    UNION ALL SELECT 2, '运营主管'
    UNION ALL SELECT 3, '运营'
) role_scope
ON DUPLICATE KEY UPDATE
    `scope_label` = VALUES(`scope_label`),
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW();
