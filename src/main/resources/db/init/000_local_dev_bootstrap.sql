-- Nuono Next local startup bootstrap
-- Purpose:
-- 1. Create the first-batch core tables directly when no local legacy reference schema exists
-- 2. Seed a small but representative local sample dataset
-- 3. Make `local-db` startup possible on a clean machine

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `code` VARCHAR(50) NOT NULL,
    `description` VARCHAR(200) DEFAULT NULL,
    `is_system` BIT(1) DEFAULT b'0',
    `parent_id` BIGINT DEFAULT 0,
    `level` INT DEFAULT 0,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `menu` (
    `id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `parent_id` BIGINT DEFAULT 0,
    `url_path` VARCHAR(255) DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `role_menu` (
    `id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_role_menu_role_id` (`role_id`),
    KEY `idx_role_menu_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL,
    `phone` VARCHAR(30) DEFAULT NULL,
    `email` VARCHAR(100) DEFAULT NULL,
    `account_no` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) DEFAULT NULL,
    `token` VARCHAR(255) DEFAULT NULL,
    `role` VARCHAR(50) DEFAULT NULL,
    `role_id` BIGINT DEFAULT NULL,
    `account_type` VARCHAR(20) DEFAULT 'external',
    `real_name` VARCHAR(50) DEFAULT NULL,
    `company_name` VARCHAR(100) DEFAULT NULL,
    `list_limit` INT DEFAULT 0,
    `collect_limit` INT DEFAULT 0,
    `wh_ap_limit` INT DEFAULT 0,
    `chatgpt_translate_limit` INT DEFAULT 0,
    `level` INT DEFAULT 0,
    `noon_partner_user` VARCHAR(100) DEFAULT NULL,
    `noon_partner_project_user` VARCHAR(100) DEFAULT NULL,
    `noon_partner_pwd` VARCHAR(255) DEFAULT NULL,
    `noon_partner_encrypted_pwd` VARCHAR(255) DEFAULT NULL,
    `noon_partner_cookie` TEXT DEFAULT NULL,
    `cookie_generate_time` DATETIME DEFAULT NULL,
    `noon_partner_id` VARCHAR(100) DEFAULT NULL,
    `noon_partner_user_code` VARCHAR(100) DEFAULT NULL,
    `noon_partner_mail_auth_code` VARCHAR(255) DEFAULT NULL,
    `status` INT DEFAULT 1,
    `effective_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `expired_time` DATETIME DEFAULT '2099-12-31 23:59:59',
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_account_no` (`account_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_menu` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    `status` INT DEFAULT 1,
    `effective_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `expired_time` DATETIME DEFAULT '2099-12-31 23:59:59',
    `is_deleted` BIT(1) DEFAULT b'0',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_menu_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_store` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `org_code` VARCHAR(50) DEFAULT NULL,
    `org_name` VARCHAR(100) DEFAULT NULL,
    `project_code` VARCHAR(100) DEFAULT NULL,
    `project_name` VARCHAR(100) DEFAULT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `manager_id` BIGINT DEFAULT NULL,
    `site` VARCHAR(20) DEFAULT NULL,
    `is_authorized` BIT(1) DEFAULT b'0',
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_store_user_id` (`user_id`),
    KEY `idx_user_store_store_code` (`store_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELETE FROM `user_menu`;
DELETE FROM `role_menu`;
DELETE FROM `user_store`;
DELETE FROM `menu`;
DELETE FROM `user`;
DELETE FROM `role`;

INSERT INTO `role` (`id`, `name`, `code`, `description`, `is_system`, `parent_id`, `level`, `is_deleted`, `created_by`, `updated_by`)
VALUES
    (1, '系统管理员', 'SYSTEM_ADMIN', '平台最高权限', b'1', 0, 0, b'0', 1, 1),
    (2, '老板', 'BOSS', '商家负责人', b'1', 1, 1, b'0', 1, 1),
    (3, '运营主管', 'OPS_MANAGER', '管理运营团队', b'1', 2, 2, b'0', 1, 1),
    (4, '运营', 'OPS', '负责选品、上架、活动', b'1', 3, 3, b'0', 1, 1),
    (5, '采购', 'PURCHASE', '负责询价、下单、成本', b'1', 3, 3, b'0', 1, 1),
    (6, '仓管', 'WAREHOUSE', '负责入库、约仓、发货', b'1', 3, 3, b'0', 1, 1);

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`)
VALUES
    (6, '利润计算', 0, '/profit', b'0'),
    (9, '约仓看板', 0, '/warehouse/appointment', b'0'),
    (10, '账号管理', 0, '/account', b'0'),
    (19, '任务列表', 0, '/task', b'0'),
    (21, '商品销量', 0, '/sales', b'0'),
    (22, '数据预约', 0, '/data-reservation', b'0'),
    (25, '角色分配', 0, '/role', b'0');

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`)
VALUES
    (1001, 1, 6, b'0'),
    (1002, 1, 9, b'0'),
    (1003, 1, 10, b'0'),
    (1004, 1, 19, b'0'),
    (1005, 1, 21, b'0'),
    (1006, 1, 22, b'0'),
    (1007, 1, 25, b'0'),
    (1101, 2, 6, b'0'),
    (1102, 2, 9, b'0'),
    (1103, 2, 19, b'0'),
    (1104, 2, 21, b'0'),
    (1105, 2, 22, b'0'),
    (1106, 2, 25, b'0'),
    (1201, 3, 6, b'0'),
    (1202, 3, 9, b'0'),
    (1203, 3, 19, b'0'),
    (1204, 3, 21, b'0'),
    (1205, 3, 22, b'0'),
    (1206, 3, 25, b'0'),
    (1301, 4, 6, b'0'),
    (1302, 4, 9, b'0'),
    (1303, 4, 19, b'0'),
    (1304, 4, 21, b'0'),
    (1305, 4, 22, b'0');

