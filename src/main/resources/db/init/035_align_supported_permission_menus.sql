-- Align master-data permission menus with the functions currently implemented in nuono-next.
-- The service layer filters historical legacy menus out of menu management and permission overview;
-- this script makes fresh/local databases carry the same canonical rows.

SET NAMES utf8mb4;

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (2, '采购', 0, '/purchase', b'0', NOW(), NOW()),
  (3, '商品', 0, '/product', b'0', NOW(), NOW()),
  (5, '用户', 0, '/user', b'0', NOW(), NOW()),
  (26, '系统管理', 0, '/system', b'0', NOW(), NOW()),
  (9200, '物流', 0, '/logistics', b'0', NOW(), NOW()),
  (6, '利润计算', 2, '/api/sku/cost', b'0', NOW(), NOW()),
  (24, '采购单', 2, '/api/purchase/order', b'0', NOW(), NOW()),
  (7, '商品管理', 3, '/api/sku/manage', b'0', NOW(), NOW()),
  (9102, '人工选品', 3, '/product/manual-selection', b'0', NOW(), NOW()),
  (9201, '货代管理', 9200, '/purchase/logistics-quote', b'0', NOW(), NOW()),
  (10, '账号管理', 5, '/api/user/manage', b'0', NOW(), NOW()),
  (25, '角色分配', 5, '/api/user/role', b'0', NOW(), NOW()),
  (27, '角色管理', 26, '/system/role', b'0', NOW(), NOW()),
  (28, '菜单维护', 26, '/system/menu', b'0', NOW(), NOW()),
  (9301, '文件管理', 26, '/system/file-management', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

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
  SELECT 1 AS `role_id`, 10 AS `menu_id`
  UNION ALL SELECT 1, 27
  UNION ALL SELECT 1, 28
  UNION ALL SELECT 1, 9301
  UNION ALL SELECT 2, 6
  UNION ALL SELECT 2, 24
  UNION ALL SELECT 2, 7
  UNION ALL SELECT 2, 9102
  UNION ALL SELECT 2, 9201
  UNION ALL SELECT 2, 25
  UNION ALL SELECT 2, 9301
  UNION ALL SELECT 3, 6
  UNION ALL SELECT 3, 24
  UNION ALL SELECT 3, 7
  UNION ALL SELECT 3, 9102
  UNION ALL SELECT 3, 9201
  UNION ALL SELECT 3, 9301
  UNION ALL SELECT 4, 6
  UNION ALL SELECT 4, 24
  UNION ALL SELECT 4, 7
  UNION ALL SELECT 4, 9102
  UNION ALL SELECT 4, 9201
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
  SELECT COALESCE(MIN(CASE WHEN existing.`is_deleted` = b'0' THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `role_menu` existing
  JOIN (
    SELECT 1 AS `role_id`, 10 AS `menu_id`
    UNION ALL SELECT 1, 27
    UNION ALL SELECT 1, 28
    UNION ALL SELECT 1, 9301
    UNION ALL SELECT 2, 6
    UNION ALL SELECT 2, 24
    UNION ALL SELECT 2, 7
    UNION ALL SELECT 2, 9102
    UNION ALL SELECT 2, 9201
    UNION ALL SELECT 2, 25
    UNION ALL SELECT 2, 9301
    UNION ALL SELECT 3, 6
    UNION ALL SELECT 3, 24
    UNION ALL SELECT 3, 7
    UNION ALL SELECT 3, 9102
    UNION ALL SELECT 3, 9201
    UNION ALL SELECT 3, 9301
    UNION ALL SELECT 4, 6
    UNION ALL SELECT 4, 24
    UNION ALL SELECT 4, 7
    UNION ALL SELECT 4, 9102
    UNION ALL SELECT 4, 9201
  ) grants ON grants.`role_id` = existing.`role_id` AND grants.`menu_id` = existing.`menu_id`
  GROUP BY existing.`role_id`, existing.`menu_id`
) keep_row ON keep_row.`id` = rm.`id`
SET rm.`is_deleted` = b'0',
    rm.`gmt_updated` = NOW();

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` = 1
  AND `menu_id` = 25
  AND `is_deleted` = b'0';

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` <> 1
  AND `menu_id` = 10
  AND `is_deleted` = b'0';

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` <> 2
  AND `menu_id` = 25
  AND `is_deleted` = b'0';

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` = 1
  AND `menu_id` IN (6, 24, 7, 9102, 9201, 25)
  AND `is_deleted` = b'0';

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
  AND rm.`is_deleted` = b'0'
  AND rm.`menu_id` IN (6, 24, 7, 9102, 9201, 10, 25, 27, 28, 9301)
WHERE u.`is_deleted` = b'0'
  AND u.`status` = 1
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = rm.`menu_id`
  );

UPDATE `user_menu` um
JOIN (
  SELECT COALESCE(MIN(CASE WHEN existing.`is_deleted` = 0 THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `user_menu` existing
  JOIN `user` u ON u.`id` = existing.`user_id`
  JOIN `role_menu` rm ON rm.`role_id` = u.`role_id`
    AND rm.`menu_id` = existing.`menu_id`
    AND rm.`is_deleted` = b'0'
  WHERE existing.`menu_id` IN (6, 24, 7, 9102, 9201, 10, 25, 27, 28, 9301)
    AND u.`is_deleted` = b'0'
    AND u.`status` = 1
    AND COALESCE(u.`account_type`, '') = 'internal'
  GROUP BY existing.`user_id`, existing.`menu_id`
) keep_row ON keep_row.`id` = um.`id`
SET um.`status` = 1,
    um.`is_deleted` = b'0',
    um.`gmt_updated` = NOW();

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE u.`role_id` = 1
  AND um.`menu_id` = 25
  AND um.`is_deleted` = b'0';

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE u.`role_id` <> 1
  AND um.`menu_id` = 10
  AND um.`is_deleted` = b'0';

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE u.`role_id` <> 2
  AND um.`menu_id` = 25
  AND um.`is_deleted` = b'0';

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE u.`role_id` = 1
  AND um.`menu_id` IN (6, 24, 7, 9102, 9201, 25)
  AND um.`is_deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS tmp_supported_role_menu;
CREATE TEMPORARY TABLE tmp_supported_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, menu_id)
);

INSERT INTO tmp_supported_role_menu (role_id, menu_id)
VALUES
  (1, 10),
  (1, 27),
  (1, 28),
  (1, 9301),
  (2, 6),
  (2, 24),
  (2, 7),
  (2, 9102),
  (2, 9201),
  (2, 25),
  (2, 9301),
  (3, 6),
  (3, 24),
  (3, 7),
  (3, 9102),
  (3, 9201),
  (3, 9301),
  (4, 6),
  (4, 24),
  (4, 7),
  (4, 9102),
  (4, 9201);

UPDATE `role_menu` rm
LEFT JOIN tmp_supported_role_menu supported
  ON supported.role_id = rm.`role_id` AND supported.menu_id = rm.`menu_id`
SET rm.`is_deleted` = b'1',
    rm.`gmt_updated` = NOW()
WHERE rm.`is_deleted` = b'0'
  AND supported.role_id IS NULL;

UPDATE `role_menu` duplicate
JOIN `role_menu` keep_row
  ON keep_row.`role_id` = duplicate.`role_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = b'0'
  AND duplicate.`is_deleted` = b'0'
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = b'1',
    duplicate.`gmt_updated` = NOW();

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
LEFT JOIN tmp_supported_role_menu supported
  ON supported.role_id = u.`role_id` AND supported.menu_id = um.`menu_id`
SET um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE um.`is_deleted` = 0
  AND (
    u.`is_deleted` <> 0
    OR u.`status` <> 1
    OR COALESCE(u.`account_type`, '') <> 'internal'
    OR supported.role_id IS NULL
  );

UPDATE `user_menu` duplicate
JOIN `user_menu` keep_row
  ON keep_row.`user_id` = duplicate.`user_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = 0
  AND duplicate.`is_deleted` = 0
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = 1,
    duplicate.`gmt_updated` = NOW();

SET @add_role_menu_active_key = IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'role_menu'
      AND column_name = 'active_unique_key'
  ),
  'ALTER TABLE role_menu ADD COLUMN active_unique_key TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = b''0'' THEN 1 ELSE NULL END) STORED',
  'SELECT ''role_menu_active_unique_key_exists'' AS stage'
);
PREPARE add_role_menu_active_key_stmt FROM @add_role_menu_active_key;
EXECUTE add_role_menu_active_key_stmt;
DEALLOCATE PREPARE add_role_menu_active_key_stmt;

SET @add_user_menu_active_key = IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_menu'
      AND column_name = 'active_unique_key'
  ),
  'ALTER TABLE user_menu ADD COLUMN active_unique_key TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED',
  'SELECT ''user_menu_active_unique_key_exists'' AS stage'
);
PREPARE add_user_menu_active_key_stmt FROM @add_user_menu_active_key;
EXECUTE add_user_menu_active_key_stmt;
DEALLOCATE PREPARE add_user_menu_active_key_stmt;

SET @add_role_menu_unique = IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'role_menu'
      AND index_name = 'uk_role_menu_active_pair'
  ),
  'CREATE UNIQUE INDEX uk_role_menu_active_pair ON role_menu (role_id, menu_id, active_unique_key)',
  'SELECT ''role_menu_active_unique_exists'' AS stage'
);
PREPARE add_role_menu_unique_stmt FROM @add_role_menu_unique;
EXECUTE add_role_menu_unique_stmt;
DEALLOCATE PREPARE add_role_menu_unique_stmt;

SET @add_user_menu_unique = IF(
  NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user_menu'
      AND index_name = 'uk_user_menu_active_pair'
  ),
  'CREATE UNIQUE INDEX uk_user_menu_active_pair ON user_menu (user_id, menu_id, active_unique_key)',
  'SELECT ''user_menu_active_unique_exists'' AS stage'
);
PREPARE add_user_menu_unique_stmt FROM @add_user_menu_unique;
EXECUTE add_user_menu_unique_stmt;
DEALLOCATE PREPARE add_user_menu_unique_stmt;
