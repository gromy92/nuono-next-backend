-- Restore the local xingyao product-management validation owner.
--
-- Product-management regression uses owner user 10002 with PRJ245027.
-- Later project-store model imports can leave this user_project row without
-- Noon credentials while keeping an old READY product projection. That state
-- lets list/read paths pass but breaks init-preflight/init-start repeatability.

USE nuono_new_dev;

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
