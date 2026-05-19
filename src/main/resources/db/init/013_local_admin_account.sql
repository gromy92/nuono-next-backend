-- Local admin test account for nuono-next acceptance.
-- Password is stored as the legacy MD5(raw + 'noon_helper') digest.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET @local_admin_account_no = CONVERT('adminBI' USING utf8mb4) COLLATE utf8mb4_unicode_ci;
SET @local_admin_password_hash = CONVERT('fec7ef8a6187e00711bcb0411832fb2f' USING utf8mb4) COLLATE utf8mb4_unicode_ci;

INSERT INTO `user` (
  phone,
  email,
  account_no,
  password,
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
  status,
  effective_time,
  expired_time,
  is_deleted,
  created_by,
  updated_by
)
SELECT
  NULL,
  NULL,
  @local_admin_account_no,
  @local_admin_password_hash,
  'ADMIN',
  1,
  'internal',
  'adminBI',
  'Nuono',
  0,
  0,
  0,
  0,
  0,
  1,
  NULL,
  NULL,
  0,
  1,
  1
WHERE NOT EXISTS (
  SELECT 1
  FROM `user`
  WHERE account_no = @local_admin_account_no
);

SET @local_admin_user_id = (
  SELECT id
  FROM `user`
  WHERE account_no = @local_admin_account_no
  ORDER BY is_deleted ASC, id DESC
  LIMIT 1
);

UPDATE `user`
SET
  password = @local_admin_password_hash,
  role = 'ADMIN',
  role_id = 1,
  account_type = 'internal',
  real_name = 'adminBI',
  company_name = 'Nuono',
  level = 0,
  status = 1,
  effective_time = NULL,
  expired_time = NULL,
  is_deleted = 0,
  updated_by = 1,
  gmt_updated = NOW()
WHERE id = @local_admin_user_id;

UPDATE user_menu
SET
  status = 1,
  is_deleted = 0,
  updated_by = 1,
  gmt_updated = NOW()
WHERE user_id = @local_admin_user_id
  AND menu_id IN (SELECT id FROM menu WHERE is_deleted = 0);

INSERT INTO user_menu (
  user_id,
  menu_id,
  effective_time,
  expired_time,
  status,
  is_deleted,
  created_by,
  updated_by
)
SELECT
  @local_admin_user_id,
  m.id,
  NULL,
  NULL,
  1,
  0,
  1,
  1
FROM menu m
WHERE m.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM user_menu um
    WHERE um.user_id = @local_admin_user_id
      AND um.menu_id = m.id
      AND um.is_deleted = 0
  );
