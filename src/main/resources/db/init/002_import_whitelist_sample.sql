-- Nuono Next sample import template v1
-- Purpose:
-- Import a small whitelist of representative accounts and their authorization data.

USE nuono_new_dev;

INSERT INTO role
SELECT *
FROM cross_border_erp.role
WHERE is_deleted = 0;

INSERT INTO menu
SELECT *
FROM cross_border_erp.menu
WHERE is_deleted = 0;

INSERT INTO role_menu
SELECT *
FROM cross_border_erp.role_menu
WHERE is_deleted = 0;

INSERT INTO `user` (
  id,
  phone,
  email,
  account_no,
  password,
  credential_version,
  token,
  role,
  role_id,
  account_type,
  real_name,
  company_name,
  list_limit,
  collect_limit,
  wh_ap_limit,
  chatgpt_translate_limit,
  level,
  noon_partner_user,
  noon_partner_project_user,
  noon_partner_pwd,
  noon_partner_encrypted_pwd,
  noon_partner_cookie,
  cookie_generate_time,
  noon_partner_id,
  noon_partner_user_code,
  noon_partner_mail_auth_code,
  status,
  effective_time,
  expired_time,
  is_deleted,
  created_by,
  updated_by,
  gmt_create,
  gmt_updated
)
SELECT
  u.id,
  u.phone,
  u.email,
  u.account_no,
  u.password,
  0 AS credential_version,
  u.token,
  u.role,
  u.role_id,
  u.account_type,
  u.real_name,
  u.company_name,
  u.list_limit,
  u.collect_limit,
  u.wh_ap_limit,
  u.chatgpt_translate_limit,
  u.level,
  u.noon_partner_user,
  u.noon_partner_project_user,
  u.noon_partner_pwd,
  u.noon_partner_encrypted_pwd,
  u.noon_partner_cookie,
  u.cookie_generate_time,
  u.noon_partner_id,
  u.noon_partner_user_code,
  u.noon_partner_mail_auth_code,
  u.status,
  u.effective_time,
  u.expired_time,
  u.is_deleted,
  u.created_by,
  u.updated_by,
  u.gmt_create,
  u.gmt_updated
FROM cross_border_erp.user u
WHERE u.is_deleted = 0
  AND u.account_no IN (
    '18521524250',
    '毕翠红',
    '马天龙',
    'xingyaoqw',
    '18660614134',
    '15812516142'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM `user` imported
    WHERE imported.id = u.id
  );

INSERT INTO user_menu
SELECT um.*
FROM cross_border_erp.user_menu um
JOIN cross_border_erp.user u ON u.id = um.user_id
WHERE um.is_deleted = 0
  AND u.is_deleted = 0
  AND u.account_no IN (
    '18521524250',
    '毕翠红',
    '马天龙',
    'xingyaoqw',
    '18660614134',
    '15812516142'
  );

INSERT INTO user_store
SELECT us.*
FROM cross_border_erp.user_store us
JOIN cross_border_erp.user u ON u.id = us.user_id
WHERE us.is_deleted = 0
  AND u.is_deleted = 0
  AND u.account_no IN (
    '18521524250',
    '毕翠红',
    '马天龙',
    'xingyaoqw',
    '18660614134',
    '15812516142'
  );
