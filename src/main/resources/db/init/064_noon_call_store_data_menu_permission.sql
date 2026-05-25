-- Add the Noon call store-data entry to the real permission menu.
-- The API still uses the system reports capability boundary, but the user-facing
-- navigation lives under Noon调用 > 店铺数据.

SET NAMES utf8mb4;

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (9700, 'Noon调用', 0, '/noon-call', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (9701, '店铺数据', 9700, '/noon-call/store-data', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

SET @noon_call_menu_id = 9700;
SET @noon_call_store_data_menu_id = 9701;
SET @next_role_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `role_menu`);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`, `gmt_create`, `gmt_updated`)
SELECT
  @next_role_menu_id := @next_role_menu_id + 1,
  grants.`role_id`,
  grants.`menu_id`,
  b'0',
  NOW(),
  NOW()
FROM (
  SELECT 1 AS `role_id`, @noon_call_menu_id AS `menu_id`
  UNION ALL SELECT 1, @noon_call_store_data_menu_id
  UNION ALL SELECT 2, @noon_call_menu_id
  UNION ALL SELECT 2, @noon_call_store_data_menu_id
  UNION ALL SELECT 3, @noon_call_menu_id
  UNION ALL SELECT 3, @noon_call_store_data_menu_id
  UNION ALL SELECT 4, @noon_call_menu_id
  UNION ALL SELECT 4, @noon_call_store_data_menu_id
) grants
JOIN `role` r ON r.`id` = grants.`role_id` AND r.`is_deleted` = b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM `role_menu` existing
  WHERE existing.`role_id` = grants.`role_id`
    AND existing.`menu_id` = grants.`menu_id`
    AND existing.`is_deleted` = b'0'
);

UPDATE `role_menu` rm
JOIN (
  SELECT
    existing.`role_id`,
    existing.`menu_id`,
    MIN(existing.`id`) AS `keep_id`
  FROM `role_menu` existing
  WHERE existing.`menu_id` IN (@noon_call_menu_id, @noon_call_store_data_menu_id)
    AND existing.`is_deleted` = b'0'
  GROUP BY existing.`role_id`, existing.`menu_id`
) keep_row
  ON keep_row.`role_id` = rm.`role_id`
  AND keep_row.`menu_id` = rm.`menu_id`
  AND keep_row.`keep_id` <> rm.`id`
SET rm.`is_deleted` = b'1',
    rm.`gmt_updated` = NOW()
WHERE rm.`is_deleted` = b'0';

SET @next_user_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `user_menu`);

INSERT INTO `user_menu` (
  `id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  @next_user_menu_id := @next_user_menu_id + 1,
  u.`id`,
  rm.`menu_id`,
  1,
  NULL,
  NULL,
  0,
  u.`id`,
  u.`id`,
  NOW(),
  NOW()
FROM `user` u
JOIN `role_menu` rm ON rm.`role_id` = u.`role_id`
  AND rm.`menu_id` IN (@noon_call_menu_id, @noon_call_store_data_menu_id)
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = 0
  AND u.`status` = 1
  AND COALESCE(u.`account_type`, '') = 'internal'
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = rm.`menu_id`
      AND existing.`is_deleted` = 0
  );

UPDATE `user_menu` duplicate
JOIN `user_menu` keep_row
  ON keep_row.`user_id` = duplicate.`user_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = 0
  AND duplicate.`is_deleted` = 0
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = 1,
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` IN (@noon_call_menu_id, @noon_call_store_data_menu_id);