INSERT INTO `user` (
    `id`, `phone`, `email`, `account_no`, `password`, `role`, `role_id`, `account_type`, `real_name`,
    `company_name`, `list_limit`, `collect_limit`, `wh_ap_limit`, `chatgpt_translate_limit`, `level`,
    `noon_partner_user`, `noon_partner_project_user`, `noon_partner_pwd`, `noon_partner_id`,
    `noon_partner_user_code`, `noon_partner_mail_auth_code`, `status`, `effective_time`, `expired_time`,
    `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (10001, '18500000001', 'admin@nuono.local', 'admin', 'admin123', 'ADMIN', 1, 'internal', '系统管理员',
     'Nuono Next', 999, 999, 999, 999, 0,
     NULL, NULL, NULL, NULL,
     NULL, NULL, 1, NOW(), '2099-12-31 23:59:59',
     b'0', 1, 1),
    (10002, '18521524250', 'boss@demo.local', '18521524250', 'boss123', 'BOSS', 2, 'external', '老板示例',
     '松果果儿', 50, 50, 10, 20, 1,
     'boss.demo', 'boss.project', 'boss-pass', 'NP-10002',
     'UC-10002', 'MAIL-10002', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10001, 10001),
    (10003, '18521524251', 'manager@demo.local', '毕翠红', 'manager123', 'OPS_MANAGER', 3, 'external', '毕翠红',
     '松果果儿', 30, 30, 8, 12, 2,
     'bicuihong.noon', 'bicuihong.project', 'ops-pass', 'NP-10003',
     'UC-10003', 'MAIL-10003', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10002, 10002),
    (10004, '18521524252', 'ops1@demo.local', '马天龙', 'ops123', 'OPS', 4, 'external', '马天龙',
     '松果果儿', 20, 20, 4, 6, 3,
     'matianlong.noon', 'matianlong.project', 'ops-pass', 'NP-10004',
     'UC-10004', 'MAIL-10004', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10003, 10003),
    (10005, '18660614134', 'ops2@demo.local', '18660614134', 'ops234', 'OPS', 4, 'external', '异常约仓样本',
     '松果果儿', 20, 20, 4, 6, 3,
     'appoint.demo', 'appoint.project', 'ops-pass', 'NP-10005',
     'UC-10005', 'MAIL-10005', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10003, 10003),
    (10006, '15812516142', 'legacy@demo.local', 'xingyaoqw', 'legacy123', 'BOSS', 2, 'external', '遗留绑定样本',
     '松果果儿', 30, 30, 6, 10, 1,
     'legacy.noon', NULL, 'legacy-pass', 'NP-10006',
     'UC-10006', 'MAIL-10006', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10001, 10001);

INSERT INTO `user_menu` (`id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`, `is_deleted`)
SELECT
    20000 + ROW_NUMBER() OVER (ORDER BY u.id, rm.menu_id) AS id,
    u.id,
    rm.menu_id,
    1,
    NOW(),
    '2099-12-31 23:59:59',
    b'0'
FROM `user` u
JOIN `role_menu` rm ON rm.role_id = u.role_id AND rm.is_deleted = b'0'
WHERE u.is_deleted = b'0';

INSERT INTO `user_store` (
    `id`, `user_id`, `org_code`, `org_name`, `project_code`, `project_name`,
    `store_code`, `manager_id`, `site`, `is_authorized`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (30001, 10002, 'ORG-001', '松果果儿', 'PJT-RUH01S', 'RUH01S 项目',
     'STORE-RUH01S', 10003, 'SA', b'1', b'0', 10002, 10002),
    (30002, 10002, 'ORG-001', '松果果儿', 'PJT-JED01', 'JED01 项目',
     'STORE-JED01', 10003, 'AE', b'1', b'0', 10002, 10002),
    (30003, 10003, 'ORG-001', '松果果儿', 'PJT-RUH01S', 'RUH01S 项目',
     'STORE-RUH01S', 10003, 'SA', b'1', b'0', 10002, 10003),
    (30004, 10004, 'ORG-001', '松果果儿', 'PJT-JED01', 'JED01 项目',
     'STORE-JED01', 10003, 'AE', b'1', b'0', 10003, 10004),
    (30005, 10005, 'ORG-001', '松果果儿', 'PJT-RUH01S', 'RUH01S 项目',
     'STORE-RUH01S', 10003, 'SA', b'1', b'0', 10003, 10005),
    (30006, 10006, 'ORG-001', '松果果儿', 'PJT-NOON-OLD', '遗留 Noon 项目',
     'STORE-NOON-OLD', 10003, 'SA', b'0', b'0', 10002, 10006);
