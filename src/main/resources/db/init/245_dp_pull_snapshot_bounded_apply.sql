-- Migration 245: bounded two-pass snapshot apply and immutable current-head projection.
-- Empty bootstrap only; every conditional ALTER is one atomic all-absent/all-present group.
SET NAMES utf8mb4;
DROP TEMPORARY TABLE IF EXISTS `nuono_dp245_empty_bootstrap_guard`;
CREATE TEMPORARY TABLE `nuono_dp245_empty_bootstrap_guard` (
    `existing_row_count` BIGINT NOT NULL, CONSTRAINT `chk_dp245_empty_bootstrap` CHECK (`existing_row_count`=0)
) ENGINE=InnoDB;
INSERT INTO `nuono_dp245_empty_bootstrap_guard`
SELECT EXISTS(SELECT 1 FROM `dp_pull_snapshot_stage` LIMIT 1)+EXISTS(SELECT 1 FROM `dp_pull_snapshot_stage_page` LIMIT 1)+EXISTS(SELECT 1 FROM `dp_pull_snapshot_stage_item` LIMIT 1)+EXISTS(SELECT 1 FROM `dp_pull_snapshot_apply` LIMIT 1);
DROP TEMPORARY TABLE `nuono_dp245_empty_bootstrap_guard`;
SET @dp245_item_column_count := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='dp_pull_snapshot_stage_item' AND column_name IN ('validated_identity_candidate','absence_reconciliation_safe'));
SET @dp245_item_index_count := (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='dp_pull_snapshot_stage_item' AND index_name='idx_dp_snapshot_item_canonical');
DROP TEMPORARY TABLE IF EXISTS `nuono_dp245_item_shape_guard`;
CREATE TEMPORARY TABLE `nuono_dp245_item_shape_guard` (
    `valid_shape` BIT(1) NOT NULL, CONSTRAINT `chk_dp245_item_shape` CHECK (`valid_shape`=b'1')
) ENGINE=InnoDB;
INSERT INTO `nuono_dp245_item_shape_guard` VALUES (IF((@dp245_item_column_count=0 AND @dp245_item_index_count=0) OR (@dp245_item_column_count=2 AND @dp245_item_index_count=1),b'1',b'0'));
DROP TEMPORARY TABLE `nuono_dp245_item_shape_guard`;
SET @dp245_item_sql := IF(@dp245_item_column_count=0,
    'ALTER TABLE `dp_pull_snapshot_stage_item` ADD COLUMN `validated_identity_candidate` BIT(1) NOT NULL DEFAULT b''0'' AFTER `payload`, ADD COLUMN `absence_reconciliation_safe` BIT(1) NOT NULL DEFAULT b''0'' AFTER `validated_identity_candidate`, ADD KEY `idx_dp_snapshot_item_canonical` (`task_id`,`stable_identity`,`validated_identity_candidate`,`page_no`,`item_ordinal`)', 'DO 0');
PREPARE dp245_item_stmt FROM @dp245_item_sql;
EXECUTE dp245_item_stmt;
DEALLOCATE PREPARE dp245_item_stmt;
-- Snapshot authority strategy and recoverable two-pass progress; 243 remains immutable.
SET @dp245_stage_two_pass_column_count := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='dp_pull_snapshot_stage' AND column_name IN ('collection_mode','verification_state','pass_one_page_count','pass_one_source_item_count','verification_next_page','verification_page_count','verification_source_item_count','comparison_after_fingerprint','comparison_digest_sha256','comparison_key_count','comparison_source_item_count'));
SET @dp245_stage_authority_constraint_count := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='dp_pull_snapshot_stage' AND constraint_name IN ('chk_dp_snapshot_stage_authority','chk_dp_snapshot_stage_authority_v2','chk_dp_snapshot_stage_two_pass'));
DROP TEMPORARY TABLE IF EXISTS `nuono_dp245_stage_two_pass_shape_guard`;
CREATE TEMPORARY TABLE `nuono_dp245_stage_two_pass_shape_guard` (
    `valid_shape` BIT(1) NOT NULL, CONSTRAINT `chk_dp245_stage_two_pass_shape` CHECK (`valid_shape`=b'1')
) ENGINE=InnoDB;
INSERT INTO `nuono_dp245_stage_two_pass_shape_guard` VALUES (IF((@dp245_stage_two_pass_column_count=0 AND @dp245_stage_authority_constraint_count=1) OR (@dp245_stage_two_pass_column_count=11 AND @dp245_stage_authority_constraint_count IN (1,2)),b'1',b'0'));
DROP TEMPORARY TABLE `nuono_dp245_stage_two_pass_shape_guard`;
SET @dp245_stage_two_pass_sql := IF(@dp245_stage_two_pass_column_count=0,
    'ALTER TABLE `dp_pull_snapshot_stage` ADD COLUMN `collection_mode` VARCHAR(24) DEFAULT NULL AFTER `declared_collection_count`, ADD COLUMN `verification_state` VARCHAR(16) DEFAULT NULL AFTER `collection_mode`, ADD COLUMN `pass_one_page_count` INT NOT NULL DEFAULT 0 AFTER `verification_state`, ADD COLUMN `pass_one_source_item_count` BIGINT NOT NULL DEFAULT 0 AFTER `pass_one_page_count`, ADD COLUMN `verification_next_page` INT DEFAULT NULL AFTER `pass_one_source_item_count`, ADD COLUMN `verification_page_count` INT NOT NULL DEFAULT 0 AFTER `verification_next_page`, ADD COLUMN `verification_source_item_count` BIGINT NOT NULL DEFAULT 0 AFTER `verification_page_count`, ADD COLUMN `comparison_after_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `verification_source_item_count`, ADD COLUMN `comparison_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `comparison_after_fingerprint`, ADD COLUMN `comparison_key_count` BIGINT NOT NULL DEFAULT 0 AFTER `comparison_digest_sha256`, ADD COLUMN `comparison_source_item_count` BIGINT NOT NULL DEFAULT 0 AFTER `comparison_key_count`', 'DO 0');
