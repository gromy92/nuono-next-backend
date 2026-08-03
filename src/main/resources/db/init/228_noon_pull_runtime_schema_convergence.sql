-- Converge the schema formerly created by Noon smoke and scheduler request paths.
CREATE TABLE IF NOT EXISTS `noon_pull_id_sequence` (
    `sequence_name` VARCHAR(100) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `noon_pull_smoke_run` (
    `id` BIGINT NOT NULL,
    `target_environment` VARCHAR(64) NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) DEFAULT NULL,
    `project_name` VARCHAR(255) DEFAULT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `rollback_global_pause_strategy` VARCHAR(1000) DEFAULT NULL,
    `requested_domains` VARCHAR(255) DEFAULT NULL,
    `missing_requirements` VARCHAR(1000) DEFAULT NULL,
    `evidence_gate_satisfied` BIT(1) NOT NULL DEFAULT b'0',
    `production_scheduling_allowed` BIT(1) NOT NULL DEFAULT b'0',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_noon_pull_smoke_run_scope`
        (`target_environment`, `owner_user_id`, `store_code`, `site_code`),
    KEY `idx_noon_pull_smoke_run_created` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `noon_pull_smoke_evidence` (
    `id` BIGINT NOT NULL,
    `run_id` BIGINT NOT NULL,
    `sequence_no` INT NOT NULL,
    `data_domain` VARCHAR(32) NOT NULL,
    `target_identity` VARCHAR(255) DEFAULT NULL,
    `date_from` DATE DEFAULT NULL,
    `date_to` DATE DEFAULT NULL,
    `row_or_item_count` INT DEFAULT NULL,
    `task_id` BIGINT DEFAULT NULL,
    `source_batch_id` VARCHAR(160) DEFAULT NULL,
    `file_digest_sha256` VARCHAR(128) DEFAULT NULL,
    `request_count` INT DEFAULT NULL,
    `elapsed_millis` BIGINT DEFAULT NULL,
    `latest_fact_date` DATE DEFAULT NULL,
    `status` VARCHAR(32) DEFAULT NULL,
    `quality_state` VARCHAR(64) DEFAULT NULL,
    `failure_classification` VARCHAR(80) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_noon_pull_smoke_evidence_run` (`run_id`, `sequence_no`),
    KEY `idx_noon_pull_smoke_evidence_task` (`task_id`),
    KEY `idx_noon_pull_smoke_evidence_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `noon_production_scheduler_enablement` (
    `id` BIGINT NOT NULL,
    `target_environment` VARCHAR(64) NOT NULL,
    `owner_user_id` BIGINT DEFAULT NULL,
    `project_code` VARCHAR(100) DEFAULT NULL,
    `project_name` VARCHAR(255) DEFAULT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `enabled_domains` VARCHAR(255) DEFAULT NULL,
    `schedule_boundaries` VARCHAR(1000) DEFAULT NULL,
    `rollback_global_pause_strategy` VARCHAR(1000) DEFAULT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `smoke_run_id` BIGINT DEFAULT NULL,
    `decision` VARCHAR(32) NOT NULL,
    `rejection_reasons` VARCHAR(1000) DEFAULT NULL,
    `plan_ids` VARCHAR(500) DEFAULT NULL,
    `hitl_approved` BIT(1) NOT NULL DEFAULT b'0',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_noon_scheduler_enablement_scope`
        (`target_environment`, `owner_user_id`, `store_code`, `site_code`),
    KEY `idx_noon_scheduler_enablement_smoke` (`smoke_run_id`),
    KEY `idx_noon_scheduler_enablement_decision` (`decision`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- next_id stores the last allocated value. The floors preserve the historical
-- first IDs while MAX(id) prevents a restored/partially seeded schema from
-- allocating an existing primary key.
INSERT INTO `noon_pull_id_sequence`
    (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    (
        'noon_pull_smoke_run',
        GREATEST(
            139999,
            COALESCE((SELECT MAX(`id`) FROM `noon_pull_smoke_run`), 139999)
        ),
        NOW(),
        NOW()
    ),
    (
        'noon_pull_smoke_evidence',
        GREATEST(
            140999,
            COALESCE((SELECT MAX(`id`) FROM `noon_pull_smoke_evidence`), 140999)
        ),
        NOW(),
        NOW()
    ),
    (
        'noon_production_scheduler_enablement',
        GREATEST(
            141999,
            COALESCE(
                (SELECT MAX(`id`) FROM `noon_production_scheduler_enablement`),
                141999
            )
        ),
        NOW(),
        NOW()
    ) AS `incoming`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(
        `noon_pull_id_sequence`.`next_id`,
        `incoming`.`next_id`
    ),
    `gmt_updated` = NOW();
