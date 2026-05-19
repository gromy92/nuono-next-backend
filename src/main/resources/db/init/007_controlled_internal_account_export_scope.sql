-- Controlled export scope for online internal accounts + account master data
-- Usage:
-- 1. Ensure the legacy account schema is reachable from the current MySQL session.
-- 2. Optionally set the source schema before running:
--      SET @legacy_schema = 'cross_border_erp_snapshot_20260428';
-- 3. Execute this script to inspect the exact internal-account scope before import.

SET @legacy_schema = IFNULL(@legacy_schema, 'cross_border_erp_snapshot_20260428');

DROP TEMPORARY TABLE IF EXISTS tmp_legacy_internal_accounts;

SET @create_internal_scope_sql = CONCAT(
    'CREATE TEMPORARY TABLE tmp_legacy_internal_accounts AS ',
    'SELECT ',
    '  u.id, u.account_no, u.phone, u.email, u.real_name, u.company_name, ',
    '  u.password, u.role, u.role_id, u.level, u.account_type, u.status, ',
    '  u.effective_time, u.expired_time, ',
    '  u.noon_partner_user, u.noon_partner_project_user, u.noon_partner_id, ',
    '  u.noon_partner_user_code, u.noon_partner_mail_auth_code, u.cookie_generate_time ',
    'FROM `', @legacy_schema, '`.`user` u ',
    'WHERE u.is_deleted = 0 ',
    '  AND u.account_type = ''internal'''
);
PREPARE create_internal_scope_stmt FROM @create_internal_scope_sql;
EXECUTE create_internal_scope_stmt;
DEALLOCATE PREPARE create_internal_scope_stmt;

SELECT
    COUNT(*) AS internal_account_total,
    SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS enabled_account_total,
    SUM(CASE WHEN status <> 1 THEN 1 ELSE 0 END) AS disabled_account_total,
    SUM(CASE WHEN effective_time IS NOT NULL AND effective_time > NOW() THEN 1 ELSE 0 END) AS future_effective_total,
    SUM(CASE WHEN expired_time IS NOT NULL AND expired_time < NOW() THEN 1 ELSE 0 END) AS expired_total,
    SUM(CASE WHEN noon_partner_project_user IS NOT NULL AND LENGTH(TRIM(noon_partner_project_user)) > 0 THEN 1 ELSE 0 END)
        AS project_bound_total,
    SUM(CASE WHEN noon_partner_user IS NOT NULL AND LENGTH(TRIM(noon_partner_user)) > 0 THEN 1 ELSE 0 END)
        AS noon_account_bound_total
FROM tmp_legacy_internal_accounts;

SELECT
    role_id,
    role,
    COUNT(*) AS internal_user_count
FROM tmp_legacy_internal_accounts
GROUP BY role_id, role
ORDER BY internal_user_count DESC, role_id ASC;

SET @role_count_sql = CONCAT(
    'SELECT COUNT(*) AS effective_role_total ',
    'FROM `', @legacy_schema, '`.`role` r ',
    'WHERE r.is_deleted = 0'
);
PREPARE role_count_stmt FROM @role_count_sql;
EXECUTE role_count_stmt;
DEALLOCATE PREPARE role_count_stmt;

SET @menu_count_sql = CONCAT(
    'SELECT COUNT(*) AS effective_menu_total ',
    'FROM `', @legacy_schema, '`.`menu` m ',
    'WHERE m.is_deleted = 0'
);
PREPARE menu_count_stmt FROM @menu_count_sql;
EXECUTE menu_count_stmt;
DEALLOCATE PREPARE menu_count_stmt;

SET @role_menu_count_sql = CONCAT(
    'SELECT COUNT(*) AS effective_role_menu_total ',
    'FROM `', @legacy_schema, '`.`role_menu` rm ',
    'WHERE rm.is_deleted = 0'
);
PREPARE role_menu_count_stmt FROM @role_menu_count_sql;
EXECUTE role_menu_count_stmt;
DEALLOCATE PREPARE role_menu_count_stmt;

SET @user_menu_count_sql = CONCAT(
    'SELECT COUNT(*) AS internal_user_menu_total ',
    'FROM `', @legacy_schema, '`.`user_menu` um ',
    'WHERE um.is_deleted = 0 ',
    '  AND EXISTS (',
    '    SELECT 1 FROM `', @legacy_schema, '`.`user` u ',
    '    WHERE u.id = um.user_id AND u.is_deleted = 0 AND u.account_type = ''internal''',
    '  )'
);
PREPARE user_menu_count_stmt FROM @user_menu_count_sql;
EXECUTE user_menu_count_stmt;
DEALLOCATE PREPARE user_menu_count_stmt;

SET @user_store_count_sql = CONCAT(
    'SELECT COUNT(*) AS internal_user_store_total ',
    'FROM `', @legacy_schema, '`.`user_store` us ',
    'WHERE us.is_deleted = 0 ',
    '  AND EXISTS (',
    '    SELECT 1 FROM `', @legacy_schema, '`.`user` u ',
    '    WHERE u.id = us.user_id AND u.is_deleted = 0 AND u.account_type = ''internal''',
    '  )'
);
PREPARE user_store_count_stmt FROM @user_store_count_sql;
EXECUTE user_store_count_stmt;
DEALLOCATE PREPARE user_store_count_stmt;

SELECT
    id,
    account_no,
    real_name,
    role_id,
    role,
    status,
    effective_time,
    expired_time,
    CASE
        WHEN noon_partner_project_user IS NOT NULL AND LENGTH(TRIM(noon_partner_project_user)) > 0 THEN 'PROJECT_BOUND'
        WHEN noon_partner_user IS NOT NULL AND LENGTH(TRIM(noon_partner_user)) > 0 THEN 'ACCOUNT_ONLY'
        ELSE 'UNBOUND'
    END AS binding_status
FROM tmp_legacy_internal_accounts
ORDER BY id ASC
LIMIT 50;
