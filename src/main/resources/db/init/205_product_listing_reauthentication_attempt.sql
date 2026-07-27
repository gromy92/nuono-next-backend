-- Exact durable link between a Listing real-run task and shared Noon auth recovery.
--
-- Depends on 190_noon_shared_email_auth_recovery.sql. The row stores only recovery
-- fences and safe status metadata; mailbox secrets and provider payloads are excluded.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `product_listing_reauthentication_attempt` (
    `real_run_task_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `draft_id` BIGINT NOT NULL,
    `project_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `recovery_id` BIGINT NOT NULL,
    `recovery_item_id` BIGINT NOT NULL,
    `requested_auth_version` BIGINT NOT NULL,
    `resume_action` VARCHAR(40) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `version_no` BIGINT NOT NULL DEFAULT 0,
    `failure_code` VARCHAR(80) DEFAULT NULL,
    `requested_at` DATETIME NOT NULL,
    `completed_at` DATETIME DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`real_run_task_id`),
    KEY `idx_listing_reauth_owner_status` (`owner_user_id`, `status`, `gmt_updated`),
    KEY `idx_listing_reauth_recovery` (`recovery_id`, `owner_user_id`, `project_code`),
    KEY `idx_listing_reauth_item` (`recovery_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
