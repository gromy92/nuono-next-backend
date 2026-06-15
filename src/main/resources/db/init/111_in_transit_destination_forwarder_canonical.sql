SET NAMES utf8mb4;

INSERT INTO `in_transit_forwarder` (
    `id`, `owner_user_id`, `forwarder_code`, `forwarder_name`, `status`, `is_deleted`,
    `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT seed.`owner_user_id` * 100000 + item.`offset_id`,
       seed.`owner_user_id`,
       item.`forwarder_code`,
       item.`forwarder_name`,
       'ACTIVE',
       b'0',
       1,
       1,
       NOW(),
       NOW()
FROM (
    SELECT 307 AS `owner_user_id`
    UNION
    SELECT DISTINCT `owner_user_id` FROM `in_transit_batch` WHERE `owner_user_id` IS NOT NULL
) seed
JOIN (
    SELECT 1 AS `offset_id`, 'QIKE' AS `forwarder_code`, '启客' AS `forwarder_name`
    UNION ALL SELECT 2, 'YITONG', '易通'
    UNION ALL SELECT 3, 'YITE', '义特'
) item
ON 1 = 1
ON DUPLICATE KEY UPDATE
    `forwarder_name` = VALUES(`forwarder_name`),
    `status` = 'ACTIVE',
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `in_transit_forwarder_alias` (
    `id`, `owner_user_id`, `standard_forwarder_id`, `raw_forwarder_name`, `normalized_raw_forwarder_name`,
    `status`, `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT seed.`owner_user_id` * 100000 + alias_item.`offset_id`,
       seed.`owner_user_id`,
       forwarder.`id`,
       alias_item.`raw_forwarder_name`,
       alias_item.`normalized_raw_forwarder_name`,
       'ACTIVE',
       b'0',
       1,
       1,
       NOW(),
       NOW()
FROM (
    SELECT 307 AS `owner_user_id`
    UNION
    SELECT DISTINCT `owner_user_id` FROM `in_transit_batch` WHERE `owner_user_id` IS NOT NULL
) seed
JOIN (
    SELECT 101 AS `offset_id`, 'QIKE' AS `forwarder_code`, '启客' AS `raw_forwarder_name`, '启客' AS `normalized_raw_forwarder_name`
    UNION ALL SELECT 102, 'QIKE', '启客物流', '启客物流'
    UNION ALL SELECT 103, 'QIKE', 'qike', 'qike'
    UNION ALL SELECT 111, 'YITONG', '易通', '易通'
    UNION ALL SELECT 112, 'YITONG', '易通物流', '易通物流'
    UNION ALL SELECT 113, 'YITONG', 'ET', 'et'
    UNION ALL SELECT 114, 'YITONG', 'YITONG', 'yitong'
    UNION ALL SELECT 121, 'YITE', '义特', '义特'
    UNION ALL SELECT 122, 'YITE', '义特物流', '义特物流'
    UNION ALL SELECT 123, 'YITE', '义特国际物流', '义特国际物流'
    UNION ALL SELECT 124, 'YITE', 'YITE', 'yite'
) alias_item
  ON 1 = 1
JOIN `in_transit_forwarder` forwarder
  ON forwarder.`owner_user_id` = seed.`owner_user_id`
 AND forwarder.`forwarder_code` = alias_item.`forwarder_code`
 AND forwarder.`is_deleted` = b'0'
ON DUPLICATE KEY UPDATE
    `standard_forwarder_id` = VALUES(`standard_forwarder_id`),
    `raw_forwarder_name` = VALUES(`raw_forwarder_name`),
    `status` = 'ACTIVE',
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

UPDATE `in_transit_batch` batch
JOIN `in_transit_forwarder` forwarder
  ON forwarder.`owner_user_id` = batch.`owner_user_id`
 AND forwarder.`forwarder_code` = 'QIKE'
 AND forwarder.`is_deleted` = b'0'
SET batch.`standard_forwarder_id` = forwarder.`id`,
    batch.`raw_forwarder_name` = '启客',
    batch.`normalized_raw_forwarder_name` = '启客',
    batch.`forwarder_quality_status` = 'forwarder_matched'
WHERE batch.`is_deleted` = b'0'
  AND (
      batch.`raw_forwarder_name` LIKE '%启客%'
      OR batch.`normalized_raw_forwarder_name` LIKE '%qike%'
      OR batch.`standard_forwarder_id` = forwarder.`id`
  );

UPDATE `in_transit_batch` batch
JOIN `in_transit_forwarder` forwarder
  ON forwarder.`owner_user_id` = batch.`owner_user_id`
 AND forwarder.`forwarder_code` = 'YITONG'
 AND forwarder.`is_deleted` = b'0'
SET batch.`standard_forwarder_id` = forwarder.`id`,
    batch.`raw_forwarder_name` = '易通',
    batch.`normalized_raw_forwarder_name` = '易通',
    batch.`forwarder_quality_status` = 'forwarder_matched'
WHERE batch.`is_deleted` = b'0'
  AND (
      batch.`raw_forwarder_name` LIKE '%易通%'
      OR batch.`normalized_raw_forwarder_name` LIKE '%yitong%'
      OR batch.`normalized_raw_forwarder_name` = 'et'
      OR batch.`standard_forwarder_id` = forwarder.`id`
  );

UPDATE `in_transit_batch` batch
JOIN `in_transit_forwarder` forwarder
  ON forwarder.`owner_user_id` = batch.`owner_user_id`
 AND forwarder.`forwarder_code` = 'YITE'
 AND forwarder.`is_deleted` = b'0'
SET batch.`standard_forwarder_id` = forwarder.`id`,
    batch.`raw_forwarder_name` = '义特',
    batch.`normalized_raw_forwarder_name` = '义特',
    batch.`forwarder_quality_status` = 'forwarder_matched'
WHERE batch.`is_deleted` = b'0'
  AND (
      batch.`raw_forwarder_name` LIKE '%义特%'
      OR batch.`normalized_raw_forwarder_name` LIKE '%yite%'
      OR batch.`standard_forwarder_id` = forwarder.`id`
  );

UPDATE `in_transit_batch`
SET `target_store_code` = 'DB'
WHERE `is_deleted` = b'0'
  AND (
      `target_store_code` IN ('DB', 'DXB', '迪拜', 'STR245027-NAE', 'STORE-JED01')
      OR `target_site_code` = 'AE'
      OR `target_warehouse_name` LIKE '%DXB%'
      OR `target_warehouse_name` LIKE '%迪拜%'
      OR `batch_reference_no` LIKE '%UAE%'
  );

UPDATE `in_transit_batch`
SET `target_store_code` = 'RUH'
WHERE `is_deleted` = b'0'
  AND (
      `target_store_code` IN ('RUH', '利雅得', 'STR245027-NSA', 'STORE-RUH01S')
      OR `target_site_code` = 'SA'
      OR `target_warehouse_name` LIKE '%RUH%'
      OR `target_warehouse_name` LIKE '%利雅得%'
      OR `batch_reference_no` LIKE '%KSA%'
  );

UPDATE `in_transit_batch`
SET `target_site_code` = NULL
WHERE `is_deleted` = b'0'
  AND `target_store_code` IN ('RUH', 'DB');
