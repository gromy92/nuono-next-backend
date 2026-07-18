-- AI product-image generation, review, rework, and Noon publication workflow.

SET NAMES utf8mb4;

ALTER TABLE `product_image_suite`
  ADD COLUMN `parent_suite_id` BIGINT DEFAULT NULL AFTER `profile_id`,
  ADD COLUMN `revision_no` INT NOT NULL DEFAULT 1 AFTER `parent_suite_id`,
  ADD COLUMN `review_comment` VARCHAR(2000) DEFAULT NULL AFTER `suite_status`,
  ADD COLUMN `reviewed_by` BIGINT DEFAULT NULL AFTER `review_comment`,
  ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL AFTER `reviewed_by`,
  ADD COLUMN `failure_stage` VARCHAR(32) DEFAULT NULL AFTER `reviewed_at`,
  ADD COLUMN `failure_reason` VARCHAR(2000) DEFAULT NULL AFTER `failure_stage`,
  ADD COLUMN `published_at` DATETIME DEFAULT NULL AFTER `failure_reason`,
  ADD COLUMN `publish_manifest_json` LONGTEXT DEFAULT NULL AFTER `published_at`,
  ADD KEY `idx_product_image_suite_generation_task` (`generation_task_id`),
  ADD KEY `idx_product_image_suite_parent_revision` (`parent_suite_id`, `revision_no`);

ALTER TABLE `product_image_suite_asset`
  ADD COLUMN `role_ordinal` INT NOT NULL DEFAULT 1 AFTER `image_role`,
  ADD COLUMN `content_type` VARCHAR(100) DEFAULT NULL AFTER `image_url`,
  ADD COLUMN `size_bytes` BIGINT DEFAULT NULL AFTER `content_type`,
  ADD COLUMN `sha256` VARCHAR(64) DEFAULT NULL AFTER `size_bytes`,
  ADD UNIQUE KEY `uk_product_image_suite_asset_slot` (`suite_id`, `image_role`, `role_ordinal`);

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
