-- File management commission tier standard alignment.
-- Makes official commission rules record amount tiers as structured fields.

SET NAMES utf8mb4;

UPDATE `file_mgmt_parse_id_sequence`
SET `next_id` = GREATEST(`next_id`, 88020),
    `gmt_updated` = NOW()
WHERE `sequence_name` = 'file_mgmt_parse_version_item';

UPDATE `file_mgmt_parse_standard_version`
SET `normalization_rule_json` = '{"currency":["AED","SAR"],"rate":"percent","tierRule":"split_by_amount_range","brandRestriction":"default_all_split_by_brand"}',
    `validation_rule_json` = '{"required":["country","categoryName","amountRangeLabel","amountCurrency","commissionRate"]}',
    `display_config_json` = '{"overviewColumns":["parentCategoryName","categoryName","brandRestriction","amountRangeLabel","amountCurrency","commissionRate","effectiveDate"]}',
    `diff_rule_json` = '{"naturalKey":["country","categoryPath","parentCategoryName","categoryName","brandRestriction","amountMin","amountMinInclusive","amountMax","amountMaxInclusive","amountCurrency","effectiveDate"]}',
    `gmt_updated` = NOW()
WHERE `id` = 2001
  AND `standard_id` = 1001
  AND `is_deleted` = b'0';

UPDATE `file_mgmt_parse_item_standard`
SET `natural_key_json` = '{"fields":["country","categoryPath","parentCategoryName","categoryName","brandRestriction","amountMin","amountMinInclusive","amountMax","amountMaxInclusive","amountCurrency","effectiveDate"]}',
    `field_schema_json` = '{"country":"string","platform":"string","fulfillmentType":"string","parentCategoryName":"string","categoryName":"string","categoryPath":"string","brandRestriction":"string","amountRangeLabel":"string","amountMin":"decimal","amountMinInclusive":"boolean","amountMax":"decimal","amountMaxInclusive":"boolean","amountCurrency":"string","commissionRate":"decimal","effectiveDate":"date"}',
    `display_config_json` = '{"columns":["parentCategoryName","categoryName","brandRestriction","amountRangeLabel","amountCurrency","commissionRate","effectiveDate"],"labels":{"country":"国家","platform":"平台","fulfillmentType":"履约方式","parentCategoryName":"一级类目","categoryName":"类目","categoryPath":"类目路径","brandRestriction":"品牌限制","amountRangeLabel":"计佣金额区间","amountCurrency":"币种","commissionRate":"佣金率","effectiveDate":"生效日期","amountMin":"金额下限","amountMinInclusive":"下限含边界","amountMax":"金额上限","amountMaxInclusive":"上限含边界"},"widths":{"parentCategoryName":150,"categoryName":220,"brandRestriction":150,"amountRangeLabel":150,"amountCurrency":90,"commissionRate":100,"effectiveDate":130}}',
    `validation_rule_json` = '{"required":["country","categoryName","amountRangeLabel","amountCurrency","commissionRate"]}',
    `diff_rule_json` = '{"compareFields":["amountRangeLabel","amountMin","amountMinInclusive","amountMax","amountMaxInclusive","amountCurrency","commissionRate","effectiveDate"]}',
    `gmt_updated` = NOW()
WHERE `id` = 3001
  AND `standard_version_id` = 2001
  AND `item_type` = 'commission_rule'
  AND `is_deleted` = b'0';