PREPARE dp245_stage_two_pass_stmt FROM @dp245_stage_two_pass_sql;
EXECUTE dp245_stage_two_pass_stmt;
DEALLOCATE PREPARE dp245_stage_two_pass_stmt;
SET @dp245_stage_authority_sql := IF(@dp245_stage_authority_constraint_count=1,
    'ALTER TABLE `dp_pull_snapshot_stage` DROP CHECK `chk_dp_snapshot_stage_authority`, ADD CONSTRAINT `chk_dp_snapshot_stage_authority_v2` CHECK ((`collection_mode` IS NULL AND `verification_state` IS NULL AND ((`authority_kind` IS NULL AND `authority_token_sha256` IS NULL AND `snapshot_as_of_utc` IS NULL AND `declared_collection_count` IS NULL) OR (`authority_kind` IN (''PAGED_GENERATION'',''COMPLETE_EXPORT'') AND `authority_token_sha256` IS NOT NULL AND `authority_token_sha256` REGEXP ''^[0-9a-f]{64}$'' AND `declared_collection_count` IS NOT NULL AND `declared_collection_count`>=0))) OR (`collection_mode`=''PROVIDER_AUTHORITY'' AND `verification_state` IS NULL AND `authority_kind` IN (''PAGED_GENERATION'',''COMPLETE_EXPORT'',''COMPLETE_RESPONSE'') AND `authority_token_sha256` IS NOT NULL AND `authority_token_sha256` REGEXP ''^[0-9a-f]{64}$'' AND `declared_collection_count` IS NOT NULL AND `declared_collection_count`>=0) OR (`collection_mode`=''TWO_PASS_REQUIRED'' AND `verification_state` IN (''PASS_ONE'',''VERIFYING'',''COMPARING'') AND `authority_kind` IS NULL AND `authority_token_sha256` IS NULL AND `snapshot_as_of_utc` IS NULL AND `declared_collection_count` IS NULL) OR (`collection_mode`=''TWO_PASS_REQUIRED'' AND `verification_state`=''VERIFIED'' AND `authority_kind`=''TWO_PASS_OBSERVATION'' AND `authority_token_sha256` IS NOT NULL AND `authority_token_sha256` REGEXP ''^[0-9a-f]{64}$'' AND `snapshot_as_of_utc` IS NULL AND `declared_collection_count` IS NOT NULL AND `declared_collection_count`>=0)), ADD CONSTRAINT `chk_dp_snapshot_stage_two_pass` CHECK (`pass_one_page_count`>=0 AND `pass_one_source_item_count`>=0 AND `verification_page_count`>=0 AND `verification_source_item_count`>=0 AND `comparison_key_count`>=0 AND `comparison_source_item_count`>=0 AND (`verification_next_page` IS NULL OR `verification_next_page`>0) AND (`comparison_after_fingerprint` IS NULL OR `comparison_after_fingerprint` REGEXP ''^[0-9a-f]{64}$'') AND (`comparison_digest_sha256` IS NULL OR `comparison_digest_sha256` REGEXP ''^[0-9a-f]{64}$''))', 'DO 0');
