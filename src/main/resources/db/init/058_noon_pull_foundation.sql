-- Noon backoffice generic pull foundation.

CREATE TABLE IF NOT EXISTS `noon_pull_id_sequence` (
    `sequence_name` VARCHAR(100) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `noon_pull_plan` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `pull_type` VARCHAR(32) NOT NULL,
    `data_domain` VARCHAR(32) NOT NULL,
    `trigger_mode` VARCHAR(64) NOT NULL,
    `schedule_expression` VARCHAR(128) DEFAULT NULL,
    `enabled` BIT(1) NOT NULL DEFAULT b'1',
    `paused` BIT(1) NOT NULL DEFAULT b'0',
    `pause_reason` VARCHAR(500) DEFAULT NULL,
    `latest_success_at` DATETIME DEFAULT NULL,
    `latest_failure_at` DATETIME DEFAULT NULL,
    `latest_failure_type` VARCHAR(64) DEFAULT NULL,
    `next_retry_at` DATETIME DEFAULT NULL,
    `max_pages_per_run` INT DEFAULT NULL,
    `max_products_per_run` INT DEFAULT NULL,
    `max_detail_fetches_per_run` INT DEFAULT NULL,
    `max_requests_per_run` INT DEFAULT NULL,
    `cooldown_seconds` INT DEFAULT NULL,
    `concurrency_limit` INT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_noon_pull_plan_scope` (`owner_user_id`, `store_code`, `site_code`, `data_domain`, `pull_type`),
    KEY `idx_noon_pull_plan_due` (`enabled`, `paused`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `noon_pull_task` (
    `id` BIGINT NOT NULL,
    `plan_id` BIGINT DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `pull_type` VARCHAR(32) NOT NULL,
    `data_domain` VARCHAR(32) NOT NULL,
    `trigger_mode` VARCHAR(64) NOT NULL,
    `target_identity` VARCHAR(255) DEFAULT NULL,
    `target_date_from` DATE DEFAULT NULL,
    `target_date_to` DATE DEFAULT NULL,
    `active_lock_key` VARCHAR(512) NOT NULL,
    `active_lock_slot` VARCHAR(512)
        GENERATED ALWAYS AS (
            CASE
                WHEN `status` IN ('QUEUED', 'RUNNING') THEN `active_lock_key`
                ELSE NULL
            END
        ) STORED,
    `status` VARCHAR(32) NOT NULL,
    `source_batch_id` VARCHAR(100) DEFAULT NULL,
    `failure_type` VARCHAR(64) DEFAULT NULL,
    `retry_action` VARCHAR(32) DEFAULT NULL,
    `retryable` BIT(1) DEFAULT NULL,
    `requires_manual_action` BIT(1) DEFAULT NULL,
    `diagnostic_summary` VARCHAR(2000) DEFAULT NULL,
    `checkpoint_cursor` VARCHAR(128) DEFAULT NULL,
    `processed_item_count` INT DEFAULT NULL,
    `request_count` INT DEFAULT NULL,
    `next_resume_position` VARCHAR(128) DEFAULT NULL,
    `last_safe_response_summary` VARCHAR(2000) DEFAULT NULL,
    `readiness_state` VARCHAR(64) DEFAULT NULL,
    `report_export_id` VARCHAR(160) DEFAULT NULL,
    `report_export_status` VARCHAR(64) DEFAULT NULL,
    `report_download_url` VARCHAR(1200) DEFAULT NULL,
    `report_total_rows` INT DEFAULT NULL,
    `report_last_poll_at` DATETIME DEFAULT NULL,
    `report_next_poll_at` DATETIME DEFAULT NULL,
    `report_poll_attempts` INT DEFAULT NULL,
    `locked_by` VARCHAR(100) DEFAULT NULL,
    `queued_at` DATETIME DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_pull_active_lock` (`active_lock_slot`),
    KEY `idx_noon_pull_task_plan` (`plan_id`),
    KEY `idx_noon_pull_task_scope` (`owner_user_id`, `store_code`, `site_code`, `data_domain`, `pull_type`),
    KEY `idx_noon_pull_task_status` (`status`, `queued_at`),
    KEY `idx_noon_pull_task_report_export` (`report_export_id`),
    KEY `idx_noon_pull_task_next_poll` (`status`, `report_next_poll_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
