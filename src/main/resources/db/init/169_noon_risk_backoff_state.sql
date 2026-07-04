-- Durable Noon risk-control backoff state shared by online Noon pull tasks.

CREATE TABLE IF NOT EXISTS `noon_risk_backoff_state` (
    `scope_key` VARCHAR(512) NOT NULL,
    `scope_type` VARCHAR(64) NOT NULL,
    `owner_user_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `operation_group` VARCHAR(64) NOT NULL,
    `risk_type` VARCHAR(64) NOT NULL,
    `source_domain` VARCHAR(64) DEFAULT NULL,
    `source_task_id` BIGINT DEFAULT NULL,
    `blocked_until` DATETIME NOT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `diagnostic_summary` VARCHAR(2000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`scope_key`),
    KEY `idx_noon_risk_backoff_active` (`scope_key`, `blocked_until`, `is_deleted`),
    KEY `idx_noon_risk_backoff_latest` (`scope_key`, `gmt_updated`),
    KEY `idx_noon_risk_backoff_scope` (`owner_user_id`, `store_code`, `site_code`, `operation_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