INSERT INTO `file_mgmt_parse_version_item` (
    `id`, `version_id`, `target_plan_id`, `item_type`, `natural_key`, `natural_key_hash`,
    `version_payload_json`, `source_result_item_id`, `data_scope_type`, `data_scope_key`,
    `sort_no`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (88011, 70001, 4001, 'commission_rule',
     'KSA + Fashion > Watches + 全部 + <= 5000 SAR + 2025-09-01',
     'seed-comm-ksa-fashion-watches-le-5000',
     '{"country":"KSA","platform":"Noon","fulfillmentType":"FBN","parentCategoryName":null,"categoryName":"Fashion > Watches","categoryPath":"Fashion > Watches","brandRestriction":"全部","amountRangeLabel":"<= 5000 SAR","amountMin":null,"amountMinInclusive":null,"amountMax":5000,"amountMaxInclusive":true,"amountCurrency":"SAR","commissionRate":"15%","effectiveDate":"2025-09-01"}',
     NULL, 'global', 'global:*', 10, b'0', 1, 1),
    (88012, 70001, 4001, 'commission_rule',
     'KSA + Fashion > Watches + 全部 + > 5000 SAR + 2025-09-01',
     'seed-comm-ksa-fashion-watches-gt-5000',
     '{"country":"KSA","platform":"Noon","fulfillmentType":"FBN","parentCategoryName":null,"categoryName":"Fashion > Watches","categoryPath":"Fashion > Watches","brandRestriction":"全部","amountRangeLabel":"> 5000 SAR","amountMin":5000,"amountMinInclusive":false,"amountMax":null,"amountMaxInclusive":null,"amountCurrency":"SAR","commissionRate":"5%","effectiveDate":"2025-09-01"}',
     NULL, 'global', 'global:*', 20, b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `natural_key` = VALUES(`natural_key`),
    `version_payload_json` = VALUES(`version_payload_json`),
    `sort_no` = VALUES(`sort_no`),
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

UPDATE `file_mgmt_parse_result_item`
SET `normalized_payload_json` = JSON_SET(
        `normalized_payload_json`,
        '$.brandRestriction', '全部'
    ),
    `effective_payload_json` = JSON_SET(
        COALESCE(NULLIF(`effective_payload_json`, ''), `normalized_payload_json`),
        '$.brandRestriction', '全部'
    ),
    `gmt_updated` = NOW()
WHERE `item_type` = 'commission_rule'
  AND `is_deleted` = b'0'
  AND JSON_VALID(`normalized_payload_json`)
  AND JSON_EXTRACT(`normalized_payload_json`, '$.brandRestriction') IS NULL;

UPDATE `file_mgmt_parse_version_item`
SET `version_payload_json` = JSON_SET(
        `version_payload_json`,
        '$.brandRestriction', '全部'
    ),
    `gmt_updated` = NOW()
WHERE `item_type` = 'commission_rule'
  AND `is_deleted` = b'0'
  AND JSON_VALID(`version_payload_json`)
  AND JSON_EXTRACT(`version_payload_json`, '$.brandRestriction') IS NULL;

UPDATE `file_mgmt_parse_result_item`
SET `normalized_payload_json` = JSON_SET(
        `normalized_payload_json`,
        '$.categoryPath', JSON_UNQUOTE(JSON_EXTRACT(`normalized_payload_json`, '$.categoryName'))
    ),
    `effective_payload_json` = JSON_SET(
        COALESCE(NULLIF(`effective_payload_json`, ''), `normalized_payload_json`),
        '$.categoryPath', JSON_UNQUOTE(JSON_EXTRACT(`normalized_payload_json`, '$.categoryName'))
    ),
    `gmt_updated` = NOW()
WHERE `item_type` = 'commission_rule'
  AND `is_deleted` = b'0'
  AND JSON_VALID(`normalized_payload_json`)
  AND JSON_EXTRACT(`normalized_payload_json`, '$.categoryName') IS NOT NULL
  AND JSON_EXTRACT(`normalized_payload_json`, '$.categoryPath') IS NULL;

UPDATE `file_mgmt_parse_version_item`
SET `version_payload_json` = JSON_SET(
        `version_payload_json`,
        '$.categoryPath', JSON_UNQUOTE(JSON_EXTRACT(`version_payload_json`, '$.categoryName'))
    ),
    `gmt_updated` = NOW()
WHERE `item_type` = 'commission_rule'
  AND `is_deleted` = b'0'
  AND JSON_VALID(`version_payload_json`)
  AND JSON_EXTRACT(`version_payload_json`, '$.categoryName') IS NOT NULL
  AND JSON_EXTRACT(`version_payload_json`, '$.categoryPath') IS NULL;

UPDATE `file_mgmt_parse_result_item`
SET `normalized_payload_json` = JSON_SET(
        `normalized_payload_json`,
        '$.amountRangeLabel', '全部',
        '$.amountCurrency', 'SAR'
    ),
    `effective_payload_json` = JSON_SET(
        COALESCE(NULLIF(`effective_payload_json`, ''), `normalized_payload_json`),
        '$.amountRangeLabel', '全部',
        '$.amountCurrency', 'SAR'
    ),
    `effective_validation_status` = 'pass',
    `gmt_updated` = NOW()
WHERE `item_type` = 'commission_rule'
  AND `is_deleted` = b'0'
  AND JSON_VALID(`normalized_payload_json`)
  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(`normalized_payload_json`, '$.country'))) IN ('KSA', 'SA')
  AND JSON_EXTRACT(`normalized_payload_json`, '$.amountMin') IS NULL
  AND JSON_EXTRACT(`normalized_payload_json`, '$.amountMax') IS NULL
  AND (
      JSON_EXTRACT(`normalized_payload_json`, '$.amountRangeLabel') IS NULL
      OR JSON_EXTRACT(`normalized_payload_json`, '$.amountCurrency') IS NULL
  );

