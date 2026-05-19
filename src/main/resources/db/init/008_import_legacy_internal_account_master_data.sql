-- Controlled import for online internal accounts + account master data
-- Usage:
-- 1. Ensure the legacy schema is reachable in the same MySQL session.
-- 2. Optional overrides:
--      SET @legacy_schema = 'cross_border_erp_snapshot_20260428';
--      SET @target_schema = 'nuono_new_dev';
-- 3. Execute after reviewing 007_controlled_internal_account_export_scope.sql.
-- Notes:
-- - This script only imports account master data required by the first-batch master-data pages.
-- - It does not import task center / message center / workbench / historical business data.

SET @legacy_schema = IFNULL(@legacy_schema, 'cross_border_erp_snapshot_20260428');
SET @target_schema = IFNULL(@target_schema, 'nuono_new_dev');

DROP TEMPORARY TABLE IF EXISTS tmp_legacy_internal_accounts;
DROP TEMPORARY TABLE IF EXISTS tmp_legacy_internal_user_menu_ids;
DROP TEMPORARY TABLE IF EXISTS tmp_legacy_internal_user_store_ids;
DROP TEMPORARY TABLE IF EXISTS tmp_legacy_internal_account_conflicts;

SET @create_internal_scope_sql = CONCAT(
    'CREATE TEMPORARY TABLE tmp_legacy_internal_accounts AS ',
    'SELECT ',
    '  u.id, u.phone, u.email, u.account_no, u.password, ',
    '  u.role, u.role_id, u.account_type, u.real_name, u.company_name, u.level, ',
    '  u.noon_partner_user, u.noon_partner_project_user, u.noon_partner_id, ',
    '  u.noon_partner_user_code, u.noon_partner_mail_auth_code, u.cookie_generate_time, ',
    '  u.status, u.effective_time, u.expired_time, u.is_deleted, ',
    '  u.created_by, u.updated_by, u.gmt_create, u.gmt_updated ',
    'FROM `', @legacy_schema, '`.`user` u ',
    'WHERE u.is_deleted = 0 ',
    '  AND u.account_type = ''internal'''
);
PREPARE create_internal_scope_stmt FROM @create_internal_scope_sql;
EXECUTE create_internal_scope_stmt;
DEALLOCATE PREPARE create_internal_scope_stmt;

SET @create_internal_user_menu_scope_sql = CONCAT(
    'CREATE TEMPORARY TABLE tmp_legacy_internal_user_menu_ids AS ',
    'SELECT um.id ',
    'FROM `', @legacy_schema, '`.`user_menu` um ',
    'INNER JOIN tmp_legacy_internal_accounts u ON u.id = um.user_id ',
    'WHERE um.is_deleted = 0'
);
PREPARE create_internal_user_menu_scope_stmt FROM @create_internal_user_menu_scope_sql;
EXECUTE create_internal_user_menu_scope_stmt;
DEALLOCATE PREPARE create_internal_user_menu_scope_stmt;

SET @create_internal_user_store_scope_sql = CONCAT(
    'CREATE TEMPORARY TABLE tmp_legacy_internal_user_store_ids AS ',
    'SELECT us.id ',
    'FROM `', @legacy_schema, '`.`user_store` us ',
    'INNER JOIN tmp_legacy_internal_accounts u ON u.id = us.user_id ',
    'WHERE us.is_deleted = 0'
);
PREPARE create_internal_user_store_scope_stmt FROM @create_internal_user_store_scope_sql;
EXECUTE create_internal_user_store_scope_stmt;
DEALLOCATE PREPARE create_internal_user_store_scope_stmt;

SET @create_internal_account_conflicts_sql = CONCAT(
    'CREATE TEMPORARY TABLE tmp_legacy_internal_account_conflicts AS ',
    'SELECT target_u.id AS target_user_id, src_u.id AS source_user_id, src_u.account_no ',
    'FROM `', @target_schema, '`.`user` target_u ',
    'INNER JOIN tmp_legacy_internal_accounts src_u ON ',
    '  CONVERT(src_u.account_no USING utf8mb4) COLLATE utf8mb4_unicode_ci = ',
    '  CONVERT(target_u.account_no USING utf8mb4) COLLATE utf8mb4_unicode_ci ',
    'WHERE target_u.account_type = ''internal'' ',
    '  AND target_u.id <> src_u.id'
);
PREPARE create_internal_account_conflicts_stmt FROM @create_internal_account_conflicts_sql;
EXECUTE create_internal_account_conflicts_stmt;
DEALLOCATE PREPARE create_internal_account_conflicts_stmt;