PREPARE dp245_stage_authority_stmt FROM @dp245_stage_authority_sql;
EXECUTE dp245_stage_authority_stmt;
DEALLOCATE PREPARE dp245_stage_authority_stmt;
CREATE TABLE IF NOT EXISTS `dp_pull_snapshot_fingerprint_count` (
    `task_id` BIGINT NOT NULL, `content_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `pass_one_count` BIGINT NOT NULL DEFAULT 0, `pass_two_count` BIGINT NOT NULL DEFAULT 0,
    `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `gmt_updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`task_id`,`content_fingerprint`),
    CONSTRAINT `chk_dp_snapshot_fingerprint_digest` CHECK (`content_fingerprint` REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT `chk_dp_snapshot_fingerprint_counts` CHECK (`pass_one_count`>=0 AND `pass_two_count`>=0 AND (`pass_one_count`>0 OR `pass_two_count`>0)),
    CONSTRAINT `fk_dp_snapshot_fingerprint_stage` FOREIGN KEY (`task_id`) REFERENCES `dp_pull_snapshot_stage` (`task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `dp_pull_snapshot_verify_page` (
    `task_id` BIGINT NOT NULL, `page_no` INT NOT NULL, `next_page` INT DEFAULT NULL,
    `is_last_page` BIT(1) DEFAULT NULL, `total_pages` INT DEFAULT NULL,
    `source_item_count` INT NOT NULL, `business_skipped_item_count` INT NOT NULL,
    `page_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`task_id`,`page_no`),
    CONSTRAINT `chk_dp_snapshot_verify_page_shape` CHECK (`page_no`>0 AND (`next_page` IS NULL OR `next_page`=`page_no`+1) AND (`total_pages` IS NULL OR `total_pages`>=`page_no`) AND `source_item_count`>=0 AND `business_skipped_item_count` BETWEEN 0 AND `source_item_count`),
    CONSTRAINT `chk_dp_snapshot_verify_page_digest` CHECK (`page_digest_sha256` REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT `fk_dp_snapshot_verify_stage` FOREIGN KEY (`task_id`) REFERENCES `dp_pull_snapshot_stage` (`task_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
SET @dp245_apply_authority_v1_count := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='dp_pull_snapshot_apply' AND constraint_name='chk_dp_snapshot_apply_authority');
SET @dp245_apply_authority_v2_count := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='dp_pull_snapshot_apply' AND constraint_name='chk_dp_snapshot_apply_authority_v2');
DROP TEMPORARY TABLE IF EXISTS `nuono_dp245_apply_authority_shape_guard`;
CREATE TEMPORARY TABLE `nuono_dp245_apply_authority_shape_guard` (`valid_shape` BIT(1) NOT NULL, CONSTRAINT `chk_dp245_apply_authority_shape` CHECK (`valid_shape`=b'1')) ENGINE=InnoDB;
INSERT INTO `nuono_dp245_apply_authority_shape_guard` VALUES (IF((@dp245_apply_authority_v1_count=1 AND @dp245_apply_authority_v2_count=0) OR (@dp245_apply_authority_v1_count=0 AND @dp245_apply_authority_v2_count=1),b'1',b'0'));
DROP TEMPORARY TABLE `nuono_dp245_apply_authority_shape_guard`;
SET @dp245_apply_authority_sql := IF(@dp245_apply_authority_v1_count=1,
    'ALTER TABLE `dp_pull_snapshot_apply` DROP CHECK `chk_dp_snapshot_apply_authority`, ADD CONSTRAINT `chk_dp_snapshot_apply_authority_v2` CHECK (`authority_kind` IN (''PAGED_GENERATION'',''COMPLETE_EXPORT'',''COMPLETE_RESPONSE'',''TWO_PASS_OBSERVATION'') AND `authority_token_sha256` REGEXP ''^[0-9a-f]{64}$'' AND `declared_collection_count`>=0)', 'DO 0');
PREPARE dp245_apply_authority_stmt FROM @dp245_apply_authority_sql;
EXECUTE dp245_apply_authority_stmt;
DEALLOCATE PREPARE dp245_apply_authority_stmt;
SET @dp245_apply_column_count := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='dp_pull_snapshot_apply' AND column_name IN ('effective_item_count','carry_mode','carried_from_task_id'));
SET @dp245_apply_index_count := (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='dp_pull_snapshot_apply' AND index_name='idx_dp_snapshot_apply_carry_source');
SET @dp245_apply_constraint_count := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='dp_pull_snapshot_apply' AND constraint_name IN ('chk_dp_snapshot_apply_effective','fk_dp_snapshot_apply_carry_source'));
DROP TEMPORARY TABLE IF EXISTS `nuono_dp245_apply_shape_guard`;
CREATE TEMPORARY TABLE `nuono_dp245_apply_shape_guard` (
    `valid_shape` BIT(1) NOT NULL, CONSTRAINT `chk_dp245_apply_shape` CHECK (`valid_shape`=b'1')
) ENGINE=InnoDB;
INSERT INTO `nuono_dp245_apply_shape_guard` VALUES (IF((@dp245_apply_column_count=0 AND @dp245_apply_index_count=0 AND @dp245_apply_constraint_count=0) OR (@dp245_apply_column_count=3 AND @dp245_apply_index_count=1 AND @dp245_apply_constraint_count=2),b'1',b'0'));
DROP TEMPORARY TABLE `nuono_dp245_apply_shape_guard`;
SET @dp245_apply_sql := IF(@dp245_apply_column_count=0,
    'ALTER TABLE `dp_pull_snapshot_apply` ADD COLUMN `effective_item_count` BIGINT NOT NULL DEFAULT 0 AFTER `last_page`, ADD COLUMN `carry_mode` VARCHAR(16) NOT NULL DEFAULT ''NONE'' AFTER `effective_item_count`, ADD COLUMN `carried_from_task_id` BIGINT DEFAULT NULL AFTER `carry_mode`, ADD KEY `idx_dp_snapshot_apply_carry_source` (`carried_from_task_id`,`task_id`), ADD CONSTRAINT `chk_dp_snapshot_apply_effective` CHECK (`effective_item_count`>=`applied_item_count` AND ((`carry_mode`=''NONE'' AND `carried_from_task_id` IS NULL) OR (`carry_mode` IN (''TARGETED'',''FULL'') AND `carried_from_task_id` IS NOT NULL AND `carried_from_task_id`>0 AND `carried_from_task_id`<`task_id`))), ADD CONSTRAINT `fk_dp_snapshot_apply_carry_source` FOREIGN KEY (`carried_from_task_id`) REFERENCES `dp_pull_task` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT', 'DO 0');
