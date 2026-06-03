-- Restore canman SA product-management site scope when the legacy store
-- authorization exists but logical_store_site was not materialized.

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'logical_store_site', GREATEST(COALESCE(MAX(`id`) + 1, 51000), 51000), NOW(), NOW()
FROM `logical_store_site`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

SET @logical_store_site_next_id := (
  SELECT GREATEST(
    COALESCE((SELECT `next_id` FROM `product_management_id_sequence` WHERE `sequence_name` = 'logical_store_site'), 51000),
    COALESCE((SELECT MAX(`id`) + 1 FROM `logical_store_site`), 51000)
  )
);

INSERT INTO `logical_store_site` (
  `id`,
  `logical_store_id`,
  `store_code`,
  `site`,
  `is_reference_site`,
  `is_mounted`,
  `site_status`,
  `is_deleted`,
  `created_by`,
  `updated_by`,
  `gmt_create`,
  `gmt_updated`
)
SELECT
  @logical_store_site_next_id + ROW_NUMBER() OVER (ORDER BY ls.`id`, us.`store_code`) - 1 AS `id`,
  ls.`id` AS `logical_store_id`,
  us.`store_code`,
  us.`site`,
  CASE
    WHEN EXISTS (
      SELECT 1
      FROM `logical_store_site` existing
      WHERE existing.`logical_store_id` = ls.`id`
        AND existing.`is_deleted` = b'0'
    ) THEN b'0'
    ELSE b'1'
  END AS `is_reference_site`,
  b'1' AS `is_mounted`,
  'active' AS `site_status`,
  b'0' AS `is_deleted`,
  COALESCE(us.`created_by`, ls.`owner_user_id`) AS `created_by`,
  COALESCE(us.`updated_by`, ls.`owner_user_id`) AS `updated_by`,
  NOW() AS `gmt_create`,
  NOW() AS `gmt_updated`
FROM `user_store` us
JOIN `logical_store` ls
  ON ls.`owner_user_id` = us.`user_id`
 AND BINARY ls.`project_code` = BINARY us.`project_code`
 AND ls.`is_deleted` = b'0'
WHERE us.`user_id` = 307
  AND BINARY us.`project_code` = BINARY 'PRJ108065'
  AND BINARY us.`store_code` = BINARY 'STR108065-NSA'
  AND BINARY us.`site` = BINARY 'SA'
  AND us.`is_authorized` = b'1'
  AND us.`is_deleted` = b'0'
ON DUPLICATE KEY UPDATE
  `logical_store_id` = VALUES(`logical_store_id`),
  `store_code` = VALUES(`store_code`),
  `site` = VALUES(`site`),
  `is_mounted` = b'1',
  `site_status` = VALUES(`site_status`),
  `is_deleted` = b'0',
  `updated_by` = VALUES(`updated_by`),
  `gmt_updated` = NOW();

UPDATE `product_management_id_sequence`
SET
  `next_id` = GREATEST(
    `next_id`,
    COALESCE((SELECT MAX(`id`) + 1 FROM `logical_store_site`), 51000)
  ),
  `gmt_updated` = NOW()
WHERE `sequence_name` = 'logical_store_site';
