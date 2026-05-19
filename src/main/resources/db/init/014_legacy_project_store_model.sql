-- Legacy project-level store/account model alignment.
-- Latest legacy system stores Noon binding and team authorization at project level:
--   user                  account master without Noon session ownership
--   user_project          boss-owned project/store rows
--   user_project_access   member-to-project authorization
--   store_data_user_mapping site data owner mapping
--
-- The local 20260428 snapshot does not include these four tables, so this script
-- derives project rows from user_store and keeps user_store as site-level data.
-- Do not copy Noon credentials from account master rows. Latest legacy code
-- reads Noon binding from user_project only; if production
-- user_project is unavailable, the derived project row must stay unbound.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

CREATE TABLE IF NOT EXISTS `user_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '所属用户ID（店铺创建人/老板）',
  `project_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon project code',
  `project_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '店铺名称',
  `org_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织代码',
  `org_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织名称',
  `noon_partner_user` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Noon登录邮箱',
  `noon_partner_project_user` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Noon后台用户名',
  `noon_partner_pwd` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Noon密码',
  `noon_partner_encrypted_pwd` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Noon加密密码',
  `noon_partner_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Noon partner id',
  `noon_partner_user_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Noon用户code',
  `noon_partner_mail_auth_code` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱授权码',
  `noon_partner_cookie` text COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '店铺级Noon cookie',
  `cookie_generate_time` datetime DEFAULT NULL COMMENT 'cookie生成时间',
  `collect_limit` int DEFAULT NULL COMMENT '采集额度（店铺级）',
  `list_limit` int DEFAULT NULL COMMENT '刊登额度（店铺级）',
  `wh_ap_limit` int DEFAULT NULL COMMENT '仓储AP额度（店铺级）',
  `chatgpt_translate_limit` int DEFAULT NULL COMMENT 'ChatGPT翻译额度（店铺级）',
  `bind_status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '绑定状态：0未绑定，1已绑定',
  `is_authorized` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否授权',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_project_owner_code_deleted` (`user_id`, `project_code`, `is_deleted`),
  KEY `idx_user_project_user_id` (`user_id`),
  KEY `idx_user_project_project_code` (`project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目级店铺表';

INSERT INTO `user_project` (
  user_id, project_code, project_name, org_code, org_name,
  noon_partner_user, noon_partner_project_user, noon_partner_pwd,
  noon_partner_encrypted_pwd, noon_partner_id, noon_partner_user_code,
  noon_partner_mail_auth_code, noon_partner_cookie, cookie_generate_time,
  collect_limit, list_limit, wh_ap_limit, chatgpt_translate_limit,
  bind_status, is_authorized, is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
  us.user_id,
  NULLIF(MAX(NULLIF(us.project_code, '')), '') AS project_code,
  NULLIF(MAX(NULLIF(us.project_name, '')), '') AS project_name,
  NULLIF(MAX(NULLIF(us.org_code, '')), '') AS org_code,
  NULLIF(MAX(NULLIF(us.org_name, '')), '') AS org_name,
  NULL AS noon_partner_user,
  NULL AS noon_partner_project_user,
  NULL AS noon_partner_pwd,
  NULL AS noon_partner_encrypted_pwd,
  REPLACE(MAX(NULLIF(us.project_code, '')), 'PRJ', '') AS noon_partner_id,
  NULL AS noon_partner_user_code,
  NULL AS noon_partner_mail_auth_code,
  NULL AS noon_partner_cookie,
  NULL AS cookie_generate_time,
  NULL AS collect_limit,
  NULL AS list_limit,
  NULL AS wh_ap_limit,
  NULL AS chatgpt_translate_limit,
  0 AS bind_status,
  MAX(CASE WHEN us.is_authorized = 1 THEN 1 ELSE 0 END) AS is_authorized,
  0 AS is_deleted,
  us.user_id AS created_by,
  us.user_id AS updated_by,
  MIN(us.gmt_create) AS gmt_create,
  MAX(us.gmt_updated) AS gmt_updated
FROM user_store us
JOIN `user` u ON u.id = us.user_id AND u.is_deleted = 0
JOIN role r ON r.id = u.role_id AND r.is_deleted = 0 AND r.level = 1
WHERE us.is_deleted = 0
  AND NULLIF(us.project_code, '') IS NOT NULL
GROUP BY
  us.user_id,
  us.project_code
ON DUPLICATE KEY UPDATE
  project_name = VALUES(project_name),
  org_code = VALUES(org_code),
  org_name = VALUES(org_name),
  noon_partner_user = COALESCE(user_project.noon_partner_user, VALUES(noon_partner_user)),
  noon_partner_project_user = COALESCE(user_project.noon_partner_project_user, VALUES(noon_partner_project_user)),
  noon_partner_pwd = COALESCE(user_project.noon_partner_pwd, VALUES(noon_partner_pwd)),
  noon_partner_encrypted_pwd = COALESCE(user_project.noon_partner_encrypted_pwd, VALUES(noon_partner_encrypted_pwd)),
  noon_partner_id = COALESCE(user_project.noon_partner_id, VALUES(noon_partner_id)),
  noon_partner_user_code = COALESCE(user_project.noon_partner_user_code, VALUES(noon_partner_user_code)),
  noon_partner_mail_auth_code = COALESCE(user_project.noon_partner_mail_auth_code, VALUES(noon_partner_mail_auth_code)),
  noon_partner_cookie = COALESCE(user_project.noon_partner_cookie, VALUES(noon_partner_cookie)),
  cookie_generate_time = COALESCE(user_project.cookie_generate_time, VALUES(cookie_generate_time)),
  collect_limit = COALESCE(user_project.collect_limit, VALUES(collect_limit)),
  list_limit = COALESCE(user_project.list_limit, VALUES(list_limit)),
  wh_ap_limit = COALESCE(user_project.wh_ap_limit, VALUES(wh_ap_limit)),
  chatgpt_translate_limit = COALESCE(user_project.chatgpt_translate_limit, VALUES(chatgpt_translate_limit)),
  bind_status = GREATEST(user_project.bind_status, VALUES(bind_status)),
  is_authorized = GREATEST(user_project.is_authorized, VALUES(is_authorized)),
  updated_by = VALUES(updated_by),
  gmt_updated = NOW();

CREATE TABLE IF NOT EXISTS `user_project_access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `project_id` bigint NOT NULL COMMENT 'user_project.id',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_project_access_active` (`user_id`, `project_id`, `is_deleted`),
  KEY `idx_user_project_access_user_id` (`user_id`),
  KEY `idx_user_project_access_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户项目店铺授权表';

INSERT INTO `user_project_access` (
  user_id, project_id, is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT DISTINCT
  us.user_id,
  up.id AS project_id,
  0 AS is_deleted,
  COALESCE(us.created_by, up.user_id) AS created_by,
  COALESCE(us.updated_by, up.user_id) AS updated_by,
  COALESCE(us.gmt_create, NOW()) AS gmt_create,
  COALESCE(us.gmt_updated, NOW()) AS gmt_updated
FROM user_store us
JOIN `user` u ON u.id = us.user_id AND u.is_deleted = 0
JOIN role r ON r.id = u.role_id AND r.is_deleted = 0 AND r.level > 1
JOIN user_project up ON up.project_code = us.project_code AND up.is_deleted = 0
WHERE us.is_deleted = 0
  AND us.user_id <> up.user_id
  AND NULLIF(us.project_code, '') IS NOT NULL
ON DUPLICATE KEY UPDATE
  is_deleted = 0,
  updated_by = VALUES(updated_by),
  gmt_updated = NOW();

CREATE TABLE IF NOT EXISTS `store_data_user_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '站点级店铺编码',
  `effective_data_user_id` bigint NOT NULL COMMENT '实际数据归属用户ID',
  `owner_user_id` bigint DEFAULT NULL COMMENT '项目老板用户ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_data_user_mapping_code` (`store_code`),
  KEY `idx_store_data_user_mapping_owner` (`owner_user_id`),
  KEY `idx_store_data_user_mapping_effective` (`effective_data_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点数据用户映射表';

INSERT INTO `store_data_user_mapping` (
  store_code, effective_data_user_id, owner_user_id, is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
  site_rows.store_code,
  site_rows.owner_user_id,
  site_rows.owner_user_id,
  0,
  site_rows.owner_user_id,
  site_rows.owner_user_id,
  NOW(),
  NOW()
FROM (
  SELECT
    us.store_code,
    MIN(up.user_id) AS owner_user_id
  FROM user_store us
  JOIN user_project up ON up.project_code = us.project_code AND up.is_deleted = 0
  WHERE us.is_deleted = 0
    AND NULLIF(us.store_code, '') IS NOT NULL
  GROUP BY us.store_code
) site_rows
ON DUPLICATE KEY UPDATE
  owner_user_id = VALUES(owner_user_id),
  effective_data_user_id = VALUES(effective_data_user_id),
  is_deleted = 0,
  updated_by = VALUES(updated_by),
  gmt_updated = NOW();
