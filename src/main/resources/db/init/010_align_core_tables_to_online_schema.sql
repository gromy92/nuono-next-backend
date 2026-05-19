-- Align nuono_new_dev core master-data tables to the verified online legacy schema.
-- Verified baseline:
-- - Online production schema: cross_border_erp
-- - Local reference snapshot: cross_border_erp_snapshot_20260428
--
-- New-system local data does not need to be backed up for this migration.
-- This script rebuilds the six legacy core tables and imports rows directly from the
-- verified legacy snapshot.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

DROP TABLE IF EXISTS `user_store`;
DROP TABLE IF EXISTS `user_menu`;
DROP TABLE IF EXISTS `role_menu`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `menu`;
DROP TABLE IF EXISTS `role`;

CREATE TABLE `role` LIKE cross_border_erp_snapshot_20260428.`role`;
CREATE TABLE `menu` LIKE cross_border_erp_snapshot_20260428.`menu`;
CREATE TABLE `role_menu` LIKE cross_border_erp_snapshot_20260428.`role_menu`;
CREATE TABLE `user` LIKE cross_border_erp_snapshot_20260428.`user`;
CREATE TABLE `user_menu` LIKE cross_border_erp_snapshot_20260428.`user_menu`;
CREATE TABLE `user_store` LIKE cross_border_erp_snapshot_20260428.`user_store`;

ALTER TABLE `user`
    MODIFY `phone` VARCHAR(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    MODIFY `email` VARCHAR(125) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    MODIFY `account_type` VARCHAR(10) COLLATE utf8mb4_unicode_ci DEFAULT 'external' COMMENT '账号类型：internal/external',
    MODIFY `real_name` VARCHAR(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '真实姓名';

INSERT INTO `role`
SELECT * FROM cross_border_erp_snapshot_20260428.`role`;

INSERT INTO `menu`
SELECT * FROM cross_border_erp_snapshot_20260428.`menu`;

INSERT INTO `role_menu`
SELECT * FROM cross_border_erp_snapshot_20260428.`role_menu`;

INSERT INTO `user`
SELECT * FROM cross_border_erp_snapshot_20260428.`user`;

INSERT INTO `user_menu`
SELECT * FROM cross_border_erp_snapshot_20260428.`user_menu`;

INSERT INTO `user_store`
SELECT * FROM cross_border_erp_snapshot_20260428.`user_store`;
