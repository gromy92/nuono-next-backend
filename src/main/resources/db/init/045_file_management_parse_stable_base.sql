-- Stable file management parse base.
-- Adds first-class process data for source extraction, AI chunks, validation issues and source-result lineage.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_source_row` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `task_input_id` BIGINT NOT NULL,
    `file_asset_id` BIGINT DEFAULT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `source_locator` VARCHAR(200) DEFAULT NULL,
    `page_no` INT DEFAULT NULL,
    `sheet_name` VARCHAR(120) DEFAULT NULL,
    `table_no` INT DEFAULT NULL,
    `row_no` INT DEFAULT NULL,
    `column_range` VARCHAR(80) DEFAULT NULL,
    `raw_text` LONGTEXT DEFAULT NULL,
    `raw_cells_json` LONGTEXT DEFAULT NULL,
    `source_hash` VARCHAR(128) NOT NULL,
    `extractor_type` VARCHAR(80) DEFAULT NULL,
    `extractor_version` VARCHAR(80) DEFAULT NULL,
    `sort_no` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_source_row_hash` (`task_id`, `task_input_id`, `source_hash`),
    KEY `idx_file_mgmt_parse_source_row_task` (`task_id`, `sort_no`),
    KEY `idx_file_mgmt_parse_source_row_input` (`task_input_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_ai_chunk` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `result_id` BIGINT DEFAULT NULL,
    `chunk_no` INT NOT NULL,
    `chunk_type` VARCHAR(40) NOT NULL,
    `source_row_ids_json` LONGTEXT DEFAULT NULL,
    `source_row_count` INT NOT NULL DEFAULT 0,
    `prompt_hash` VARCHAR(128) DEFAULT NULL,
    `input_hash` VARCHAR(128) DEFAULT NULL,
    `model_provider` VARCHAR(80) DEFAULT NULL,
    `model_name` VARCHAR(120) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'pending',
    `output_item_count` INT DEFAULT NULL,
    `response_hash` VARCHAR(128) DEFAULT NULL,
    `raw_response_json` LONGTEXT DEFAULT NULL,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(1000) DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_ai_chunk_no` (`task_id`, `chunk_no`),
    KEY `idx_file_mgmt_parse_ai_chunk_task` (`task_id`, `status`, `chunk_no`),
    KEY `idx_file_mgmt_parse_ai_chunk_result` (`result_id`, `chunk_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_validation_issue` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `result_id` BIGINT DEFAULT NULL,
    `result_item_id` BIGINT DEFAULT NULL,
    `source_row_id` BIGINT DEFAULT NULL,
    `ai_chunk_id` BIGINT DEFAULT NULL,
    `issue_type` VARCHAR(60) NOT NULL,
    `severity` VARCHAR(30) NOT NULL DEFAULT 'warning',
    `field_key` VARCHAR(120) DEFAULT NULL,
    `message` VARCHAR(1000) DEFAULT NULL,
    `details_json` LONGTEXT DEFAULT NULL,
    `resolved_status` VARCHAR(30) NOT NULL DEFAULT 'open',
    `resolved_by` BIGINT DEFAULT NULL,
    `resolved_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_file_mgmt_parse_validation_task` (`task_id`, `severity`, `issue_type`),
    KEY `idx_file_mgmt_parse_validation_result` (`result_id`, `severity`),
    KEY `idx_file_mgmt_parse_validation_item` (`result_item_id`),
    KEY `idx_file_mgmt_parse_validation_source` (`source_row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_result_item_source` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `result_id` BIGINT NOT NULL,
    `result_item_id` BIGINT NOT NULL,
    `source_row_id` BIGINT NOT NULL,
    `source_role` VARCHAR(40) NOT NULL DEFAULT 'primary',
    `confidence` VARCHAR(20) DEFAULT NULL,
    `evidence_text` VARCHAR(1000) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_result_item_source` (`result_item_id`, `source_row_id`, `source_role`),
    KEY `idx_file_mgmt_parse_result_item_source_item` (`result_item_id`),
    KEY `idx_file_mgmt_parse_result_item_source_row` (`source_row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `file_mgmt_parse_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('file_mgmt_parse_source_row', 35000, NOW(), NOW()),
    ('file_mgmt_parse_ai_chunk', 36000, NOW(), NOW()),
    ('file_mgmt_parse_result_item_source', 55000, NOW(), NOW()),
    ('file_mgmt_parse_validation_issue', 56000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