SET @delete_conflict_user_menus_sql = CONCAT(
    'DELETE um ',
    'FROM `', @target_schema, '`.`user_menu` um ',
    'INNER JOIN tmp_legacy_internal_account_conflicts conflict ON conflict.target_user_id = um.user_id'
);
PREPARE delete_conflict_user_menus_stmt FROM @delete_conflict_user_menus_sql;
EXECUTE delete_conflict_user_menus_stmt;
DEALLOCATE PREPARE delete_conflict_user_menus_stmt;

SET @delete_conflict_user_stores_sql = CONCAT(
    'DELETE us ',
    'FROM `', @target_schema, '`.`user_store` us ',
    'INNER JOIN tmp_legacy_internal_account_conflicts conflict ON conflict.target_user_id = us.user_id'
);
PREPARE delete_conflict_user_stores_stmt FROM @delete_conflict_user_stores_sql;
EXECUTE delete_conflict_user_stores_stmt;
DEALLOCATE PREPARE delete_conflict_user_stores_stmt;

SET @delete_conflict_users_sql = CONCAT(
    'DELETE u ',
    'FROM `', @target_schema, '`.`user` u ',
    'INNER JOIN tmp_legacy_internal_account_conflicts conflict ON conflict.target_user_id = u.id'
);
PREPARE delete_conflict_users_stmt FROM @delete_conflict_users_sql;
EXECUTE delete_conflict_users_stmt;
DEALLOCATE PREPARE delete_conflict_users_stmt;

SET @insert_roles_sql = CONCAT(
    'INSERT INTO `', @target_schema, '`.`role` ',
    '(`id`,`name`,`code`,`description`,`is_system`,`parent_id`,`level`,`is_deleted`,`created_by`,`updated_by`,`gmt_create`,`gmt_updated`) ',
    'SELECT ',
    '  r.id, r.name, r.code, r.description, r.is_system, r.parent_id, r.level, r.is_deleted, ',
    '  r.created_by, r.updated_by, r.gmt_create, r.gmt_updated ',
    'FROM `', @legacy_schema, '`.`role` r ',
    'WHERE r.is_deleted = 0 ',
    'ON DUPLICATE KEY UPDATE ',
    '  `name` = VALUES(`name`), ',
    '  `code` = VALUES(`code`), ',
    '  `description` = VALUES(`description`), ',
    '  `is_system` = VALUES(`is_system`), ',
    '  `parent_id` = VALUES(`parent_id`), ',
    '  `level` = VALUES(`level`), ',
    '  `is_deleted` = VALUES(`is_deleted`), ',
    '  `updated_by` = VALUES(`updated_by`), ',
    '  `gmt_updated` = VALUES(`gmt_updated`)'
);
PREPARE insert_roles_stmt FROM @insert_roles_sql;
EXECUTE insert_roles_stmt;
DEALLOCATE PREPARE insert_roles_stmt;

SET @insert_menus_sql = CONCAT(
    'INSERT INTO `', @target_schema, '`.`menu` ',
    '(`id`,`name`,`parent_id`,`url_path`,`is_deleted`,`gmt_create`,`gmt_updated`) ',
    'SELECT m.id, m.name, m.parent_id, m.url_path, m.is_deleted, m.gmt_create, m.gmt_updated ',
    'FROM `', @legacy_schema, '`.`menu` m ',
    'WHERE m.is_deleted = 0 ',
    'ON DUPLICATE KEY UPDATE ',
    '  `name` = VALUES(`name`), ',
    '  `parent_id` = VALUES(`parent_id`), ',
    '  `url_path` = VALUES(`url_path`), ',
    '  `is_deleted` = VALUES(`is_deleted`), ',
    '  `gmt_updated` = VALUES(`gmt_updated`)'
);
PREPARE insert_menus_stmt FROM @insert_menus_sql;
EXECUTE insert_menus_stmt;
DEALLOCATE PREPARE insert_menus_stmt;

SET @insert_role_menus_sql = CONCAT(
    'INSERT INTO `', @target_schema, '`.`role_menu` ',
    '(`id`,`role_id`,`menu_id`,`is_deleted`,`gmt_create`,`gmt_updated`) ',
    'SELECT rm.id, rm.role_id, rm.menu_id, rm.is_deleted, rm.gmt_create, rm.gmt_updated ',
    'FROM `', @legacy_schema, '`.`role_menu` rm ',
    'WHERE rm.is_deleted = 0 ',
    'ON DUPLICATE KEY UPDATE ',
    '  `role_id` = VALUES(`role_id`), ',
    '  `menu_id` = VALUES(`menu_id`), ',
    '  `is_deleted` = VALUES(`is_deleted`), ',
    '  `gmt_updated` = VALUES(`gmt_updated`)'
);
PREPARE insert_role_menus_stmt FROM @insert_role_menus_sql;
EXECUTE insert_role_menus_stmt;
DEALLOCATE PREPARE insert_role_menus_stmt;

