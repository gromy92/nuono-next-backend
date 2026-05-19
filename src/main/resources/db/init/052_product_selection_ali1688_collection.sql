-- Product selection 1688 candidate collection V1.
-- Re-created after 051 scope cleanup, using product_management_id_sequence for IDs.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_ali1688_collection_task` (
    `id` BIGINT NOT NULL,
    `source_collection_id` BIGINT NOT NULL,
    `current_task_key` VARCHAR(80) DEFAULT NULL,
    `owner_user_id` BIGINT DEFAULT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `task_no` VARCHAR(60) NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'waiting_source',
    `progress_percent` INT NOT NULL DEFAULT 0,
    `search_mode` VARCHAR(40) DEFAULT NULL,
    `source_image_url` VARCHAR(1000) DEFAULT NULL,
    `selected_image_count` INT NOT NULL DEFAULT 0,
    `scanned_count` INT NOT NULL DEFAULT 0,
    `candidate_count` INT NOT NULL DEFAULT 0,
    `recommended_count` INT NOT NULL DEFAULT 0,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(500) DEFAULT NULL,
    `official_search_url` VARCHAR(1000) DEFAULT NULL,
    `search_image_id` VARCHAR(120) DEFAULT NULL,
    `search_image_id_list_json` TEXT DEFAULT NULL,
    `raw_search_snapshot_json` LONGTEXT DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `locked_at` DATETIME DEFAULT NULL,
    `locked_by` VARCHAR(120) DEFAULT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_selection_ali1688_current_task` (`current_task_key`),
    UNIQUE KEY `uk_product_selection_ali1688_task_no` (`task_no`),
    KEY `idx_product_selection_ali1688_source` (`source_collection_id`, `is_deleted`),
    KEY `idx_product_selection_ali1688_store_status` (`logical_store_id`, `status`, `gmt_updated`),
    KEY `idx_product_selection_ali1688_claim` (`status`, `locked_at`, `attempt_count`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_ali1688_candidate` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `source_collection_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT DEFAULT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `rank_no` INT NOT NULL DEFAULT 0,
    `selected_rank_no` INT DEFAULT NULL,
    `level` VARCHAR(30) NOT NULL DEFAULT 'review',
    `offer_id` VARCHAR(100) DEFAULT NULL,
    `candidate_url` VARCHAR(1000) DEFAULT NULL,
    `candidate_url_hash` CHAR(64) DEFAULT NULL,
    `active_candidate_key` VARCHAR(180) DEFAULT NULL,
    `title` VARCHAR(500) DEFAULT NULL,
    `supplier_name` VARCHAR(300) DEFAULT NULL,
    `price_text` VARCHAR(200) DEFAULT NULL,
    `price_min` DECIMAL(18,4) DEFAULT NULL,
    `price_max` DECIMAL(18,4) DEFAULT NULL,
    `moq_text` VARCHAR(200) DEFAULT NULL,
    `moq_value` INT DEFAULT NULL,
    `location_text` VARCHAR(200) DEFAULT NULL,
    `main_image_url` VARCHAR(1000) DEFAULT NULL,
    `image_urls_json` TEXT DEFAULT NULL,
    `badges_json` TEXT DEFAULT NULL,
    `sku_snapshot_json` LONGTEXT DEFAULT NULL,
    `supplier_snapshot_json` LONGTEXT DEFAULT NULL,
    `logistics_snapshot_json` LONGTEXT DEFAULT NULL,
    `rule_score` INT DEFAULT NULL,
    `total_score` INT DEFAULT NULL,
    `match_score` INT DEFAULT NULL,
    `spec_score` INT DEFAULT NULL,
    `price_score` INT DEFAULT NULL,
    `moq_score` INT DEFAULT NULL,
    `supplier_score` INT DEFAULT NULL,
    `delivery_score` INT DEFAULT NULL,
    `score_status` VARCHAR(30) NOT NULL DEFAULT 'pending',
    `score_version` VARCHAR(80) DEFAULT NULL,
    `score_detail_json` TEXT DEFAULT NULL,
    `ai_assessment_status` VARCHAR(30) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_selection_ali1688_active_candidate` (`active_candidate_key`),
    KEY `idx_product_selection_ali1688_candidate_task` (`task_id`, `rank_no`, `is_deleted`),
    KEY `idx_product_selection_ali1688_candidate_source` (`source_collection_id`, `is_deleted`),
    KEY `idx_product_selection_ali1688_candidate_selected` (`task_id`, `selected_rank_no`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_ali1688_candidate_ai_assessment` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'pending',
    `feature_code` VARCHAR(80) NOT NULL,
    `operation_code` VARCHAR(80) NOT NULL,
    `prompt_version` VARCHAR(80) DEFAULT NULL,
    `schema_version` VARCHAR(80) DEFAULT NULL,
    `model_name` VARCHAR(120) DEFAULT NULL,
    `input_hash` CHAR(64) DEFAULT NULL,
    `input_snapshot_json` LONGTEXT DEFAULT NULL,
    `output_json` LONGTEXT DEFAULT NULL,
    `match_score` INT DEFAULT NULL,
    `spec_score` INT DEFAULT NULL,
    `risk_level` VARCHAR(30) DEFAULT NULL,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(500) DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `locked_at` DATETIME DEFAULT NULL,
    `locked_by` VARCHAR(120) DEFAULT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `next_run_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_selection_ali1688_ai_input` (`candidate_id`, `feature_code`, `operation_code`, `input_hash`),
    KEY `idx_product_selection_ali1688_ai_task` (`task_id`, `status`, `is_deleted`),
    KEY `idx_product_selection_ali1688_ai_claim` (`status`, `next_run_at`, `locked_at`, `attempt_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_ali1688_collection_task', GREATEST(COALESCE(MAX(`id`), 87000), 87000), NOW(), NOW()
FROM `product_selection_ali1688_collection_task`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_ali1688_candidate', GREATEST(COALESCE(MAX(`id`), 88000), 88000), NOW(), NOW()
FROM `product_selection_ali1688_candidate`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_ali1688_candidate_ai_assessment', GREATEST(COALESCE(MAX(`id`), 89000), 89000), NOW(), NOW()
FROM `product_selection_ali1688_candidate_ai_assessment`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
