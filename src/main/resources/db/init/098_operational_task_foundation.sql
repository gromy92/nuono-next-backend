-- Durable operational task foundation.

CREATE TABLE IF NOT EXISTS `operational_task` (
    `id` BIGINT NOT NULL,
    `task_type` VARCHAR(64) NOT NULL,
    `owner_user_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `natural_key` VARCHAR(255) NOT NULL,
    `active_natural_slot` VARCHAR(384)
        GENERATED ALWAYS AS (
            CASE
                WHEN `status` IN ('QUEUED', 'RUNNING') THEN CONCAT(`task_type`, '|', `natural_key`)
                ELSE NULL
            END
        ) STORED,
    `status` VARCHAR(32) NOT NULL,
    `progress_percent` INT NOT NULL DEFAULT 0,
    `message` VARCHAR(1024) DEFAULT NULL,
    `payload_json` LONGTEXT DEFAULT NULL,
    `result_json` LONGTEXT DEFAULT NULL,
    `error_code` VARCHAR(128) DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_operational_task_active` (`active_natural_slot`),
    KEY `idx_operational_task_scope` (`owner_user_id`, `store_code`, `site_code`, `task_type`, `status`),
    KEY `idx_operational_task_status_updated` (`status`, `gmt_updated`),
    KEY `idx_operational_task_natural_key` (`task_type`, `natural_key`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operational_task_id_sequence` (
    `sequence_name` VARCHAR(100) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `operational_task_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT
    'operational_task',
    GREATEST(COALESCE(MAX(`id`) + 1, 150000), 150000),
    NOW(),
    NOW()
FROM `operational_task`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
