-- Replay latest production role/store/Noon data fixes that are not present in the local 20260428 snapshot.
-- No backup tables are created: the new local system tables are treated as disposable migration targets.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

/* 1. Production account supplement: 陆文欢 is `user`.id = 338. */
INSERT INTO `user` (
  id, phone, email, account_no, `password`, token, `role`, role_id, account_type,
  real_name, company_name, list_limit, collect_limit, wh_ap_limit, chatgpt_translate_limit,
  `level`, `status`, effective_time, expired_time, is_deleted, created_by, updated_by,
  gmt_create, gmt_updated
)
SELECT
  338,
  NULL,
  NULL,
  '陆文欢',
  COALESCE((SELECT `password` FROM `user` WHERE id = 307 LIMIT 1), (SELECT `password` FROM `user` WHERE id = 307 LIMIT 1)),
  NULL,
  '运营',
  4,
  'internal',
  '陆文欢',
  NULL,
  0,
  0,
  0,
  0,
  3,
  1,
  COALESCE((SELECT effective_time FROM `user` WHERE id = 307 LIMIT 1), NOW()),
  COALESCE((SELECT expired_time FROM `user` WHERE id = 307 LIMIT 1), '2099-12-31 23:59:59'),
  0,
  307,
  307,
  NOW(),
  NOW()
ON DUPLICATE KEY UPDATE
  account_no = VALUES(account_no),
  real_name = VALUES(real_name),
  `role` = VALUES(`role`),
  role_id = VALUES(role_id),
  account_type = VALUES(account_type),
  `level` = VALUES(`level`),
  `status` = VALUES(`status`),
  is_deleted = 0,
  created_by = VALUES(created_by),
  updated_by = VALUES(updated_by),
  gmt_updated = NOW();

INSERT INTO `user` (
  id, phone, email, account_no, `password`, token, `role`, role_id, account_type,
  real_name, company_name, list_limit, collect_limit, wh_ap_limit, chatgpt_translate_limit,
  `level`, `status`, effective_time, expired_time, is_deleted, created_by, updated_by,
  gmt_create, gmt_updated
)
SELECT
  u.id, u.phone, u.email, u.account_no, u.`password`, u.token, u.`role`, u.role_id, u.account_type,
  u.real_name, u.company_name, u.list_limit, u.collect_limit, u.wh_ap_limit, u.chatgpt_translate_limit,
  u.`level`, u.`status`, u.effective_time, u.expired_time, u.is_deleted, u.created_by, u.updated_by,
  u.gmt_create, u.gmt_updated
FROM `user` u
WHERE u.id = 338
ON DUPLICATE KEY UPDATE
  account_no = VALUES(account_no),
  real_name = VALUES(real_name),
  `role` = VALUES(`role`),
  role_id = VALUES(role_id),
  account_type = VALUES(account_type),
  `level` = VALUES(`level`),
  `status` = VALUES(`status`),
  is_deleted = 0,
  created_by = VALUES(created_by),
  updated_by = VALUES(updated_by),
  gmt_updated = NOW();

/* 2. Production organization hotfix: 毕翠红 -> 郭瑶 -> 韩雨. */
UPDATE `user` c
JOIN `user` p ON p.account_no = '毕翠红' AND p.is_deleted = 0
SET c.created_by = p.id, c.updated_by = p.id, c.gmt_updated = NOW()
WHERE c.account_no = '郭瑶' AND c.is_deleted = 0;

UPDATE `user` c
JOIN `user` p ON p.account_no = '郭瑶' AND p.is_deleted = 0
SET c.created_by = p.id, c.updated_by = p.id, c.gmt_updated = NOW()
WHERE c.account_no = '韩雨' AND c.is_deleted = 0;

UPDATE `user` c
JOIN `user` p ON p.account_no = '毕翠红' AND p.is_deleted = 0
SET c.created_by = p.id, c.updated_by = p.id, c.gmt_updated = NOW()
WHERE c.account_no = '郭瑶' AND c.is_deleted = 0;

UPDATE `user` c
JOIN `user` p ON p.account_no = '郭瑶' AND p.is_deleted = 0
SET c.created_by = p.id, c.updated_by = p.id, c.gmt_updated = NOW()
WHERE c.account_no = '韩雨' AND c.is_deleted = 0;

/* 3. Production project-access hotfix for 毕翠红 -> 郭瑶 -> 韩雨. */
DELETE stale
FROM user_project_access stale
JOIN `user` member ON member.id = stale.user_id AND member.account_no IN ('郭瑶', '韩雨') AND member.is_deleted = 0
JOIN user_project up ON up.id = stale.project_id AND up.is_deleted = 0
JOIN `user` bch ON bch.account_no = '毕翠红' AND bch.is_deleted = 0
WHERE up.user_id <> bch.id
  AND stale.is_deleted = 1;

