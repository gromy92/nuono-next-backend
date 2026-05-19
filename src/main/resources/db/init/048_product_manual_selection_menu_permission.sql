-- Add the manual product-selection entry to the real permission menu.
-- This keeps source collection under 商品 > 人工选品 and out of system-admin-only menus.

SET NAMES utf8mb4;

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES (9102, '人工选品', 3, '/product/manual-selection', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

SET @manual_selection_menu_id = (
  SELECT id
  FROM `menu`
  WHERE `url_path` = '/product/manual-selection'
    AND `is_deleted` = b'0'
  ORDER BY id ASC
  LIMIT 1
);

SET @next_role_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `role_menu`);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`, `gmt_create`, `gmt_updated`)
SELECT
  @next_role_menu_id := @next_role_menu_id + 1,
  r.`id`,
  @manual_selection_menu_id,
  b'0',
  NOW(),
  NOW()
FROM `role` r
WHERE r.`id` IN (2, 3, 4)
  AND r.`is_deleted` = b'0'
  AND @manual_selection_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `role_menu` existing
    WHERE existing.`role_id` = r.`id`
      AND existing.`menu_id` = @manual_selection_menu_id
  );

UPDATE `role_menu` rm
JOIN (
  SELECT
    existing.`role_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = b'0' THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `role_menu` existing
  WHERE existing.`role_id` IN (2, 3, 4)
    AND existing.`menu_id` = @manual_selection_menu_id
  GROUP BY existing.`role_id`
) keep_row ON keep_row.`id` = rm.`id`
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
WHERE duplicate.`menu_id` = @manual_selection_menu_id;

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` = 1
  AND `menu_id` = @manual_selection_menu_id
  AND `is_deleted` = b'0';

SET @next_user_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `user_menu`);

INSERT INTO `user_menu` (
  `id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  @next_user_menu_id := @next_user_menu_id + 1,
  u.`id`,
  @manual_selection_menu_id,
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
  AND rm.`menu_id` = @manual_selection_menu_id
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = 0
  AND u.`status` = 1
  AND COALESCE(u.`account_type`, '') = 'internal'
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = @manual_selection_menu_id
  );

UPDATE `user_menu` um
JOIN (
  SELECT
    existing.`user_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = 0 THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `user_menu` existing
  JOIN `user` u ON u.`id` = existing.`user_id`
  WHERE existing.`menu_id` = @manual_selection_menu_id
    AND u.`role_id` IN (2, 3, 4)
    AND u.`is_deleted` = 0
    AND u.`status` = 1
    AND COALESCE(u.`account_type`, '') = 'internal'
  GROUP BY existing.`user_id`
) keep_row ON keep_row.`id` = um.`id`
SET um.`status` = 1,
    um.`is_deleted` = b'0',
    um.`gmt_updated` = NOW();

UPDATE `user_menu` duplicate
JOIN `user_menu` keep_row
  ON keep_row.`user_id` = duplicate.`user_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = 0
  AND duplicate.`is_deleted` = 0
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = 1,
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` = @manual_selection_menu_id;

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE u.`role_id` = 1
  AND um.`menu_id` = @manual_selection_menu_id
  AND um.`is_deleted` = b'0';
