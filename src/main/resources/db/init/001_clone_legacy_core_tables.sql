-- Nuono Next local database bootstrap v1
-- Purpose:
-- 1. Initialize the first-batch core tables inside nuono_new_dev
-- 2. Reuse verified legacy structures as the starting point
-- 3. Keep the old system as the read-only reference source

-- Prerequisite:
-- Replace `cross_border_erp_snapshot_20260428` below if your legacy reference schema uses a different name.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

CREATE TABLE IF NOT EXISTS role LIKE cross_border_erp_snapshot_20260428.role;
CREATE TABLE IF NOT EXISTS menu LIKE cross_border_erp_snapshot_20260428.menu;
CREATE TABLE IF NOT EXISTS role_menu LIKE cross_border_erp_snapshot_20260428.role_menu;
CREATE TABLE IF NOT EXISTS `user` LIKE cross_border_erp_snapshot_20260428.user;
CREATE TABLE IF NOT EXISTS user_menu LIKE cross_border_erp_snapshot_20260428.user_menu;
CREATE TABLE IF NOT EXISTS user_store LIKE cross_border_erp_snapshot_20260428.user_store;
