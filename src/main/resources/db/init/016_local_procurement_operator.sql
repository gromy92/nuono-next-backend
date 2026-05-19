-- Local procurement operator used by the procurement requirement confirmation page
-- when it is opened without a persisted login session.

INSERT INTO `user` (
  id, phone, email, account_no, `password`, token, `role`, role_id, account_type,
  real_name, company_name, `level`, status, is_deleted, created_by, updated_by,
  gmt_create, gmt_updated
)
SELECT
  90001, NULL, NULL, 'procurement.demo', 'procurement123', NULL, '采购', 5, 'internal',
  '采购演示账号', 'Nuono Demo', 3, 1, 0, 1, 1, NOW(), NOW()
WHERE EXISTS (
  SELECT 1
  FROM role
  WHERE id = 5
    AND is_deleted = 0
)
ON DUPLICATE KEY UPDATE
  account_no = VALUES(account_no),
  `role` = VALUES(`role`),
  role_id = VALUES(role_id),
  account_type = VALUES(account_type),
  real_name = VALUES(real_name),
  company_name = VALUES(company_name),
  `level` = VALUES(`level`),
  status = VALUES(status),
  is_deleted = VALUES(is_deleted),
  updated_by = VALUES(updated_by),
  gmt_updated = NOW();
