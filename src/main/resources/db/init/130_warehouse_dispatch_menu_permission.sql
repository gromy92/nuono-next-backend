-- Add warehouse dispatch workbench menu permission.

SET NAMES utf8mb4;

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (9250, '仓库', 0, '/warehouse', b'0', NOW(), NOW()),
  (9251, '仓库发运', 9250, '/warehouse/dispatch', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

SET @warehouse_menu_id = 9250;
SET @warehouse_dispatch_menu_id = 9251;
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
  SELECT 2 AS `role_id`, @warehouse_menu_id AS `menu_id`
  UNION ALL SELECT 2, @warehouse_dispatch_menu_id
  UNION ALL SELECT 3, @warehouse_menu_id
  UNION ALL SELECT 3, @warehouse_dispatch_menu_id
  UNION ALL SELECT 5, @warehouse_menu_id
  UNION ALL SELECT 5, @warehouse_dispatch_menu_id
  UNION ALL SELECT 6, @warehouse_menu_id
  UNION ALL SELECT 6, @warehouse_dispatch_menu_id
) grants
JOIN `role` r ON r.`id` = grants.`role_id` AND r.`is_deleted` = b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM `role_menu` existing
  WHERE existing.`role_id` = grants.`role_id`
    AND existing.`menu_id` = grants.`menu_id`
);

UPDATE `role_menu` rm
JOIN (
  SELECT
    existing.`role_id`,
    existing.`menu_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = b'0' THEN existing.`id` END), MIN(existing.`id`)) AS `keep_id`
  FROM `role_menu` existing
  WHERE existing.`menu_id` IN (@warehouse_menu_id, @warehouse_dispatch_menu_id)
  GROUP BY existing.`role_id`, existing.`menu_id`
) keep_row
  ON keep_row.`role_id` = rm.`role_id`
  AND keep_row.`menu_id` = rm.`menu_id`
  AND keep_row.`keep_id` = rm.`id`
SET rm.`is_deleted` = b'0',
    rm.`gmt_updated` = NOW();

UPDATE `role_menu` duplicate
JOIN `role_menu` keep_row
  ON keep_row.`role_id` = duplicate.`role_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = b'0'
  AND duplicate.`is_deleted` = b'0'
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = b'1',
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` IN (@warehouse_menu_id, @warehouse_dispatch_menu_id);

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
  NOW(),
  COALESCE(u.`expired_time`, '2099-12-31 23:59:59'),
  b'0',
  COALESCE(u.`updated_by`, u.`created_by`, 1),
  COALESCE(u.`updated_by`, u.`created_by`, 1),
  NOW(),
  NOW()
FROM `user` u
JOIN `role_menu` rm ON rm.`role_id` = u.`role_id`
  AND rm.`menu_id` IN (@warehouse_menu_id, @warehouse_dispatch_menu_id)
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = b'0'
  AND u.`status` = 1
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = rm.`menu_id`
  );

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
JOIN `role_menu` rm ON rm.`role_id` = u.`role_id`
  AND rm.`menu_id` = um.`menu_id`
  AND rm.`is_deleted` = b'0'
SET um.`status` = 1,
    um.`is_deleted` = b'0',
    um.`gmt_updated` = NOW()
WHERE um.`menu_id` IN (@warehouse_menu_id, @warehouse_dispatch_menu_id)
  AND u.`is_deleted` = b'0'
  AND u.`status` = 1;

UPDATE `user_menu` duplicate
JOIN `user_menu` keep_row
  ON keep_row.`user_id` = duplicate.`user_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = b'0'
  AND duplicate.`is_deleted` = b'0'
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = b'1',
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` IN (@warehouse_menu_id, @warehouse_dispatch_menu_id);