SET @insert_users_sql = CONCAT(
    'INSERT INTO `', @target_schema, '`.`user` ',
    '(`id`,`phone`,`email`,`account_no`,`password`,`role`,`role_id`,`account_type`,`real_name`,`company_name`,`level`,',
    '`noon_partner_user`,`noon_partner_project_user`,`noon_partner_id`,`noon_partner_user_code`,`noon_partner_mail_auth_code`,`cookie_generate_time`,',
    '`status`,`effective_time`,`expired_time`,`is_deleted`,`created_by`,`updated_by`,`gmt_create`,`gmt_updated`) ',
    'SELECT ',
    '  u.id, u.phone, u.email, u.account_no, u.password, u.role, u.role_id, u.account_type, u.real_name, u.company_name, u.level, ',
    '  u.noon_partner_user, u.noon_partner_project_user, u.noon_partner_id, u.noon_partner_user_code, u.noon_partner_mail_auth_code, u.cookie_generate_time, ',
    '  u.status, u.effective_time, u.expired_time, u.is_deleted, u.created_by, u.updated_by, u.gmt_create, u.gmt_updated ',
    'FROM tmp_legacy_internal_accounts u ',
    'ON DUPLICATE KEY UPDATE ',
    '  `phone` = VALUES(`phone`), ',
    '  `email` = VALUES(`email`), ',
    '  `password` = VALUES(`password`), ',
    '  `role` = VALUES(`role`), ',
    '  `role_id` = VALUES(`role_id`), ',
    '  `account_type` = VALUES(`account_type`), ',
    '  `real_name` = VALUES(`real_name`), ',
    '  `company_name` = VALUES(`company_name`), ',
    '  `level` = VALUES(`level`), ',
    '  `noon_partner_user` = VALUES(`noon_partner_user`), ',
    '  `noon_partner_project_user` = VALUES(`noon_partner_project_user`), ',
    '  `noon_partner_id` = VALUES(`noon_partner_id`), ',
    '  `noon_partner_user_code` = VALUES(`noon_partner_user_code`), ',
    '  `noon_partner_mail_auth_code` = VALUES(`noon_partner_mail_auth_code`), ',
    '  `cookie_generate_time` = VALUES(`cookie_generate_time`), ',
    '  `status` = VALUES(`status`), ',
    '  `effective_time` = VALUES(`effective_time`), ',
    '  `expired_time` = VALUES(`expired_time`), ',
    '  `is_deleted` = VALUES(`is_deleted`), ',
    '  `updated_by` = VALUES(`updated_by`), ',
    '  `gmt_updated` = VALUES(`gmt_updated`)'
);
PREPARE insert_users_stmt FROM @insert_users_sql;
EXECUTE insert_users_stmt;
DEALLOCATE PREPARE insert_users_stmt;

SET @soft_delete_outdated_users_sql = CONCAT(
    'UPDATE `', @target_schema, '`.`user` u ',
    'LEFT JOIN tmp_legacy_internal_accounts src ON src.id = u.id ',
    'SET u.is_deleted = b''1'', ',
    '    u.status = 0, ',
    '    u.updated_by = 0, ',
    '    u.gmt_updated = NOW() ',
    'WHERE u.account_type = ''internal'' ',
    '  AND src.id IS NULL'
);
PREPARE soft_delete_outdated_users_stmt FROM @soft_delete_outdated_users_sql;
EXECUTE soft_delete_outdated_users_stmt;
DEALLOCATE PREPARE soft_delete_outdated_users_stmt;

SET @insert_user_menus_sql = CONCAT(
    'INSERT INTO `', @target_schema, '`.`user_menu` ',
    '(`id`,`user_id`,`menu_id`,`status`,`effective_time`,`expired_time`,`is_deleted`,`gmt_create`,`gmt_updated`) ',
    'SELECT um.id, um.user_id, um.menu_id, um.status, um.effective_time, um.expired_time, um.is_deleted, um.gmt_create, um.gmt_updated ',
    'FROM `', @legacy_schema, '`.`user_menu` um ',
    'INNER JOIN tmp_legacy_internal_accounts u ON u.id = um.user_id ',
    'WHERE um.is_deleted = 0 ',
    'ON DUPLICATE KEY UPDATE ',
    '  `user_id` = VALUES(`user_id`), ',
    '  `menu_id` = VALUES(`menu_id`), ',
    '  `status` = VALUES(`status`), ',
    '  `effective_time` = VALUES(`effective_time`), ',
    '  `expired_time` = VALUES(`expired_time`), ',
    '  `is_deleted` = VALUES(`is_deleted`), ',
    '  `gmt_updated` = VALUES(`gmt_updated`)'
);
PREPARE insert_user_menus_stmt FROM @insert_user_menus_sql;
EXECUTE insert_user_menus_stmt;
DEALLOCATE PREPARE insert_user_menus_stmt;

