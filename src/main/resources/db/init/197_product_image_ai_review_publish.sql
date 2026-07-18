-- AI product-image generation, review, rework, and Noon publication workflow.
-- Keep the migration rerunnable because production releases may resume after a partial DDL failure.

SET NAMES utf8mb4;

SET @product_image_parent_suite_id_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'parent_suite_id'
);
SET @product_image_parent_suite_id_ddl := IF(
  @product_image_parent_suite_id_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `parent_suite_id` BIGINT DEFAULT NULL AFTER `profile_id`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_parent_suite_id_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_revision_no_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'revision_no'
);
SET @product_image_revision_no_ddl := IF(
  @product_image_revision_no_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `revision_no` INT NOT NULL DEFAULT 1 AFTER `parent_suite_id`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_revision_no_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_review_comment_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'review_comment'
);
SET @product_image_review_comment_ddl := IF(
  @product_image_review_comment_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `review_comment` VARCHAR(2000) DEFAULT NULL AFTER `suite_status`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_review_comment_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_reviewed_by_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'reviewed_by'
);
SET @product_image_reviewed_by_ddl := IF(
  @product_image_reviewed_by_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `reviewed_by` BIGINT DEFAULT NULL AFTER `review_comment`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_reviewed_by_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_reviewed_at_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'reviewed_at'
);
SET @product_image_reviewed_at_ddl := IF(
  @product_image_reviewed_at_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL AFTER `reviewed_by`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_reviewed_at_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_failure_stage_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'failure_stage'
);
SET @product_image_failure_stage_ddl := IF(
  @product_image_failure_stage_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `failure_stage` VARCHAR(32) DEFAULT NULL AFTER `reviewed_at`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_failure_stage_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_failure_reason_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'failure_reason'
);
SET @product_image_failure_reason_ddl := IF(
  @product_image_failure_reason_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `failure_reason` VARCHAR(2000) DEFAULT NULL AFTER `failure_stage`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_failure_reason_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_published_at_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'published_at'
);
SET @product_image_published_at_ddl := IF(
  @product_image_published_at_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `published_at` DATETIME DEFAULT NULL AFTER `failure_reason`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_published_at_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_publish_manifest_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND COLUMN_NAME = 'publish_manifest_json'
);
SET @product_image_publish_manifest_ddl := IF(
  @product_image_publish_manifest_exists = 0,
  'ALTER TABLE `product_image_suite` ADD COLUMN `publish_manifest_json` LONGTEXT DEFAULT NULL AFTER `published_at`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_publish_manifest_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_generation_task_index_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND INDEX_NAME = 'idx_product_image_suite_generation_task'
);
SET @product_image_generation_task_index_ddl := IF(
  @product_image_generation_task_index_exists = 0,
  'ALTER TABLE `product_image_suite` ADD KEY `idx_product_image_suite_generation_task` (`generation_task_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_generation_task_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_parent_revision_index_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite' AND INDEX_NAME = 'idx_product_image_suite_parent_revision'
);
SET @product_image_parent_revision_index_ddl := IF(
  @product_image_parent_revision_index_exists = 0,
  'ALTER TABLE `product_image_suite` ADD KEY `idx_product_image_suite_parent_revision` (`parent_suite_id`, `revision_no`)',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_parent_revision_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_role_ordinal_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite_asset' AND COLUMN_NAME = 'role_ordinal'
);
SET @product_image_role_ordinal_ddl := IF(
  @product_image_role_ordinal_exists = 0,
  'ALTER TABLE `product_image_suite_asset` ADD COLUMN `role_ordinal` INT NOT NULL DEFAULT 1 AFTER `image_role`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_role_ordinal_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_content_type_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite_asset' AND COLUMN_NAME = 'content_type'
);
SET @product_image_content_type_ddl := IF(
  @product_image_content_type_exists = 0,
  'ALTER TABLE `product_image_suite_asset` ADD COLUMN `content_type` VARCHAR(100) DEFAULT NULL AFTER `image_url`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_content_type_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_size_bytes_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite_asset' AND COLUMN_NAME = 'size_bytes'
);
SET @product_image_size_bytes_ddl := IF(
  @product_image_size_bytes_exists = 0,
  'ALTER TABLE `product_image_suite_asset` ADD COLUMN `size_bytes` BIGINT DEFAULT NULL AFTER `content_type`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_size_bytes_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_sha256_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite_asset' AND COLUMN_NAME = 'sha256'
);
SET @product_image_sha256_ddl := IF(
  @product_image_sha256_exists = 0,
  'ALTER TABLE `product_image_suite_asset` ADD COLUMN `sha256` VARCHAR(64) DEFAULT NULL AFTER `size_bytes`',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_sha256_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_asset_slot_index_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_image_suite_asset' AND INDEX_NAME = 'uk_product_image_suite_asset_slot'
);

-- Legacy suites can contain multiple images with the same role. Number them by the
-- existing display order before enforcing the new stable role + ordinal slot key.
SET @product_image_role_ordinal_backfill_ddl := IF(
  @product_image_asset_slot_index_exists = 0,
  'UPDATE `product_image_suite_asset` target JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY suite_id, image_role ORDER BY sort_order, id) AS expected_role_ordinal FROM `product_image_suite_asset`) ranked ON ranked.id = target.id SET target.role_ordinal = ranked.expected_role_ordinal WHERE target.role_ordinal <> ranked.expected_role_ordinal',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_role_ordinal_backfill_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_asset_slot_index_ddl := IF(
  @product_image_asset_slot_index_exists = 0,
  'ALTER TABLE `product_image_suite_asset` ADD UNIQUE KEY `uk_product_image_suite_asset_slot` (`suite_id`, `image_role`, `role_ordinal`)',
  'SELECT 1'
);
PREPARE stmt FROM @product_image_asset_slot_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `product_image_suite_review_target` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `suite_id` BIGINT NOT NULL,
  `target_scope` VARCHAR(16) NOT NULL,
  `asset_id` BIGINT DEFAULT NULL,
  `image_role` VARCHAR(40) DEFAULT NULL,
  `role_ordinal` INT DEFAULT NULL,
  `created_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_image_review_target_suite` (`suite_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
