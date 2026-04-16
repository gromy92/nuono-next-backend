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

INSERT INTO `user`
SELECT *
FROM cross_border_erp.user
WHERE is_deleted = 0
  AND account_no IN (
    '18521524250',
    '毕翠红',
    '马天龙',
    'xingyaoqw',
    '18660614134',
    '15812516142'
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
