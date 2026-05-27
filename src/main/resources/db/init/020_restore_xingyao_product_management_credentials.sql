-- Restore the local xingyao product-management validation owner.
--
-- Product-management regression uses owner user 10002 with PRJ245027.
-- Later project-store model imports can leave this user_project row without
-- Noon credentials while keeping an old READY product projection. That state
-- lets list/read paths pass but breaks init-preflight/init-start repeatability.

UPDATE `user`
SET
  account_no = CONCAT('legacy_', id, '_', account_no),
  status = 0,
  updated_by = 10003,
  gmt_updated = NOW()
WHERE account_no = 'xingyaoqw'
  AND id <> 10002;

INSERT INTO `user` (
  id,
  phone,
  email,
  account_no,
  `password`,
  `role`,
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
  10002,
  '18521524250',
  'xingyao-product-test@nuono.local',
  'xingyaoqw',
  'boss123',
  'BOSS',
  2,
  'internal',
  'xingyao测试老板',
  'xingyao',
  50,
  50,
  10,
  20,
  1,
  1,
  NOW(),
  '2099-12-31 23:59:59',
  0,
  10003,
  10003
WHERE NOT EXISTS (
  SELECT 1
  FROM `user`
  WHERE id = 10002
);

UPDATE `user`
SET
  account_no = 'xingyaoqw',
  `password` = 'boss123',
  `role` = 'BOSS',
  role_id = 2,
  account_type = 'internal',
  real_name = COALESCE(NULLIF(real_name, ''), 'xingyao测试老板'),
  company_name = 'xingyao',
  status = 1,
  is_deleted = 0,
  updated_by = 10003,
  gmt_updated = NOW()
WHERE id = 10002;

INSERT INTO user_project (
  user_id,
  project_code,
  project_name,
  org_code,
  org_name,
  noon_partner_user,
  noon_partner_project_user,
  noon_partner_pwd,
  noon_partner_id,
  bind_status,
  is_authorized,
  is_deleted,
  created_by,
  updated_by
)
SELECT
  10002,
  'PRJ245027',
  'xingyao',
  'ORG-P245027',
  'xingyao',
  'nuonuo@p245027.idp.noon.partners',
  'nuonuo@p245027.idp.noon.partners',
  'j0vtZ%ftvG324VU(',
  '245027',
  1,
  1,
  0,
  10003,
  10003
WHERE NOT EXISTS (
  SELECT 1
  FROM user_project
  WHERE user_id = 10002
    AND project_code = 'PRJ245027'
    AND is_deleted = 0
);

UPDATE user_project
SET
  noon_partner_user = 'nuonuo@p245027.idp.noon.partners',
  noon_partner_project_user = 'nuonuo@p245027.idp.noon.partners',
  noon_partner_pwd = 'j0vtZ%ftvG324VU(',
  noon_partner_id = '245027',
  bind_status = 1,
  is_authorized = 1,
  updated_by = 10003,
  gmt_updated = NOW()
WHERE user_id = 10002
  AND project_code = 'PRJ245027'
  AND is_deleted = 0;

UPDATE user_store
SET
  org_code = 'ORG-P245027',
  org_name = 'xingyao',
  project_code = 'PRJ245027',
  project_name = 'xingyao',
  store_code = 'STR245027-NAE',
  site = 'AE',
  is_authorized = 1,
  is_deleted = 0,
  updated_by = 10003,
  gmt_updated = NOW()
WHERE user_id = 10002
  AND store_code = 'STR245027-NAE';

INSERT INTO user_store (
  user_id,
  org_code,
  org_name,
  project_code,
  project_name,
  store_code,
  site,
  is_authorized,
  is_deleted,
  created_by,
  updated_by
)
SELECT
  10002,
  'ORG-P245027',
  'xingyao',
  'PRJ245027',
  'xingyao',
  'STR245027-NAE',
  'AE',
  1,
  0,
  10003,
  10003
WHERE NOT EXISTS (
  SELECT 1
  FROM user_store
  WHERE user_id = 10002
    AND store_code = 'STR245027-NAE'
);

UPDATE user_store
SET
  org_code = 'ORG-P245027',
  org_name = 'xingyao',
  project_code = 'PRJ245027',
  project_name = 'xingyao',
  store_code = 'STR245027-NSA',
  site = 'SA',
  is_authorized = 1,
  is_deleted = 0,
  updated_by = 10003,
  gmt_updated = NOW()
WHERE user_id = 10002
  AND store_code = 'STR245027-NSA';

INSERT INTO user_store (
  user_id,
  org_code,
  org_name,
  project_code,
  project_name,
  store_code,
  site,
  is_authorized,
  is_deleted,
  created_by,
  updated_by
)
SELECT
  10002,
  'ORG-P245027',
  'xingyao',
  'PRJ245027',
  'xingyao',
  'STR245027-NSA',
  'SA',
  1,
  0,
  10003,
  10003
WHERE NOT EXISTS (
  SELECT 1
  FROM user_store
  WHERE user_id = 10002
    AND store_code = 'STR245027-NSA'
);

SET @next_user_menu_id = (SELECT COALESCE(MAX(id), 0) FROM user_menu);

INSERT INTO user_menu (
  id,
  user_id,
  menu_id,
  status,
  effective_time,
  expired_time,
  is_deleted,
  created_by,
  updated_by
)
SELECT
  @next_user_menu_id := @next_user_menu_id + 1,
  10002,
  rm.menu_id,
  1,
  NOW(),
  '2099-12-31 23:59:59',
  0,
  10003,
  10003
FROM `user` u
JOIN role_menu rm ON rm.role_id = 2
WHERE u.id = 10002
  AND u.is_deleted = 0
  AND rm.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM user_menu um
    WHERE um.user_id = 10002
      AND um.menu_id = rm.menu_id
      AND um.is_deleted = 0
  );
