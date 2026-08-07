-- Migration 251: allow an immutable owner-scoped successor to drain ahead of a mixed successor.
SET NAMES utf8mb4;

SET @dp251_scope_owner_count := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='noon_auth_identity_recovery'
    AND column_name='scope_owner_user_id');
SET @dp251_scoped_slot_count := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='noon_auth_identity_recovery'
    AND column_name='scoped_successor_slot');
SET @dp251_old_successor_shape := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='noon_auth_identity_recovery'
    AND column_name='successor_identity_slot'
    AND LOCATE('status',LOWER(generation_expression))>0
    AND LOCATE('waiting_predecessor',LOWER(generation_expression))>0
    AND LOCATE('scope_owner_user_id',LOWER(generation_expression))=0);
SET @dp251_current_successor_shape := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='noon_auth_identity_recovery'
    AND column_name='successor_identity_slot'
    AND LOCATE('waiting_predecessor',LOWER(generation_expression))>0
    AND LOCATE('scope_owner_user_id',LOWER(generation_expression))>0
    AND LOCATE('is null',LOWER(generation_expression))>0);
SET @dp251_shape_sql := IF(
  @dp251_scope_owner_count=0 AND @dp251_scoped_slot_count=0 AND @dp251_old_successor_shape=1,
  'ALTER TABLE `noon_auth_identity_recovery`
     DROP INDEX `uk_noon_auth_identity_recovery_successor`,
     DROP COLUMN `successor_identity_slot`,
     ADD COLUMN `scope_owner_user_id` BIGINT DEFAULT NULL AFTER `status`,
     ADD COLUMN `successor_identity_slot` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN `status`=''WAITING_PREDECESSOR'' AND `scope_owner_user_id` IS NULL THEN `identity_key` ELSE NULL END) STORED AFTER `active_identity_slot`,
     ADD COLUMN `scoped_successor_slot` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN `status`=''WAITING_PREDECESSOR'' AND `scope_owner_user_id` IS NOT NULL THEN SHA2(CONCAT(`identity_key`, '':'' , `scope_owner_user_id`), 256) ELSE NULL END) STORED AFTER `successor_identity_slot`,
     ADD UNIQUE KEY `uk_noon_auth_identity_recovery_successor` (`successor_identity_slot`),
     ADD UNIQUE KEY `uk_noon_auth_recovery_scoped_successor` (`scoped_successor_slot`),
     ADD KEY `idx_noon_auth_recovery_scope_owner` (`scope_owner_user_id`, `status`, `id`)',
  IF(@dp251_scope_owner_count=1 AND @dp251_scoped_slot_count=1 AND @dp251_current_successor_shape=1,
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''DP251_RECOVERY_PREDECESSOR_DRIFT'''
  )
);
PREPARE dp251_shape_stmt FROM @dp251_shape_sql;
EXECUTE dp251_shape_stmt;
DEALLOCATE PREPARE dp251_shape_stmt;

CREATE TABLE IF NOT EXISTS `noon_auth_owner_scope_manifest` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `manifest_key` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `identity_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `predecessor_recovery_id` BIGINT NOT NULL,
  `source_recovery_id` BIGINT NOT NULL,
  `scoped_recovery_id` BIGINT NOT NULL,
  `predecessor_recovery_version` BIGINT NOT NULL,
  `source_recovery_version` BIGINT NOT NULL,
  `source_generation_no` INT NOT NULL,
  `source_send_budget_epoch` INT NOT NULL,
  `source_send_attempt_count` INT NOT NULL,
  `source_remaining_item_count` INT NOT NULL,
  `source_remaining_project_count` INT NOT NULL,
  `source_remaining_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `manifest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `item_count` INT NOT NULL,
  `project_count` INT NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  `active_identity_slot` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
    GENERATED ALWAYS AS (CASE WHEN `status`='ACTIVE' THEN `identity_key` ELSE NULL END) STORED,
  `created_by` VARCHAR(100) NOT NULL,
  `status_reason` VARCHAR(500) DEFAULT NULL,
  `version_no` BIGINT NOT NULL DEFAULT 0,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_noon_auth_owner_scope_manifest_key` (`manifest_key`),
  UNIQUE KEY `uk_noon_auth_owner_scope_manifest_active` (`active_identity_slot`),
  UNIQUE KEY `uk_noon_auth_owner_scope_manifest_recovery` (`scoped_recovery_id`),
  KEY `idx_noon_auth_owner_scope_source` (`source_recovery_id`, `status`),
  CONSTRAINT `chk_noon_auth_owner_scope_counts` CHECK (`item_count`>0 AND `project_count`>0),
  CONSTRAINT `chk_noon_auth_owner_scope_status` CHECK (`status` IN ('ACTIVE','RELEASED','ROLLED_BACK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `noon_auth_owner_scope_manifest_item` (
  `manifest_id` BIGINT NOT NULL,
  `source_item_id` BIGINT NOT NULL,
  `selected_for_scope` BIT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `project_code` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `store_code` VARCHAR(100) DEFAULT NULL,
  `site_code` VARCHAR(32) DEFAULT NULL,
  `source_task_id` BIGINT DEFAULT NULL,
  `source_domain` VARCHAR(64) DEFAULT NULL,
  `source_checkpoint` VARCHAR(64) DEFAULT NULL,
  `resume_policy` VARCHAR(32) NOT NULL,
  `expected_auth_version` BIGINT NOT NULL,
  `item_status` VARCHAR(24) NOT NULL,
  `item_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`manifest_id`, `source_item_id`),
  KEY `idx_noon_auth_owner_scope_source_item` (`source_item_id`),
  KEY `idx_noon_auth_owner_scope_project` (`manifest_id`, `owner_user_id`, `project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `noon_auth_owner_scope_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `manifest_id` BIGINT NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `actor` VARCHAR(100) NOT NULL,
  `before_state_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `after_state_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `details_json` JSON NOT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_noon_auth_owner_scope_audit` (`manifest_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
