-- Add lifecycle classifier formula parameters used by lifecycle evidence and
-- downstream sales forecast calculations.

SET NAMES utf8mb4;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '成长期',
    'itemName', '爆发惯性系数',
    'cadence', '随时',
    'valueType', '数值',
    'defaultValue', '1.5',
    'resultShape', NULL,
    'note', '用于成长期爆发式形态证据和后续销量预测'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '爆发惯性系数', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '成长期',
    'itemName', '稳健系数',
    'cadence', '随时',
    'valueType', '数值',
    'defaultValue', '1.05',
    'resultShape', NULL,
    'note', '用于成长期匀速式形态证据和后续销量预测'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '稳健系数', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '成长期',
    'itemName', '阶梯增长倍数',
    'cadence', '随时',
    'valueType', '数值',
    'defaultValue', '2',
    'resultShape', NULL,
    'note', '最近7天日均超过之前30天日均的倍数阈值'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '阶梯增长倍数', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '成长期',
    'itemName', '波动去极值比例',
    'cadence', '随时',
    'valueType', '数值',
    'defaultValue', '0.1',
    'resultShape', NULL,
    'note', '波动型增长去除最高和最低销量点比例'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '波动去极值比例', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '成长期',
    'itemName', '波动增长动量阈值',
    'cadence', '随时',
    'valueType', '数值',
    'defaultValue', '0.1',
    'resultShape', NULL,
    'note', '波动型增长后半段相对前半段的最小动量'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '波动增长动量阈值', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '衰退期',
    'itemName', '衰退比例阈值',
    'cadence', '随时',
    'valueType', '数值',
    'defaultValue', '0.8',
    'resultShape', NULL,
    'note', '最近7天日均相对T-38至T-8历史日均的最大比例'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '衰退比例阈值', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '稳定期',
    'itemName', '成熟期上升短期权重',
    'cadence', '随时',
    'valueType', '数值',
    'defaultValue', '0.7',
    'resultShape', NULL,
    'note', '短期日均高于长期日均时的短期权重'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '成熟期上升短期权重', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `content_json` = JSON_ARRAY_APPEND(
  `content_json`,
  '$',
  JSON_OBJECT(
    'groupName', '稳定期',
    'itemName', '成熟期下滑短期权重',
    'cadence', '随时',
    'valueType', '数值',
    'defaultValue', '0.6',
    'resultShape', NULL,
    'note', '短期日均低于长期日均时的短期权重'
  )
)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`)
  AND JSON_SEARCH(`content_json`, 'one', '成熟期下滑短期权重', NULL, '$[*].itemName') IS NULL;

UPDATE `operation_config_typed_version`
SET `item_count` = JSON_LENGTH(`content_json`)
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`);

UPDATE `operation_config_typed_version`
SET `summary` = CONCAT(JSON_LENGTH(`content_json`), ' 条 DEFAULT_V1 配置')
WHERE `config_type` = 'PRODUCT_LIFECYCLE'
  AND `version_no` = 'DEFAULT_LIFECYCLE_CONFIG'
  AND `content_json` IS NOT NULL
  AND JSON_VALID(`content_json`);
