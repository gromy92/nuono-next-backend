-- Noon smoke evidence persistence for administrator review.

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
    KEY `idx_noon_pull_smoke_run_scope` (`target_environment`, `owner_user_id`, `store_code`, `site_code`),
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
