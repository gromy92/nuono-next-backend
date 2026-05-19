CREATE TABLE IF NOT EXISTS `product_publish_task` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `baseline_snapshot_id` BIGINT DEFAULT NULL,
    `draft_snapshot_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(64) NOT NULL,
    `project_code` VARCHAR(64) DEFAULT NULL,
    `sku_parent` VARCHAR(100) NOT NULL,
    `partner_sku` VARCHAR(100) DEFAULT NULL,
    `psku_code` VARCHAR(100) DEFAULT NULL,
    `current_site_code` VARCHAR(64) DEFAULT NULL,
    `task_type` VARCHAR(50) NOT NULL DEFAULT 'publish-current',
    `status` VARCHAR(40) NOT NULL,
    `active_lock_key` VARCHAR(100) DEFAULT NULL,
    `idempotency_key` VARCHAR(180) NOT NULL,
    `draft_hash` VARCHAR(128) NOT NULL,
    `changed_domains_json` LONGTEXT DEFAULT NULL,
    `baseline_json` LONGTEXT NOT NULL,
    `draft_json` LONGTEXT NOT NULL,
    `request_json` LONGTEXT DEFAULT NULL,
    `result_json` LONGTEXT DEFAULT NULL,
    `error_code` VARCHAR(100) DEFAULT NULL,
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `verify_attempt_count` INT NOT NULL DEFAULT 0,
    `max_retry_count` INT NOT NULL DEFAULT 3,
    `version_no` INT NOT NULL DEFAULT 1,
    `next_run_at` DATETIME DEFAULT NULL,
    `locked_by` VARCHAR(100) DEFAULT NULL,
    `locked_at` DATETIME DEFAULT NULL,
    `submitted_at` DATETIME DEFAULT NULL,
    `verify_started_at` DATETIME DEFAULT NULL,
    `verify_finished_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_publish_task_active_lock` (`active_lock_key`),
    UNIQUE KEY `uk_product_publish_task_idempotency` (`idempotency_key`),
    KEY `idx_product_publish_task_draft_hash` (`product_master_id`, `draft_hash`),
    KEY `idx_product_publish_task_status_next` (`status`, `next_run_at`),
    KEY `idx_product_publish_task_master_status` (`product_master_id`, `status`),
    KEY `idx_product_publish_task_owner_store` (`owner_user_id`, `store_code`, `sku_parent`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES ('product_publish_task', 64000, NOW(), NOW())
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, 64000);

SET @ppt_add_active_lock_key := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_publish_task'
              AND COLUMN_NAME = 'active_lock_key'
        ),
        'SELECT 1',
        'ALTER TABLE `product_publish_task` ADD COLUMN `active_lock_key` VARCHAR(100) DEFAULT NULL AFTER `status`'
    )
);
PREPARE stmt FROM @ppt_add_active_lock_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ppt_add_active_lock_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_publish_task'
              AND INDEX_NAME = 'uk_product_publish_task_active_lock'
        ),
        'SELECT 1',
        'ALTER TABLE `product_publish_task` ADD UNIQUE KEY `uk_product_publish_task_active_lock` (`active_lock_key`)'
    )
);
PREPARE stmt FROM @ppt_add_active_lock_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