SET @soft_delete_outdated_user_menus_sql = CONCAT(
    'UPDATE `', @target_schema, '`.`user_menu` um ',
    'INNER JOIN `', @target_schema, '`.`user` u ON u.id = um.user_id AND u.account_type = ''internal'' ',
    'LEFT JOIN tmp_legacy_internal_user_menu_ids src ON src.id = um.id ',
    'SET um.is_deleted = b''1'', ',
    '    um.gmt_updated = NOW() ',
    'WHERE src.id IS NULL'
);
PREPARE soft_delete_outdated_user_menus_stmt FROM @soft_delete_outdated_user_menus_sql;
EXECUTE soft_delete_outdated_user_menus_stmt;
DEALLOCATE PREPARE soft_delete_outdated_user_menus_stmt;

SET @insert_user_stores_sql = CONCAT(
    'INSERT INTO `', @target_schema, '`.`user_store` ',
    '(`id`,`user_id`,`org_code`,`org_name`,`project_code`,`project_name`,`store_code`,`site`,`is_authorized`,`is_deleted`,`created_by`,`updated_by`,`gmt_create`,`gmt_updated`) ',
    'SELECT ',
    '  us.id, us.user_id, us.org_code, us.org_name, us.project_code, us.project_name, us.store_code, us.site, ',
    '  us.is_authorized, us.is_deleted, us.created_by, us.updated_by, us.gmt_create, us.gmt_updated ',
    'FROM `', @legacy_schema, '`.`user_store` us ',
    'INNER JOIN tmp_legacy_internal_accounts u ON u.id = us.user_id ',
    'WHERE us.is_deleted = 0 ',
    'ON DUPLICATE KEY UPDATE ',
    '  `org_code` = VALUES(`org_code`), ',
    '  `org_name` = VALUES(`org_name`), ',
    '  `project_code` = VALUES(`project_code`), ',
    '  `project_name` = VALUES(`project_name`), ',
    '  `store_code` = VALUES(`store_code`), ',
    '  `site` = VALUES(`site`), ',
    '  `is_authorized` = VALUES(`is_authorized`), ',
    '  `is_deleted` = VALUES(`is_deleted`), ',
    '  `updated_by` = VALUES(`updated_by`), ',
    '  `gmt_updated` = VALUES(`gmt_updated`)'
);
PREPARE insert_user_stores_stmt FROM @insert_user_stores_sql;
EXECUTE insert_user_stores_stmt;
DEALLOCATE PREPARE insert_user_stores_stmt;

SET @soft_delete_outdated_user_stores_sql = CONCAT(
    'UPDATE `', @target_schema, '`.`user_store` us ',
    'INNER JOIN `', @target_schema, '`.`user` u ON u.id = us.user_id AND u.account_type = ''internal'' ',
    'LEFT JOIN tmp_legacy_internal_user_store_ids src ON src.id = us.id ',
    'SET us.is_deleted = b''1'', ',
    '    us.gmt_updated = NOW() ',
    'WHERE src.id IS NULL'
);
PREPARE soft_delete_outdated_user_stores_stmt FROM @soft_delete_outdated_user_stores_sql;
EXECUTE soft_delete_outdated_user_stores_stmt;
DEALLOCATE PREPARE soft_delete_outdated_user_stores_stmt;

SET @verify_users_sql = CONCAT(
    'SELECT COUNT(*) AS imported_internal_user_total ',
    'FROM `', @target_schema, '`.`user` u ',
    'WHERE u.is_deleted = 0 AND u.account_type = ''internal'''
);
PREPARE verify_users_stmt FROM @verify_users_sql;
EXECUTE verify_users_stmt;
DEALLOCATE PREPARE verify_users_stmt;

SET @verify_user_store_sql = CONCAT(
    'SELECT COUNT(*) AS imported_user_store_total ',
    'FROM `', @target_schema, '`.`user_store` us ',
    'WHERE us.is_deleted = 0 ',
    '  AND EXISTS (SELECT 1 FROM `', @target_schema, '`.`user` u WHERE u.id = us.user_id AND u.account_type = ''internal'' AND u.is_deleted = 0)'
);
PREPARE verify_user_store_stmt FROM @verify_user_store_sql;
EXECUTE verify_user_store_stmt;
DEALLOCATE PREPARE verify_user_store_stmt;
