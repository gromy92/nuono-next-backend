-- Add explicit lifecycle stage duration parameters used by the product lifecycle
-- 90-day forecast timeline. Threshold-only lifecycle configs cannot safely
-- project future stage changes.

SET NAMES utf8mb4;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '成长期',
    'itemName', '成长期最长周期',
    'cadence', NULL,
    'valueType', '数值',
    'defaultValue', '45',
    'resultShape', NULL,
    'note', '用于未来生命周期时间线推演'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '成长期最长周期', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '稳定期',
    'itemName', '稳定期最长周期',
    'cadence', NULL,
    'valueType', '数值',
    'defaultValue', '180',
    'resultShape', NULL,
    'note', '用于未来生命周期时间线推演'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '稳定期最长周期', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '衰退期',
    'itemName', '衰退期最长周期',
    'cadence', NULL,
    'valueType', '数值',
    'defaultValue', '30',
    'resultShape', NULL,
    'note', '用于未来生命周期时间线推演'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '衰退期最长周期', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET
  `item_count` = JSON_LENGTH(`content_json`),
  `summary` = CASE
    WHEN `version_no` = 'DEFAULT_LIFECYCLE_CONFIG'
      THEN CONCAT(JSON_LENGTH(`content_json`), ' 条 DEFAULT_V1 配置')
    ELSE CONCAT(JSON_LENGTH(`content_json`), ' 条生命周期配置')
  END
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`);
