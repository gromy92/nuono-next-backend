-- Align local role-menu permissions with latest verified online legacy data.
-- Online cross_border_erp has role_id=1/menu_id=25 soft-deleted, leaving
-- 89 active role_menu rows. Keep local rebuilds on the same permission source.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET NAMES utf8mb4;

UPDATE role_menu
SET
  is_deleted = b'1',
  gmt_updated = NOW()
WHERE role_id = 1
  AND menu_id = 25
  AND is_deleted = b'0';
