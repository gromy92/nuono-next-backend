-- Durable project-auth transport backoff. Each logical store and exact error type
-- owns an independent counter and retry deadline.

CREATE TABLE IF NOT EXISTS `noon_auth_transient_backoff_state` (
    `logical_store_id` BIGINT NOT NULL,
    `error_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) NOT NULL,
    `last_store_code` VARCHAR(100) DEFAULT NULL,
    `source_stage` VARCHAR(64) NOT NULL,
    `source_recovery_id` BIGINT NOT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `blocked_until` DATETIME NOT NULL,
    `last_failed_at` DATETIME NOT NULL,
    `last_success_at` DATETIME DEFAULT NULL,
    `diagnostic_summary` VARCHAR(1000) DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`logical_store_id`, `error_type`),
    KEY `idx_noon_auth_transient_backoff_active`
        (`logical_store_id`, `blocked_until`, `attempt_count`),
    KEY `idx_noon_auth_transient_backoff_recovery`
        (`source_recovery_id`, `logical_store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
