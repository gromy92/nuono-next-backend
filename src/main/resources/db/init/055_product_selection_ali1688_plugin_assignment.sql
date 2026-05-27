-- Product selection 1688 plugin-assisted collection assignment boundary.
-- Assignment codes locate a current task only; Nuono bearer session remains the auth credential.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_ali1688_plugin_assignment` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `source_collection_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT DEFAULT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `active_assignment_key` VARCHAR(80) DEFAULT NULL,
    `assignment_code_hash` CHAR(64) NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'created',
    `expires_at` DATETIME NOT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(500) DEFAULT NULL,
    `raw_assignment_snapshot_json` LONGTEXT DEFAULT NULL,
    `submission_idempotency_key` VARCHAR(120) DEFAULT NULL,
    `submitted_candidate_count` INT NOT NULL DEFAULT 0,
    `accepted_candidate_count` INT NOT NULL DEFAULT 0,
    `rejected_candidate_count` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_selection_ali1688_plugin_current` (`active_assignment_key`),
    UNIQUE KEY `uk_product_selection_ali1688_plugin_code` (`assignment_code_hash`),
    UNIQUE KEY `uk_product_selection_ali1688_plugin_idempotency` (`id`, `submission_idempotency_key`),
    KEY `idx_product_selection_ali1688_plugin_task` (`task_id`, `status`, `is_deleted`),
    KEY `idx_product_selection_ali1688_plugin_store` (`logical_store_id`, `status`, `gmt_updated`),
    KEY `idx_product_selection_ali1688_plugin_expiry` (`status`, `expires_at`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_ali1688_plugin_assignment', GREATEST(COALESCE(MAX(`id`), 90000), 90000), NOW(), NOW()
FROM `product_selection_ali1688_plugin_assignment`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