UPDATE `file_mgmt_parse_result_item`
SET `normalized_payload_json` = JSON_SET(
        `normalized_payload_json`,
        '$.amountRangeLabel', '全部',
        '$.amountCurrency', 'AED'
    ),
    `effective_payload_json` = JSON_SET(
        COALESCE(NULLIF(`effective_payload_json`, ''), `normalized_payload_json`),
        '$.amountRangeLabel', '全部',
        '$.amountCurrency', 'AED'
    ),
    `effective_validation_status` = 'pass',
    `gmt_updated` = NOW()
WHERE `item_type` = 'commission_rule'
  AND `is_deleted` = b'0'
  AND JSON_VALID(`normalized_payload_json`)
  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(`normalized_payload_json`, '$.country'))) IN ('UAE', 'AE')
  AND JSON_EXTRACT(`normalized_payload_json`, '$.amountMin') IS NULL
  AND JSON_EXTRACT(`normalized_payload_json`, '$.amountMax') IS NULL
  AND (
      JSON_EXTRACT(`normalized_payload_json`, '$.amountRangeLabel') IS NULL
      OR JSON_EXTRACT(`normalized_payload_json`, '$.amountCurrency') IS NULL
  );

UPDATE `file_mgmt_parse_version_item`
SET `version_payload_json` = JSON_SET(
        `version_payload_json`,
        '$.amountRangeLabel', '全部',
        '$.amountCurrency', 'SAR'
    ),
    `gmt_updated` = NOW()
WHERE `item_type` = 'commission_rule'
  AND `is_deleted` = b'0'
  AND JSON_VALID(`version_payload_json`)
  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(`version_payload_json`, '$.country'))) IN ('KSA', 'SA')
  AND JSON_EXTRACT(`version_payload_json`, '$.amountMin') IS NULL
  AND JSON_EXTRACT(`version_payload_json`, '$.amountMax') IS NULL
  AND (
      JSON_EXTRACT(`version_payload_json`, '$.amountRangeLabel') IS NULL
      OR JSON_EXTRACT(`version_payload_json`, '$.amountCurrency') IS NULL
  );

UPDATE `file_mgmt_parse_version_item`
SET `version_payload_json` = JSON_SET(
        `version_payload_json`,
        '$.amountRangeLabel', '全部',
        '$.amountCurrency', 'AED'
    ),
    `gmt_updated` = NOW()
WHERE `item_type` = 'commission_rule'
  AND `is_deleted` = b'0'
  AND JSON_VALID(`version_payload_json`)
  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(`version_payload_json`, '$.country'))) IN ('UAE', 'AE')
  AND JSON_EXTRACT(`version_payload_json`, '$.amountMin') IS NULL
  AND JSON_EXTRACT(`version_payload_json`, '$.amountMax') IS NULL
  AND (
      JSON_EXTRACT(`version_payload_json`, '$.amountRangeLabel') IS NULL
      OR JSON_EXTRACT(`version_payload_json`, '$.amountCurrency') IS NULL
  );
