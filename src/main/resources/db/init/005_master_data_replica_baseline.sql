-- Nuono Next local master-data replica baseline delta
-- Align the running local-db environment with the first-batch master-data replica module.

UPDATE `menu`
SET `name` = '用户管理',
    `url_path` = '/user/manage'
WHERE `id` = 10;

UPDATE `menu`
SET `name` = '角色分配',
    `url_path` = '/user/role'
WHERE `id` = 25;

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`)
SELECT 26, '角色维护', 0, '/system/role', b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `menu` WHERE `id` = 26
);

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`)
SELECT 27, '菜单维护', 0, '/system/menu', b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `menu` WHERE `id` = 27
);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`)
SELECT 1008, 1, 26, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `role_menu` WHERE `id` = 1008
);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`)
SELECT 1009, 1, 27, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `role_menu` WHERE `id` = 1009
);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`)
SELECT 1107, 2, 26, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `role_menu` WHERE `id` = 1107
);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`)
SELECT 1108, 2, 27, b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `role_menu` WHERE `id` = 1108
);

UPDATE `user`
SET `account_type` = 'internal'
WHERE `id` IN (10002, 10003, 10004);

INSERT INTO `user_menu` (`id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`, `is_deleted`)
SELECT
    21026,
    10001,
    26,
    1,
    NOW(),
    '2099-12-31 23:59:59',
    b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `user_menu` WHERE `user_id` = 10001 AND `menu_id` = 26 AND `is_deleted` = b'0'
);

INSERT INTO `user_menu` (`id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`, `is_deleted`)
SELECT
    21027,
    10001,
    27,
    1,
    NOW(),
    '2099-12-31 23:59:59',
    b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `user_menu` WHERE `user_id` = 10001 AND `menu_id` = 27 AND `is_deleted` = b'0'
);

INSERT INTO `user_menu` (`id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`, `is_deleted`)
SELECT
    22026,
    10002,
    26,
    1,
    NOW(),
    '2099-12-31 23:59:59',
    b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `user_menu` WHERE `user_id` = 10002 AND `menu_id` = 26 AND `is_deleted` = b'0'
);

INSERT INTO `user_menu` (`id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`, `is_deleted`)
SELECT
    22027,
    10002,
    27,
    1,
    NOW(),
    '2099-12-31 23:59:59',
    b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `user_menu` WHERE `user_id` = 10002 AND `menu_id` = 27 AND `is_deleted` = b'0'
);
