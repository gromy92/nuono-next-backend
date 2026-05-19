-- Clear Noon project credentials that were derived from the deprecated legacy
-- `user` table before migration source was corrected to latest user_project.
-- This script only touches rows whose credential values exactly match the old
-- user-table fields and whose project code matches that old user's partner id.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET NAMES utf8mb4;

UPDATE user_project up
JOIN `user` un ON un.id = up.user_id AND un.is_deleted = 0
JOIN role r ON r.id = un.role_id AND r.is_deleted = 0 AND r.level = 1
JOIN `user` legacy ON legacy.id = up.user_id AND legacy.is_deleted = 0
SET
  up.noon_partner_user = NULL,
  up.noon_partner_project_user = NULL,
  up.noon_partner_pwd = NULL,
  up.noon_partner_encrypted_pwd = NULL,
  up.noon_partner_user_code = NULL,
  up.noon_partner_mail_auth_code = NULL,
  up.noon_partner_cookie = NULL,
  up.cookie_generate_time = NULL,
  up.bind_status = 0,
  up.updated_by = up.user_id,
  up.gmt_updated = NOW()
WHERE up.is_deleted = 0
  AND up.project_code = CONCAT('PRJ', NULLIF(legacy.noon_partner_id, ''))
  AND (
    (NULLIF(up.noon_partner_project_user, '') IS NOT NULL AND up.noon_partner_project_user <=> legacy.noon_partner_project_user)
    OR (NULLIF(up.noon_partner_user, '') IS NOT NULL AND up.noon_partner_user <=> legacy.noon_partner_user)
    OR (NULLIF(up.noon_partner_cookie, '') IS NOT NULL AND up.noon_partner_cookie <=> legacy.noon_partner_cookie)
  );