UPDATE user_project_access upa
JOIN `user` member ON member.id = upa.user_id AND member.account_no IN ('郭瑶', '韩雨') AND member.is_deleted = 0
JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0
JOIN `user` bch ON bch.account_no = '毕翠红' AND bch.is_deleted = 0
SET upa.is_deleted = 1,
    upa.updated_by = bch.id,
    upa.gmt_updated = NOW()
WHERE up.user_id <> bch.id
  AND upa.is_deleted = 0;

UPDATE user_project_access upa
JOIN `user` member ON member.id = upa.user_id AND member.account_no IN ('郭瑶', '韩雨') AND member.is_deleted = 0
JOIN `user` bch ON bch.account_no = '毕翠红' AND bch.is_deleted = 0
JOIN user_project up ON up.id = upa.project_id
  AND up.user_id = bch.id
  AND up.project_code = 'PRJ69486'
  AND up.is_deleted = 0
SET upa.is_deleted = 0,
    upa.updated_by = bch.id,
    upa.gmt_updated = NOW();

INSERT INTO user_project_access (
  user_id, project_id, is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
  member.id,
  up.id,
  0,
  bch.id,
  bch.id,
  NOW(),
  NOW()
FROM `user` member
JOIN `user` bch ON bch.account_no = '毕翠红' AND bch.is_deleted = 0
JOIN user_project up ON up.user_id = bch.id
  AND up.project_code = 'PRJ69486'
  AND up.is_deleted = 0
WHERE member.account_no IN ('郭瑶', '韩雨')
  AND member.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM user_project_access existing
    WHERE existing.user_id = member.id
      AND existing.project_id = up.id
      AND existing.is_deleted = 0
  );

/* 4. Production 陆文欢 canman store authorization. */
DELETE stale
FROM user_project_access stale
JOIN `user` member ON member.id = stale.user_id AND member.account_no = '陆文欢' AND member.is_deleted = 0
JOIN user_project up ON up.id = stale.project_id AND up.is_deleted = 0
WHERE up.project_code <> 'PRJ108065'
  AND stale.is_deleted = 1;

UPDATE user_project_access upa
JOIN `user` member ON member.id = upa.user_id AND member.account_no = '陆文欢' AND member.is_deleted = 0
JOIN user_project up ON up.id = upa.project_id AND up.is_deleted = 0
JOIN `user` bch ON bch.account_no = '毕翠红' AND bch.is_deleted = 0
SET upa.is_deleted = 1,
    upa.updated_by = bch.id,
    upa.gmt_updated = NOW()
WHERE up.project_code <> 'PRJ108065'
  AND upa.is_deleted = 0;

INSERT INTO user_store (
  user_id, org_code, org_name, project_code, project_name, store_code, site,
  is_authorized, is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
  338,
  s.org_code,
  s.org_name,
  s.project_code,
  s.project_name,
  s.store_code,
  s.site,
  s.is_authorized,
  0,
  307,
  307,
  NOW(),
  NOW()
FROM user_store s
WHERE s.user_id = 307
  AND s.is_deleted = 0
  AND s.project_code = 'PRJ108065'
  AND s.store_code IN ('STR108065-NAE', 'STR108065-NSA')
  AND NOT EXISTS (
    SELECT 1
    FROM user_store t
    WHERE t.user_id = 338
      AND t.store_code = s.store_code
      AND t.is_deleted = 0
  );

INSERT INTO user_project_access (
  user_id, project_id, is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
  338,
  up.id,
  0,
  307,
  307,
  NOW(),
  NOW()
FROM user_project up
WHERE up.user_id = 307
  AND up.project_code = 'PRJ108065'
  AND up.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM user_project_access existing
    WHERE existing.user_id = 338
      AND existing.project_id = up.id
      AND existing.is_deleted = 0
  );

INSERT INTO user_menu (
  user_id, menu_id, status, effective_time, expired_time,
  is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
  338,
  rm.menu_id,
  1,
  COALESCE(u.effective_time, NOW()),
  COALESCE(u.expired_time, '2099-12-31 23:59:59'),
  0,
  307,
  307,
  NOW(),
  NOW()
FROM `user` u
JOIN role_menu rm ON rm.role_id = u.role_id AND rm.is_deleted = 0
WHERE u.id = 338
  AND NOT EXISTS (
    SELECT 1
    FROM user_menu existing
    WHERE existing.user_id = 338
      AND existing.menu_id = rm.menu_id
      AND existing.is_deleted = 0
  );

SET FOREIGN_KEY_CHECKS = 1;