PREPARE dp245_apply_stmt FROM @dp245_apply_sql;
EXECUTE dp245_apply_stmt;
DEALLOCATE PREPARE dp245_apply_stmt;
CREATE TABLE IF NOT EXISTS `dp_pull_snapshot_apply_progress` (
    `task_id` BIGINT NOT NULL, `active_fence_epoch` BIGINT NOT NULL, `cursor_page_no` INT NOT NULL DEFAULT 0,
    `cursor_item_ordinal` INT NOT NULL DEFAULT -1, `prepared_item_count` BIGINT NOT NULL DEFAULT 0,
    `absence_unsafe_item_count` BIGINT NOT NULL DEFAULT 0, `effective_item_count` BIGINT NOT NULL DEFAULT 0,
    `target_ref_type` VARCHAR(64) DEFAULT NULL, `target_ref_id` BIGINT DEFAULT NULL,
    `carry_mode` VARCHAR(16) NOT NULL DEFAULT 'NONE', `carry_source_task_id` BIGINT DEFAULT NULL,
    `carry_source_head_version` BIGINT DEFAULT NULL, `carry_cursor_identity` VARCHAR(240) DEFAULT NULL,
    `state` VARCHAR(16) NOT NULL, `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `gmt_updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`task_id`), KEY `idx_dp_snapshot_progress_target` (`target_ref_type`, `target_ref_id`, `state`, `task_id`),
    KEY `idx_dp_snapshot_progress_carry_source` (`carry_source_task_id`, `task_id`, `state`),
    CONSTRAINT `chk_dp_snapshot_progress_fence` CHECK (`active_fence_epoch`>0),
    CONSTRAINT `chk_dp_snapshot_progress_cursor` CHECK ((`cursor_page_no`=0 AND `cursor_item_ordinal`=-1) OR (`cursor_page_no`>0 AND `cursor_item_ordinal`>=0)),
    CONSTRAINT `chk_dp_snapshot_progress_count` CHECK (`prepared_item_count`>=0 AND `absence_unsafe_item_count` BETWEEN 0 AND `prepared_item_count` AND `effective_item_count`>=0),
    CONSTRAINT `chk_dp_snapshot_progress_target` CHECK ((`target_ref_type` IS NULL AND `target_ref_id` IS NULL) OR (`target_ref_type` IS NOT NULL AND `target_ref_type` REGEXP '^[A-Z][A-Z0-9_]{0,63}$' AND `target_ref_id` IS NOT NULL AND `target_ref_id`>0)),
    CONSTRAINT `chk_dp_snapshot_progress_carry` CHECK ((`state`='PREPARING' AND `carry_mode`='NONE' AND `carry_source_task_id` IS NULL AND `carry_source_head_version` IS NULL AND `carry_cursor_identity` IS NULL AND `effective_item_count`<=`prepared_item_count`) OR (`state`='CARRYING' AND `carry_mode` IN ('TARGETED','FULL') AND `carry_source_task_id` IS NOT NULL AND `carry_source_task_id`>0 AND `carry_source_task_id`<`task_id` AND `carry_source_head_version` IS NOT NULL AND `carry_source_head_version`>=0 AND (`carry_cursor_identity` IS NULL OR CHAR_LENGTH(TRIM(`carry_cursor_identity`))>0)) OR (`state`='SEALED' AND ((`carry_mode`='NONE' AND `carry_source_task_id` IS NULL AND `carry_source_head_version` IS NULL AND `carry_cursor_identity` IS NULL) OR (`carry_mode` IN ('TARGETED','FULL') AND `carry_source_task_id` IS NOT NULL AND `carry_source_task_id`>0 AND `carry_source_task_id`<`task_id` AND `carry_source_head_version` IS NOT NULL AND `carry_source_head_version`>=0)))),
    CONSTRAINT `chk_dp_snapshot_progress_state` CHECK (`state` IN ('PREPARING','CARRYING','SEALED')),
    CONSTRAINT `fk_dp_snapshot_progress_task` FOREIGN KEY (`task_id`) REFERENCES `dp_pull_task` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_dp_snapshot_progress_carry_source` FOREIGN KEY (`carry_source_task_id`) REFERENCES `dp_pull_task` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `dp_pull_snapshot_effective_item` (
    `task_id` BIGINT NOT NULL, `stable_identity` VARCHAR(240) NOT NULL, `source_page_no` INT NOT NULL,
    `source_item_ordinal` INT NOT NULL, `content_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `payload` MEDIUMTEXT NOT NULL, `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`task_id`, `stable_identity`),
    CONSTRAINT `chk_dp_snapshot_effective_identity` CHECK (CHAR_LENGTH(TRIM(`stable_identity`))>0 AND `source_page_no`>0 AND `source_item_ordinal`>=0),
    CONSTRAINT `chk_dp_snapshot_effective_fingerprint` CHECK (`content_fingerprint` REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT `chk_dp_snapshot_effective_payload` CHECK (OCTET_LENGTH(`payload`)<=16711680),
    CONSTRAINT `fk_dp_snapshot_effective_task` FOREIGN KEY (`task_id`) REFERENCES `dp_pull_task` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `dp_pull_snapshot_current_head` (
    `operation_code` VARCHAR(16) NOT NULL, `scope_key` VARCHAR(96) NOT NULL, `task_id` BIGINT NOT NULL,
    `business_window_key` VARCHAR(160) NOT NULL, `schedule_slot` DATETIME(3) NOT NULL, `retire_missing` BIT(1) NOT NULL,
    `version_no` BIGINT NOT NULL DEFAULT 0, `sealed_at` DATETIME(3) NOT NULL,
    `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `gmt_updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`operation_code`, `scope_key`), UNIQUE KEY `uk_dp_snapshot_current_head_task` (`task_id`),
    CONSTRAINT `chk_dp_snapshot_head_operation` CHECK (`operation_code` IN ('DP04','DP07A')),
    CONSTRAINT `chk_dp_snapshot_head_identity` CHECK (CHAR_LENGTH(TRIM(`scope_key`))>0 AND CHAR_LENGTH(TRIM(`business_window_key`))>0 AND `version_no`>=0),
    CONSTRAINT `fk_dp_snapshot_head_task` FOREIGN KEY (`task_id`) REFERENCES `dp_pull_task` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
-- DP07 effective counts stay BIGINT end-to-end; the batch seal must not narrow a valid provider container.
SET @dp245_inventory_batch_count_shape := (SELECT GROUP_CONCAT(LOWER(column_type) ORDER BY FIELD(column_name,'total_rows','valid_rows','error_rows') SEPARATOR ',') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='official_warehouse_inventory_sync_batch' AND column_name IN ('total_rows','valid_rows','error_rows'));
DROP TEMPORARY TABLE IF EXISTS `nuono_dp245_inventory_batch_shape_guard`;
CREATE TEMPORARY TABLE `nuono_dp245_inventory_batch_shape_guard` (`valid_shape` BIT(1) NOT NULL, CONSTRAINT `chk_dp245_inventory_batch_shape` CHECK (`valid_shape`=b'1')) ENGINE=InnoDB;
INSERT INTO `nuono_dp245_inventory_batch_shape_guard` VALUES (IF(@dp245_inventory_batch_count_shape IN ('int,int,int','bigint,bigint,bigint'),b'1',b'0'));
DROP TEMPORARY TABLE `nuono_dp245_inventory_batch_shape_guard`;
SET @dp245_inventory_batch_sql := IF(@dp245_inventory_batch_count_shape='int,int,int',
    'ALTER TABLE `official_warehouse_inventory_sync_batch` MODIFY COLUMN `total_rows` BIGINT DEFAULT NULL, MODIFY COLUMN `valid_rows` BIGINT DEFAULT NULL, MODIFY COLUMN `error_rows` BIGINT DEFAULT NULL', 'DO 0');
PREPARE dp245_inventory_batch_stmt FROM @dp245_inventory_batch_sql;
EXECUTE dp245_inventory_batch_stmt;
DEALLOCATE PREPARE dp245_inventory_batch_stmt;
SET @dp245_inventory_column_count := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='official_warehouse_inventory_snapshot_line' AND column_name='snapshot_stable_identity');
SET @dp245_inventory_index_count := (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='official_warehouse_inventory_snapshot_line' AND index_name IN ('uk_official_inventory_batch_stable_identity','idx_official_inventory_carry'));
DROP TEMPORARY TABLE IF EXISTS `nuono_dp245_inventory_shape_guard`;
CREATE TEMPORARY TABLE `nuono_dp245_inventory_shape_guard` (
    `valid_shape` BIT(1) NOT NULL, CONSTRAINT `chk_dp245_inventory_shape` CHECK (`valid_shape`=b'1')
) ENGINE=InnoDB;
INSERT INTO `nuono_dp245_inventory_shape_guard` VALUES (IF((@dp245_inventory_column_count=0 AND @dp245_inventory_index_count=0) OR (@dp245_inventory_column_count=1 AND @dp245_inventory_index_count=2),b'1',b'0'));
DROP TEMPORARY TABLE `nuono_dp245_inventory_shape_guard`;
SET @dp245_inventory_sql := IF(@dp245_inventory_column_count=0,
    'ALTER TABLE `official_warehouse_inventory_snapshot_line` ADD COLUMN `snapshot_stable_identity` VARCHAR(240) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL AFTER `sync_batch_id`, ADD UNIQUE KEY `uk_official_inventory_batch_stable_identity` (`sync_batch_id`,`snapshot_stable_identity`), ADD KEY `idx_official_inventory_carry` (`sync_batch_id`,`snapshot_stable_identity`,`is_deleted`,`id`)', 'DO 0');
PREPARE dp245_inventory_stmt FROM @dp245_inventory_sql;
EXECUTE dp245_inventory_stmt;
DEALLOCATE PREPARE dp245_inventory_stmt;
INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'official_warehouse_inventory_snapshot_line',GREATEST(622000,COALESCE(MAX(`id`),0)),NOW(),NOW()
FROM `official_warehouse_inventory_snapshot_line`
ON DUPLICATE KEY UPDATE `next_id`=GREATEST(`next_id`,VALUES(`next_id`)),`gmt_updated`=NOW();
SET @dp245_task_index_count := (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='dp_pull_task' AND index_name='idx_dp_pull_task_business_scope');
SET @dp245_task_index_shape := (SELECT IF(COUNT(*)=8 AND MIN(non_unique)=1 AND GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')='operation_code,owner_user_id,logical_store_id,project_code,store_code,site_code,id,scope_key' AND MIN(index_type='BTREE' AND is_visible='YES' AND sub_part IS NULL AND expression IS NULL)=1,b'1',b'0') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='dp_pull_task' AND index_name='idx_dp_pull_task_business_scope');
DROP TEMPORARY TABLE IF EXISTS `nuono_dp245_task_index_shape_guard`;
CREATE TEMPORARY TABLE `nuono_dp245_task_index_shape_guard` (`valid_shape` BIT(1) NOT NULL, CONSTRAINT `chk_dp245_task_index_shape` CHECK (`valid_shape`=b'1')) ENGINE=InnoDB;
INSERT INTO `nuono_dp245_task_index_shape_guard` VALUES (IF(@dp245_task_index_count=0 OR (@dp245_task_index_count=1 AND @dp245_task_index_shape=b'1'),b'1',b'0'));
DROP TEMPORARY TABLE `nuono_dp245_task_index_shape_guard`;
SET @dp245_task_index_sql := IF(@dp245_task_index_count=0,
    'ALTER TABLE `dp_pull_task` ADD KEY `idx_dp_pull_task_business_scope` (`operation_code`,`owner_user_id`,`logical_store_id`,`project_code`,`store_code`,`site_code`,`id`,`scope_key`)', 'DO 0');
PREPARE dp245_task_index_stmt FROM @dp245_task_index_sql;
EXECUTE dp245_task_index_stmt;
DEALLOCATE PREPARE dp245_task_index_stmt;
-- Runtime/legacy visibility; malformed heads fail closed and explicit fields prevent projection drift.
CREATE OR REPLACE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW `official_warehouse_current_inventory_snapshot_line_raw` AS
SELECT line.id,line.sync_batch_id,line.snapshot_stable_identity,line.owner_user_id,line.logical_store_id,line.store_code,line.site_code,
       line.project_code,line.partner_id,line.product_master_id,line.product_variant_id,line.product_site_offer_id,line.partner_sku,
       line.psku_code,line.noon_sku,line.pbarcode,line.barcode,line.warehouse_code,line.country_code,line.inventory_type,line.reason_code,
       line.classification_code,line.stock_bucket,line.qty,line.inventory_snapshot_at,line.title_cache,line.brand_cache,
       line.match_status,line.match_message,line.raw_payload_json,line.is_current,line.is_deleted,line.created_by,line.updated_by,
       line.gmt_create,line.gmt_updated
FROM `official_warehouse_inventory_snapshot_line` line
JOIN `dp_pull_task` task ON task.owner_user_id=line.owner_user_id AND task.logical_store_id <=> line.logical_store_id
 AND BINARY task.project_code <=> BINARY line.project_code AND BINARY task.store_code=BINARY line.store_code
 AND BINARY UPPER(task.site_code)=BINARY UPPER(line.site_code)
JOIN `dp_pull_snapshot_current_head` head ON head.operation_code='DP07A' AND head.task_id=task.id
 AND BINARY head.scope_key=BINARY task.scope_key AND BINARY head.business_window_key=BINARY task.business_window_key
 AND head.schedule_slot=task.schedule_slot
JOIN `dp_pull_snapshot_apply_progress` progress ON progress.task_id=head.task_id AND progress.state='SEALED'
 AND progress.target_ref_type='OFFICIAL_WAREHOUSE_INVENTORY_BATCH'
JOIN `dp_pull_snapshot_apply` applied ON applied.task_id=head.task_id AND applied.operation_code=head.operation_code
 AND BINARY applied.scope_key=BINARY head.scope_key AND BINARY applied.business_window_key=BINARY head.business_window_key
 AND applied.applied_fence_epoch=progress.active_fence_epoch AND applied.effective_item_count=progress.effective_item_count
 AND applied.carry_mode=progress.carry_mode AND applied.carried_from_task_id <=> progress.carry_source_task_id
JOIN `official_warehouse_inventory_sync_batch` batch ON batch.id=progress.target_ref_id AND batch.status='IMPORTED'
 AND batch.source_type='FBN_INVENTORY_API' AND batch.is_deleted=b'0' AND batch.owner_user_id=task.owner_user_id
 AND batch.logical_store_id <=> task.logical_store_id AND BINARY batch.project_code <=> BINARY task.project_code
 AND BINARY batch.store_code=BINARY task.store_code AND BINARY UPPER(batch.site_code)=BINARY UPPER(task.site_code)
 AND batch.total_pages=applied.last_page AND batch.total_rows=applied.effective_item_count
 AND batch.valid_rows=batch.total_rows AND batch.error_rows=applied.business_skipped_item_count
 AND applied.effective_item_count=(SELECT COUNT(*) FROM `official_warehouse_inventory_snapshot_line` materialized
     WHERE materialized.sync_batch_id=batch.id AND materialized.is_deleted=b'0' AND materialized.is_current=b'0'
       AND materialized.snapshot_stable_identity IS NOT NULL AND materialized.owner_user_id=task.owner_user_id
       AND materialized.logical_store_id <=> task.logical_store_id
       AND BINARY materialized.project_code <=> BINARY task.project_code
       AND BINARY materialized.store_code=BINARY task.store_code
       AND BINARY UPPER(materialized.site_code)=BINARY UPPER(task.site_code))
 AND NOT EXISTS (SELECT 1 FROM `official_warehouse_inventory_snapshot_line` malformed
     WHERE malformed.sync_batch_id=batch.id AND malformed.is_deleted=b'0'
       AND (malformed.is_current<>b'0' OR malformed.snapshot_stable_identity IS NULL
         OR malformed.owner_user_id<>task.owner_user_id OR NOT (malformed.logical_store_id <=> task.logical_store_id)
         OR NOT (BINARY malformed.project_code <=> BINARY task.project_code)
         OR BINARY malformed.store_code<>BINARY task.store_code
         OR BINARY UPPER(malformed.site_code)<>BINARY UPPER(task.site_code)))
WHERE line.is_deleted=b'0' AND line.is_current=b'0' AND line.sync_batch_id=batch.id
  AND line.snapshot_stable_identity IS NOT NULL
UNION ALL
SELECT line.id,line.sync_batch_id,line.snapshot_stable_identity,line.owner_user_id,line.logical_store_id,line.store_code,line.site_code,
       line.project_code,line.partner_id,line.product_master_id,line.product_variant_id,line.product_site_offer_id,line.partner_sku,
       line.psku_code,line.noon_sku,line.pbarcode,line.barcode,line.warehouse_code,line.country_code,line.inventory_type,line.reason_code,
       line.classification_code,line.stock_bucket,line.qty,line.inventory_snapshot_at,line.title_cache,line.brand_cache,
       line.match_status,line.match_message,line.raw_payload_json,line.is_current,line.is_deleted,line.created_by,line.updated_by,
       line.gmt_create,line.gmt_updated
FROM `official_warehouse_inventory_snapshot_line` line
WHERE line.is_deleted=b'0' AND line.is_current=b'1'
  AND NOT EXISTS (SELECT 1 FROM `dp_pull_snapshot_current_head` head JOIN `dp_pull_task` task ON task.id=head.task_id
      WHERE head.operation_code='DP07A' AND task.owner_user_id=line.owner_user_id
        AND task.logical_store_id <=> line.logical_store_id AND BINARY task.project_code <=> BINARY line.project_code
        AND BINARY task.store_code=BINARY line.store_code AND BINARY UPPER(task.site_code)=BINARY UPPER(line.site_code));
-- Product association remains a query-only compatibility projection, never a DP07 write dependency.
CREATE OR REPLACE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW `official_warehouse_effective_inventory_snapshot_line` AS
WITH match_candidate AS (
    SELECT raw.id AS line_id,pm.id AS product_master_id,pv.id AS product_variant_id,pso.id AS product_site_offer_id,
           pso.psku_code,COALESCE(pm.title_cn_cache,pm.title_cache,pv.partner_sku,pm.sku_parent) AS product_title,
           pm.brand_cache AS product_brand,
           ROW_NUMBER() OVER (PARTITION BY raw.id ORDER BY CASE
               WHEN raw.noon_sku IS NOT NULL AND BINARY COALESCE(NULLIF(pv.child_sku,''),pm.sku_parent)=BINARY raw.noon_sku THEN 0
               WHEN raw.partner_sku IS NOT NULL AND BINARY pv.partner_sku=BINARY raw.partner_sku THEN 1
               WHEN raw.noon_sku IS NOT NULL AND BINARY pso.psku_code=BINARY raw.noon_sku THEN 2 ELSE 3 END,pso.id) AS candidate_rank
    FROM `official_warehouse_current_inventory_snapshot_line_raw` raw
    JOIN `logical_store_site` lss ON lss.logical_store_id=raw.logical_store_id
     AND BINARY lss.store_code=BINARY raw.store_code AND BINARY UPPER(lss.site)=BINARY UPPER(raw.site_code) AND lss.is_deleted=b'0'
    JOIN `logical_store` ls ON ls.id=lss.logical_store_id AND ls.owner_user_id=raw.owner_user_id AND ls.is_deleted=b'0'
    JOIN `product_master` pm ON pm.logical_store_id=ls.id AND pm.is_deleted=b'0'
    JOIN `product_variant` pv ON pv.product_master_id=pm.id AND pv.is_deleted=b'0'
    JOIN `product_site_offer` pso ON pso.variant_id=pv.id AND pso.site_id=lss.id AND pso.is_deleted=b'0'
    WHERE raw.snapshot_stable_identity IS NOT NULL
      AND ((raw.noon_sku IS NOT NULL AND (BINARY COALESCE(NULLIF(pv.child_sku,''),pm.sku_parent)=BINARY raw.noon_sku
           OR BINARY pso.psku_code=BINARY raw.noon_sku OR BINARY pm.sku_parent=BINARY raw.noon_sku))
        OR (raw.partner_sku IS NOT NULL AND BINARY pv.partner_sku=BINARY raw.partner_sku))
), best_match AS (
    SELECT line_id,product_master_id,product_variant_id,product_site_offer_id,psku_code,product_title,product_brand
    FROM match_candidate WHERE candidate_rank=1
)
SELECT raw.id,raw.sync_batch_id,raw.snapshot_stable_identity,raw.owner_user_id,raw.logical_store_id,raw.store_code,
       raw.site_code,raw.project_code,raw.partner_id,
       CASE WHEN raw.snapshot_stable_identity IS NULL THEN raw.product_master_id ELSE best.product_master_id END AS product_master_id,
       CASE WHEN raw.snapshot_stable_identity IS NULL THEN raw.product_variant_id ELSE best.product_variant_id END AS product_variant_id,
       CASE WHEN raw.snapshot_stable_identity IS NULL THEN raw.product_site_offer_id ELSE best.product_site_offer_id END AS product_site_offer_id,
       raw.partner_sku,CASE WHEN raw.snapshot_stable_identity IS NULL THEN raw.psku_code ELSE best.psku_code END AS psku_code,
       raw.noon_sku,raw.pbarcode,raw.barcode,raw.warehouse_code,raw.country_code,raw.inventory_type,raw.reason_code,
       raw.classification_code,raw.stock_bucket,raw.qty,raw.inventory_snapshot_at,
       CASE WHEN raw.snapshot_stable_identity IS NULL THEN raw.title_cache ELSE COALESCE(NULLIF(raw.title_cache,''),best.product_title) END AS title_cache,
       CASE WHEN raw.snapshot_stable_identity IS NULL THEN raw.brand_cache ELSE COALESCE(NULLIF(raw.brand_cache,''),best.product_brand) END AS brand_cache,
       CASE WHEN raw.snapshot_stable_identity IS NULL THEN raw.match_status WHEN best.line_id IS NULL THEN 'RAW_PROVIDER_FACT' ELSE 'MATCHED' END AS match_status,
       CASE WHEN raw.snapshot_stable_identity IS NULL THEN raw.match_message ELSE NULL END AS match_message,
       raw.raw_payload_json,raw.is_current,raw.is_deleted,raw.created_by,raw.updated_by,raw.gmt_create,raw.gmt_updated
FROM `official_warehouse_current_inventory_snapshot_line_raw` raw
LEFT JOIN best_match best ON best.line_id=raw.id;
-- Registration gates: statement-boundary repair, exact/live metadata, plan review, materialized count
-- closure, empty generations, and technical size/range rejection without business-row truncation.
