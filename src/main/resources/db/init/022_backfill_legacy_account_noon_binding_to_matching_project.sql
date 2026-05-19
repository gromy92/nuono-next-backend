-- Backfill legacy account-level Noon binding into the matching derived project row.
--
-- The 20260428 local snapshot does not contain production user_project rows, so
-- 014 derives user_project from user_store and 017 clears credentials that came
-- from the deprecated user table. For acceptance, keep the old system's visible
-- bound state only when the legacy account partner id maps to the exact derived
-- project code (project_code = PRJ + noon_partner_id). Other projects under the
-- same account remain unbound until real project-level binding exists.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET NAMES utf8mb4;

UPDATE user_project up
JOIN `user` legacy ON legacy.id = up.user_id AND legacy.is_deleted = 0
SET
  up.noon_partner_user = NULLIF(legacy.noon_partner_user, ''),
  up.noon_partner_project_user = NULLIF(legacy.noon_partner_project_user, ''),
  up.noon_partner_pwd = NULLIF(legacy.noon_partner_pwd, ''),
  up.noon_partner_encrypted_pwd = NULLIF(legacy.noon_partner_encrypted_pwd, ''),
  up.noon_partner_id = NULLIF(legacy.noon_partner_id, ''),
  up.noon_partner_user_code = NULLIF(legacy.noon_partner_user_code, ''),
  up.noon_partner_mail_auth_code = NULLIF(legacy.noon_partner_mail_auth_code, ''),
  up.noon_partner_cookie = NULLIF(legacy.noon_partner_cookie, ''),
  up.cookie_generate_time = legacy.cookie_generate_time,
  up.bind_status = CASE
    WHEN NULLIF(legacy.noon_partner_user, '') IS NOT NULL
      OR NULLIF(legacy.noon_partner_project_user, '') IS NOT NULL
      OR NULLIF(legacy.noon_partner_cookie, '') IS NOT NULL
    THEN 1
    ELSE up.bind_status
  END,
  up.updated_by = up.user_id,
  up.gmt_updated = NOW()
WHERE up.is_deleted = 0
  AND NULLIF(legacy.noon_partner_id, '') IS NOT NULL
  AND up.project_code = CONCAT('PRJ', legacy.noon_partner_id);
