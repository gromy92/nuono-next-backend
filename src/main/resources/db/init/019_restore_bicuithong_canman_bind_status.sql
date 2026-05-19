-- Keep the local acceptance baseline aligned with verified production
-- user_project data: Bi Cuihong's canman project is a bound project row.
-- The real Noon material for this project may fail a live re-test, so UI/e2e
-- tests should not use it as the mutable connection-test sample.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET NAMES utf8mb4;

UPDATE user_project
SET
  bind_status = 1,
  gmt_updated = NOW()
WHERE user_id = 307
  AND project_code = 'PRJ108065'
  AND is_deleted = 0
  AND NULLIF(noon_partner_cookie, '') IS NOT NULL;
