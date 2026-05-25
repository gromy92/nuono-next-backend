-- File management generic parse V1.
-- This script creates the generic parse foundation and seeds the first five target output plans.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_id_sequence` (
    `sequence_name` VARCHAR(100) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_standard` (
    `id` BIGINT NOT NULL,
    `document_type` VARCHAR(80) NOT NULL,
    `document_name` VARCHAR(120) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `active_version_id` BIGINT DEFAULT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'draft',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_standard_type` (`document_type`),
    KEY `idx_file_mgmt_parse_standard_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_standard_version` (
    `id` BIGINT NOT NULL,
    `standard_id` BIGINT NOT NULL,
    `standard_version` VARCHAR(60) NOT NULL,
    `input_schema_json` LONGTEXT DEFAULT NULL,
    `result_schema_json` LONGTEXT DEFAULT NULL,
    `normalization_rule_json` LONGTEXT DEFAULT NULL,
    `validation_rule_json` LONGTEXT DEFAULT NULL,
    `display_config_json` LONGTEXT DEFAULT NULL,
    `diff_rule_json` LONGTEXT DEFAULT NULL,
    `review_action_json` LONGTEXT DEFAULT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'draft',
    `published_at` DATETIME DEFAULT NULL,
    `published_by` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_standard_version` (`standard_id`, `standard_version`),
    KEY `idx_file_mgmt_parse_standard_version_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_item_standard` (
    `id` BIGINT NOT NULL,
    `standard_version_id` BIGINT NOT NULL,
    `item_type` VARCHAR(80) NOT NULL,
    `item_label` VARCHAR(120) NOT NULL,
    `natural_key_json` LONGTEXT DEFAULT NULL,
    `field_schema_json` LONGTEXT DEFAULT NULL,
    `display_config_json` LONGTEXT DEFAULT NULL,
    `validation_rule_json` LONGTEXT DEFAULT NULL,
    `diff_rule_json` LONGTEXT DEFAULT NULL,
    `sort_no` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(30) NOT NULL DEFAULT 'active',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_item_standard_type` (`standard_version_id`, `item_type`),
    KEY `idx_file_mgmt_parse_item_standard_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_target_plan` (
    `id` BIGINT NOT NULL,
    `plan_code` VARCHAR(80) NOT NULL,
    `plan_label` VARCHAR(120) NOT NULL,
    `standard_id` BIGINT NOT NULL,
    `standard_version_id` BIGINT NOT NULL,
    `document_type` VARCHAR(80) NOT NULL,
    `document_name` VARCHAR(120) NOT NULL,
    `business_scope_code` VARCHAR(120) DEFAULT NULL,
    `business_scope_label` VARCHAR(200) DEFAULT NULL,
    `publish_adapter` VARCHAR(120) DEFAULT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `sort_no` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(30) NOT NULL DEFAULT 'active',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_target_plan_code` (`plan_code`),
    KEY `idx_file_mgmt_parse_target_plan_standard` (`standard_version_id`),
    KEY `idx_file_mgmt_parse_target_plan_status` (`status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_target_plan_scope` (
    `id` BIGINT NOT NULL,
    `target_plan_id` BIGINT NOT NULL,
    `scope_type` VARCHAR(40) NOT NULL,
    `scope_value` VARCHAR(160) NOT NULL,
    `scope_label` VARCHAR(160) DEFAULT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'active',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_target_plan_scope` (`target_plan_id`, `scope_type`, `scope_value`),
    KEY `idx_file_mgmt_parse_target_plan_scope_lookup` (`scope_type`, `scope_value`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_file_asset` (
    `id` BIGINT NOT NULL,
    `upload_id` VARCHAR(80) DEFAULT NULL,
    `target_plan_id` BIGINT DEFAULT NULL,
    `standard_version_id` BIGINT DEFAULT NULL,
    `original_file_name` VARCHAR(300) NOT NULL,
    `content_type` VARCHAR(120) DEFAULT NULL,
    `file_extension` VARCHAR(30) DEFAULT NULL,
    `file_size_bytes` BIGINT DEFAULT NULL,
    `sha256_hash` VARCHAR(128) DEFAULT NULL,
    `storage_bucket` VARCHAR(120) DEFAULT NULL,
    `storage_key` VARCHAR(500) NOT NULL,
    `bound_task_id` BIGINT DEFAULT NULL,
    `uploaded_by` BIGINT DEFAULT NULL,
    `expires_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_file_asset_upload` (`upload_id`),
    KEY `idx_file_mgmt_parse_file_asset_task` (`bound_task_id`),
    KEY `idx_file_mgmt_parse_file_asset_hash` (`sha256_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_task` (
    `id` BIGINT NOT NULL,
    `task_no` VARCHAR(80) NOT NULL,
    `document_title` VARCHAR(200) NOT NULL,
    `target_plan_id` BIGINT NOT NULL,
    `standard_version_id` BIGINT NOT NULL,
    `data_scope_type` VARCHAR(40) NOT NULL DEFAULT 'global',
    `data_scope_key` VARCHAR(160) NOT NULL DEFAULT 'global:*',
    `data_owner_user_id` BIGINT DEFAULT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(120) DEFAULT NULL,
    `project_code` VARCHAR(120) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'reading',
    `base_version_id` BIGINT DEFAULT NULL,
    `document_group_id` BIGINT DEFAULT NULL,
    `parent_task_id` BIGINT DEFAULT NULL,
    `iteration_no` INT NOT NULL DEFAULT 1,
    `current_result_id` BIGINT DEFAULT NULL,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(1000) DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `idempotency_key` VARCHAR(180) DEFAULT NULL,
    `request_hash` VARCHAR(128) DEFAULT NULL,
    `parse_attempt_count` INT NOT NULL DEFAULT 0,
    `next_run_at` DATETIME DEFAULT NULL,
    `locked_by` VARCHAR(100) DEFAULT NULL,
    `locked_at` DATETIME DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_task_no` (`task_no`),
    UNIQUE KEY `uk_file_mgmt_parse_task_idempotency` (`idempotency_key`),
    KEY `idx_file_mgmt_parse_task_plan_status` (`target_plan_id`, `status`),
    KEY `idx_file_mgmt_parse_task_scope` (`target_plan_id`, `data_scope_type`, `data_scope_key`),
    KEY `idx_file_mgmt_parse_task_group` (`document_group_id`, `iteration_no`, `id`),
    KEY `idx_file_mgmt_parse_task_run` (`status`, `next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_task_input` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `input_type` VARCHAR(30) NOT NULL,
    `input_role` VARCHAR(40) NOT NULL DEFAULT 'primary_source',
    `file_asset_id` BIGINT DEFAULT NULL,
    `text_content` LONGTEXT DEFAULT NULL,
    `display_name` VARCHAR(300) DEFAULT NULL,
    `sort_no` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_file_mgmt_parse_task_input_task` (`task_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_result` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `result_no` VARCHAR(80) NOT NULL,
    `target_plan_id` BIGINT NOT NULL,
    `standard_version_id` BIGINT NOT NULL,
    `base_version_id` BIGINT DEFAULT NULL,
    `data_scope_type` VARCHAR(40) NOT NULL DEFAULT 'global',
    `data_scope_key` VARCHAR(160) NOT NULL DEFAULT 'global:*',
    `parser_type` VARCHAR(40) NOT NULL DEFAULT 'ai_mixed',
    `parser_model` VARCHAR(120) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'created',
    `summary_json` LONGTEXT DEFAULT NULL,
    `raw_result_json` LONGTEXT DEFAULT NULL,
    `validation_summary_json` LONGTEXT DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_result_no` (`result_no`),
    KEY `idx_file_mgmt_parse_result_task` (`task_id`),
    KEY `idx_file_mgmt_parse_result_plan_scope` (`target_plan_id`, `data_scope_type`, `data_scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_result_item` (
    `id` BIGINT NOT NULL,
    `result_id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `target_plan_id` BIGINT NOT NULL,
    `item_type` VARCHAR(80) NOT NULL,
    `natural_key` VARCHAR(500) NOT NULL,
    `natural_key_hash` VARCHAR(128) NOT NULL,
    `change_type` VARCHAR(40) NOT NULL DEFAULT 'unchanged',
    `review_status` VARCHAR(40) NOT NULL DEFAULT 'pending',
    `current_review_id` BIGINT DEFAULT NULL,
    `current_marker` VARCHAR(40) DEFAULT NULL,
    `confidence` VARCHAR(20) DEFAULT NULL,
    `validation_status` VARCHAR(40) DEFAULT NULL,
    `normalized_payload_json` LONGTEXT DEFAULT NULL,
    `old_payload_json` LONGTEXT DEFAULT NULL,
    `changed_field_keys_json` LONGTEXT DEFAULT NULL,
    `effective_payload_json` LONGTEXT DEFAULT NULL,
    `effective_validation_status` VARCHAR(40) DEFAULT NULL,
    `effective_payload_hash` VARCHAR(128) DEFAULT NULL,
    `evidence_json` LONGTEXT DEFAULT NULL,
    `validation_error_json` LONGTEXT DEFAULT NULL,
    `sort_no` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_result_item_key` (`result_id`, `item_type`, `natural_key_hash`),
    KEY `idx_file_mgmt_parse_result_item_task` (`task_id`, `review_status`),
    KEY `idx_file_mgmt_parse_result_item_change` (`change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_item_review` (
    `id` BIGINT NOT NULL,
    `result_item_id` BIGINT NOT NULL,
    `result_id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `review_action` VARCHAR(40) NOT NULL,
    `review_status` VARCHAR(40) NOT NULL,
    `override_payload_json` LONGTEXT DEFAULT NULL,
    `effective_payload_json` LONGTEXT DEFAULT NULL,
    `validation_status` VARCHAR(40) DEFAULT NULL,
    `validation_message` VARCHAR(1000) DEFAULT NULL,
    `review_note` VARCHAR(1000) DEFAULT NULL,
    `expected_result_id` BIGINT NOT NULL,
    `idempotency_key` VARCHAR(180) DEFAULT NULL,
    `request_hash` VARCHAR(128) DEFAULT NULL,
    `is_current` BIT(1) NOT NULL DEFAULT b'1',
    `current_marker` VARCHAR(40) DEFAULT 'current',
    `reviewed_by` BIGINT DEFAULT NULL,
    `reviewed_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_item_review_idem` (`task_id`, `result_item_id`, `idempotency_key`),
    UNIQUE KEY `uk_file_mgmt_parse_item_review_current` (`result_item_id`, `current_marker`),
    KEY `idx_file_mgmt_parse_item_review_item` (`result_item_id`, `is_current`),
    KEY `idx_file_mgmt_parse_item_review_task` (`task_id`, `review_action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_file_parse_result_item_old_payload := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_result_item'
              AND COLUMN_NAME = 'old_payload_json'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_result_item` ADD COLUMN `old_payload_json` LONGTEXT DEFAULT NULL AFTER `normalized_payload_json`'
    )
);
PREPARE stmt FROM @add_file_parse_result_item_old_payload;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_result_item_changed_keys := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_result_item'
              AND COLUMN_NAME = 'changed_field_keys_json'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_result_item` ADD COLUMN `changed_field_keys_json` LONGTEXT DEFAULT NULL AFTER `old_payload_json`'
    )
);
PREPARE stmt FROM @add_file_parse_result_item_changed_keys;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_result_item_effective_payload := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_result_item'
              AND COLUMN_NAME = 'effective_payload_json'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_result_item` ADD COLUMN `effective_payload_json` LONGTEXT DEFAULT NULL AFTER `changed_field_keys_json`'
    )
);
PREPARE stmt FROM @add_file_parse_result_item_effective_payload;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_result_item_effective_validation := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_result_item'
              AND COLUMN_NAME = 'effective_validation_status'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_result_item` ADD COLUMN `effective_validation_status` VARCHAR(40) DEFAULT NULL AFTER `effective_payload_json`'
    )
);
PREPARE stmt FROM @add_file_parse_result_item_effective_validation;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_result_item_effective_hash := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_result_item'
              AND COLUMN_NAME = 'effective_payload_hash'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_result_item` ADD COLUMN `effective_payload_hash` VARCHAR(128) DEFAULT NULL AFTER `effective_validation_status`'
    )
);
PREPARE stmt FROM @add_file_parse_result_item_effective_hash;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_review_effective_payload := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_item_review'
              AND COLUMN_NAME = 'effective_payload_json'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_item_review` ADD COLUMN `effective_payload_json` LONGTEXT DEFAULT NULL AFTER `override_payload_json`'
    )
);
PREPARE stmt FROM @add_file_parse_review_effective_payload;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_review_validation_status := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_item_review'
              AND COLUMN_NAME = 'validation_status'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_item_review` ADD COLUMN `validation_status` VARCHAR(40) DEFAULT NULL AFTER `effective_payload_json`'
    )
);
PREPARE stmt FROM @add_file_parse_review_validation_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_review_validation_message := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_item_review'
              AND COLUMN_NAME = 'validation_message'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_item_review` ADD COLUMN `validation_message` VARCHAR(1000) DEFAULT NULL AFTER `validation_status`'
    )
);
PREPARE stmt FROM @add_file_parse_review_validation_message;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_review_current_marker := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_item_review'
              AND COLUMN_NAME = 'current_marker'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_item_review` ADD COLUMN `current_marker` VARCHAR(40) DEFAULT NULL AFTER `is_current`'
    )
);
PREPARE stmt FROM @add_file_parse_review_current_marker;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_old_file_parse_review_idem_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_item_review'
              AND INDEX_NAME = 'uk_file_mgmt_parse_item_review_idem'
              AND COLUMN_NAME = 'idempotency_key'
              AND SEQ_IN_INDEX = 1
        )
        AND NOT EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_item_review'
              AND INDEX_NAME = 'uk_file_mgmt_parse_item_review_idem'
              AND COLUMN_NAME = 'task_id'
        ),
        'ALTER TABLE `file_mgmt_parse_item_review` DROP INDEX `uk_file_mgmt_parse_item_review_idem`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_old_file_parse_review_idem_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_review_idem_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_item_review'
              AND INDEX_NAME = 'uk_file_mgmt_parse_item_review_idem'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_item_review` ADD UNIQUE KEY `uk_file_mgmt_parse_item_review_idem` (`task_id`, `result_item_id`, `idempotency_key`)'
    )
);
PREPARE stmt FROM @add_file_parse_review_idem_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_review_current_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_item_review'
              AND INDEX_NAME = 'uk_file_mgmt_parse_item_review_current'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_item_review` ADD UNIQUE KEY `uk_file_mgmt_parse_item_review_current` (`result_item_id`, `current_marker`)'
    )
);
PREPARE stmt FROM @add_file_parse_review_current_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_current_result` (
    `task_id` BIGINT NOT NULL,
    `result_id` BIGINT NOT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`task_id`),
    KEY `idx_file_mgmt_parse_current_result_result` (`result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_version` (
    `id` BIGINT NOT NULL,
    `version_no` VARCHAR(80) NOT NULL,
    `target_plan_id` BIGINT NOT NULL,
    `source_task_id` BIGINT DEFAULT NULL,
    `source_result_id` BIGINT DEFAULT NULL,
    `standard_version_id` BIGINT NOT NULL,
    `base_version_id` BIGINT DEFAULT NULL,
    `data_scope_type` VARCHAR(40) NOT NULL DEFAULT 'global',
    `data_scope_key` VARCHAR(160) NOT NULL DEFAULT 'global:*',
    `version_status` VARCHAR(30) NOT NULL DEFAULT 'history',
    `published_at` DATETIME DEFAULT NULL,
    `published_by` BIGINT DEFAULT NULL,
    `summary_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_version_no` (`version_no`),
    KEY `idx_file_mgmt_parse_version_plan_scope` (`target_plan_id`, `data_scope_type`, `data_scope_key`),
    KEY `idx_file_mgmt_parse_version_status` (`version_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_version_item` (
    `id` BIGINT NOT NULL,
    `version_id` BIGINT NOT NULL,
    `target_plan_id` BIGINT NOT NULL,
    `item_type` VARCHAR(80) NOT NULL,
    `natural_key` VARCHAR(500) NOT NULL,
    `natural_key_hash` VARCHAR(128) NOT NULL,
    `version_payload_json` LONGTEXT NOT NULL,
    `source_result_item_id` BIGINT DEFAULT NULL,
    `data_scope_type` VARCHAR(40) NOT NULL DEFAULT 'global',
    `data_scope_key` VARCHAR(160) NOT NULL DEFAULT 'global:*',
    `sort_no` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_version_item_key` (`version_id`, `item_type`, `natural_key_hash`),
    KEY `idx_file_mgmt_parse_version_item_plan` (`target_plan_id`, `item_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_active_version` (
    `id` BIGINT NOT NULL,
    `target_plan_id` BIGINT NOT NULL,
    `data_scope_type` VARCHAR(40) NOT NULL DEFAULT 'global',
    `data_scope_key` VARCHAR(160) NOT NULL DEFAULT 'global:*',
    `version_id` BIGINT NOT NULL,
    `version_no` VARCHAR(80) NOT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_active_version_scope` (`target_plan_id`, `data_scope_type`, `data_scope_key`),
    KEY `idx_file_mgmt_parse_active_version_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_logistics_channel_activation` (
    `id` BIGINT NOT NULL,
    `target_plan_id` BIGINT NOT NULL,
    `version_id` BIGINT NOT NULL,
    `version_item_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `channel_key` VARCHAR(200) NOT NULL,
    `natural_key` VARCHAR(500) NOT NULL,
    `natural_key_hash` VARCHAR(128) NOT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_mgmt_parse_logistics_activation_key` (`owner_user_id`, `target_plan_id`, `version_id`, `natural_key_hash`),
    KEY `idx_file_mgmt_parse_logistics_activation_owner` (`owner_user_id`, `target_plan_id`, `is_deleted`),
    KEY `idx_file_mgmt_parse_logistics_activation_version` (`version_id`, `version_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `file_mgmt_parse_audit_log` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT DEFAULT NULL,
    `target_plan_id` BIGINT DEFAULT NULL,
    `version_id` BIGINT DEFAULT NULL,
    `operation_type` VARCHAR(60) NOT NULL,
    `operation_summary` VARCHAR(1000) DEFAULT NULL,
    `request_id` VARCHAR(100) DEFAULT NULL,
    `payload_hash` VARCHAR(128) DEFAULT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_file_mgmt_parse_audit_task` (`task_id`),
    KEY `idx_file_mgmt_parse_audit_operation` (`operation_type`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `file_mgmt_parse_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('file_mgmt_parse_standard', 1000, NOW(), NOW()),
    ('file_mgmt_parse_standard_version', 2000, NOW(), NOW()),
    ('file_mgmt_parse_item_standard', 3000, NOW(), NOW()),
    ('file_mgmt_parse_target_plan', 4000, NOW(), NOW()),
    ('file_mgmt_parse_target_plan_scope', 5000, NOW(), NOW()),
    ('file_mgmt_parse_file_asset', 10000, NOW(), NOW()),
    ('file_mgmt_parse_task', 20000, NOW(), NOW()),
    ('file_mgmt_parse_task_input', 30000, NOW(), NOW()),
    ('file_mgmt_parse_result', 40000, NOW(), NOW()),
    ('file_mgmt_parse_result_item', 50000, NOW(), NOW()),
    ('file_mgmt_parse_item_review', 60000, NOW(), NOW()),
    ('file_mgmt_parse_version', 70005, NOW(), NOW()),
    ('file_mgmt_parse_active_version', 72000, NOW(), NOW()),
    ('file_mgmt_parse_version_item', 88020, NOW(), NOW()),
    ('file_mgmt_parse_audit_log', 90000, NOW(), NOW()),
    ('file_mgmt_parse_logistics_channel_activation', 95000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_standard` (
    `id`, `document_type`, `document_name`, `description`, `active_version_id`,
    `status`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (1001, 'official_commission', '官方佣金方案', 'Noon KSA/UAE FBN 官方佣金落库标准', 2001, 'active', b'0', 1, 1),
    (1002, 'official_outbound_fee', '官方出仓费方案', 'Noon KSA/UAE 官方出仓费落库标准', 2002, 'active', b'0', 1, 1),
    (1003, 'logistics_rule', '物流规则方案', '义特物流阿联酋 FBN 迪拜渠道规则落库标准', 2003, 'active', b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `document_name` = VALUES(`document_name`),
    `description` = VALUES(`description`),
    `active_version_id` = VALUES(`active_version_id`),
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_standard_version` (
    `id`, `standard_id`, `standard_version`, `input_schema_json`, `result_schema_json`,
    `normalization_rule_json`, `validation_rule_json`, `display_config_json`, `diff_rule_json`,
    `review_action_json`, `status`, `published_at`, `published_by`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (2001, 1001, 'STD-2026.05',
     '{"inputTypes":["excel","pdf","image","ocr_text","manual_text"]}',
     '{"items":["commission_rule"]}',
     '{"currency":["AED","SAR"],"rate":"percent","tierRule":"split_by_amount_range","brandRestriction":"default_all_split_by_brand"}',
     '{"required":["country","categoryName","amountRangeLabel","amountCurrency","commissionRate"]}',
     '{"overviewColumns":["parentCategoryName","categoryName","brandRestriction","amountRangeLabel","amountCurrency","commissionRate","effectiveDate"]}',
     '{"naturalKey":["country","categoryPath","parentCategoryName","categoryName","brandRestriction","amountMin","amountMinInclusive","amountMax","amountMaxInclusive","amountCurrency","effectiveDate"]}',
     '{"actions":["accept","edit","reject","keep_old"]}',
     'active', NOW(), 1, b'0', 1, 1),
    (2002, 1002, 'STD-2026.05',
     '{"inputTypes":["excel","pdf","image","ocr_text","manual_text"]}',
     '{"items":["outbound_fee_rule"]}',
     '{"currency":["AED","SAR"],"fee":"decimal"}',
     '{"required":["country","feeItem","feeAmount","currency"]}',
     '{"overviewColumns":["country","feeItem","feeAmount","currency","minFee","effectiveDate"]}',
     '{"naturalKey":["country","feeItem","sizeTier"]}',
     '{"actions":["accept","edit","reject","keep_old"]}',
     'active', NOW(), 1, b'0', 1, 1),
    (2003, 1003, 'STD-2026.05',
     '{"inputTypes":["excel","pdf","image","ocr_text","manual_text"]}',
     '{"items":["logistics_channel_rule"]}',
     '{"country":"UAE","city":"Dubai","unit":["CNY/KG","AED/item"]}',
     '{"required":["channelKey","shippingMethod","feeItem","billingRule"]}',
     '{"overviewColumns":["channelKey","country","city","shippingMethod","feeItem","billingRule","leadTime"]}',
     '{"naturalKey":["channelKey","country","city","shippingMethod","feeItem"]}',
     '{"actions":["accept","edit","reject","keep_old"]}',
     'active', NOW(), 1, b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `input_schema_json` = VALUES(`input_schema_json`),
    `result_schema_json` = VALUES(`result_schema_json`),
    `normalization_rule_json` = VALUES(`normalization_rule_json`),
    `validation_rule_json` = VALUES(`validation_rule_json`),
    `display_config_json` = VALUES(`display_config_json`),
    `diff_rule_json` = VALUES(`diff_rule_json`),
    `review_action_json` = VALUES(`review_action_json`),
    `status` = 'active',
    `published_at` = VALUES(`published_at`),
    `published_by` = VALUES(`published_by`),
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_item_standard` (
    `id`, `standard_version_id`, `item_type`, `item_label`, `natural_key_json`, `field_schema_json`,
    `display_config_json`, `validation_rule_json`, `diff_rule_json`, `sort_no`, `status`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (3001, 2001, 'commission_rule', '佣金规则',
     '{"fields":["country","categoryPath","parentCategoryName","categoryName","brandRestriction","amountMin","amountMinInclusive","amountMax","amountMaxInclusive","amountCurrency","effectiveDate"]}',
     '{"country":"string","platform":"string","fulfillmentType":"string","parentCategoryName":"string","categoryName":"string","categoryPath":"string","brandRestriction":"string","amountRangeLabel":"string","amountMin":"decimal","amountMinInclusive":"boolean","amountMax":"decimal","amountMaxInclusive":"boolean","amountCurrency":"string","commissionRate":"decimal","effectiveDate":"date"}',
     '{"columns":["parentCategoryName","categoryName","brandRestriction","amountRangeLabel","amountCurrency","commissionRate","effectiveDate"],"labels":{"country":"国家","platform":"平台","fulfillmentType":"履约方式","parentCategoryName":"一级类目","categoryName":"类目","categoryPath":"类目路径","brandRestriction":"品牌限制","amountRangeLabel":"计佣金额区间","amountCurrency":"币种","commissionRate":"佣金率","effectiveDate":"生效日期","amountMin":"金额下限","amountMinInclusive":"下限含边界","amountMax":"金额上限","amountMaxInclusive":"上限含边界"},"widths":{"parentCategoryName":150,"categoryName":220,"brandRestriction":150,"amountRangeLabel":150,"amountCurrency":90,"commissionRate":100,"effectiveDate":130}}',
     '{"required":["country","categoryName","amountRangeLabel","amountCurrency","commissionRate"]}',
     '{"compareFields":["amountRangeLabel","amountMin","amountMinInclusive","amountMax","amountMaxInclusive","amountCurrency","commissionRate","effectiveDate"]}',
     10, 'active', b'0', 1, 1),
    (3002, 2002, 'outbound_fee_rule', '出仓费规则',
     '{"fields":["country","feeItem","sizeTier"]}',
     '{"country":"string","feeItem":"string","sizeTier":"string","feeAmount":"decimal","currency":"string","minFee":"decimal","effectiveDate":"date"}',
     '{"columns":["country","feeItem","sizeTier","feeAmount","currency","minFee","effectiveDate"]}',
     '{"required":["country","feeItem","feeAmount","currency"]}',
     '{"compareFields":["feeAmount","currency","minFee","effectiveDate"]}',
     20, 'active', b'0', 1, 1),
    (3003, 2003, 'logistics_channel_rule', '物流渠道规则',
     '{"fields":["channelKey","country","city","shippingMethod","feeItem"]}',
     '{"channelKey":"string","country":"string","city":"string","shippingMethod":"string","feeItem":"string","billingRule":"string","leadTime":"string"}',
     '{"columns":["channelKey","country","city","shippingMethod","feeItem","billingRule","leadTime"]}',
     '{"required":["channelKey","shippingMethod","feeItem","billingRule"]}',
     '{"compareFields":["billingRule","leadTime"]}',
     30, 'active', b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `natural_key_json` = VALUES(`natural_key_json`),
    `field_schema_json` = VALUES(`field_schema_json`),
    `display_config_json` = VALUES(`display_config_json`),
    `validation_rule_json` = VALUES(`validation_rule_json`),
    `diff_rule_json` = VALUES(`diff_rule_json`),
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_target_plan` (
    `id`, `plan_code`, `plan_label`, `standard_id`, `standard_version_id`,
    `document_type`, `document_name`, `business_scope_code`, `business_scope_label`,
    `publish_adapter`, `description`, `sort_no`, `status`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (4001, 'commission_ksa', '佣金-KSA', 1001, 2001, 'official_commission', '官方佣金方案',
     'noon_ksa_fbn_commission', 'Noon KSA FBN 官方佣金', 'official_commission', 'Noon / KSA / FBN 官方佣金输出', 10, 'active', b'0', 1, 1),
    (4002, 'commission_uae', '佣金-UAE', 1001, 2001, 'official_commission', '官方佣金方案',
     'noon_uae_fbn_commission', 'Noon UAE FBN 官方佣金', 'official_commission', 'Noon / UAE / FBN 官方佣金输出', 20, 'active', b'0', 1, 1),
    (4003, 'outbound_fee_ksa', '出仓费-KSA', 1002, 2002, 'official_outbound_fee', '官方出仓费方案',
     'noon_ksa_outbound_fee', 'Noon KSA 官方出仓费', 'official_outbound_fee', 'Noon / KSA 官方出仓费输出', 30, 'active', b'0', 1, 1),
    (4004, 'outbound_fee_uae', '出仓费-UAE', 1002, 2002, 'official_outbound_fee', '官方出仓费方案',
     'noon_uae_outbound_fee', 'Noon UAE 官方出仓费', 'official_outbound_fee', 'Noon / UAE 官方出仓费输出', 40, 'active', b'0', 1, 1),
    (4005, 'logistics_yite', '物流-义特', 1003, 2003, 'logistics_rule', '物流规则方案',
     'yite_ae_fbn_dubai', '义特物流，阿联酋 FBN，迪拜', 'logistics_rule', '义特物流阿联酋 FBN 迪拜渠道规则输出', 50, 'active', b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `plan_label` = VALUES(`plan_label`),
    `standard_id` = VALUES(`standard_id`),
    `standard_version_id` = VALUES(`standard_version_id`),
    `document_type` = VALUES(`document_type`),
    `document_name` = VALUES(`document_name`),
    `business_scope_code` = VALUES(`business_scope_code`),
    `business_scope_label` = VALUES(`business_scope_label`),
    `publish_adapter` = VALUES(`publish_adapter`),
    `description` = VALUES(`description`),
    `sort_no` = VALUES(`sort_no`),
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_target_plan_scope` (
    `id`, `target_plan_id`, `scope_type`, `scope_value`, `scope_label`,
    `status`, `is_deleted`, `created_by`, `updated_by`
)
SELECT
    5000 + plan_offset.`offset_no` * 10 + role_scope.`role_level`,
    plan_offset.`target_plan_id`,
    'role_level',
    CAST(role_scope.`role_level` AS CHAR),
    role_scope.`role_label`,
    'active',
    b'0',
    1,
    1
FROM (
    SELECT 1 AS `offset_no`, 4001 AS `target_plan_id`
    UNION ALL SELECT 2, 4002
    UNION ALL SELECT 3, 4003
    UNION ALL SELECT 4, 4004
    UNION ALL SELECT 5, 4005
) plan_offset
JOIN (
    SELECT 0 AS `role_level`, '系统管理员' AS `role_label`
    UNION ALL SELECT 1, '老板'
    UNION ALL SELECT 2, '运营主管'
    UNION ALL SELECT 3, '运营'
) role_scope
ON 1 = 1
ON DUPLICATE KEY UPDATE
    `scope_label` = VALUES(`scope_label`),
    `status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_version` (
    `id`, `version_no`, `target_plan_id`, `source_task_id`, `source_result_id`, `standard_version_id`,
    `base_version_id`, `data_scope_type`, `data_scope_key`, `version_status`, `published_at`, `published_by`,
    `summary_json`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (70001, 'COMM-KSA-2026-04', 4001, NULL, NULL, 2001, NULL, 'global', 'global:*', 'active', NOW(), 1, '{"seed":"initial_active_version"}', b'0', 1, 1),
    (70002, 'COMM-UAE-2026-04', 4002, NULL, NULL, 2001, NULL, 'global', 'global:*', 'active', NOW(), 1, '{"seed":"initial_active_version"}', b'0', 1, 1),
    (70003, 'OUT-KSA-2026-04', 4003, NULL, NULL, 2002, NULL, 'global', 'global:*', 'active', NOW(), 1, '{"seed":"initial_active_version"}', b'0', 1, 1),
    (70004, 'OUT-UAE-2026-04', 4004, NULL, NULL, 2002, NULL, 'global', 'global:*', 'active', NOW(), 1, '{"seed":"initial_active_version"}', b'0', 1, 1),
    (70005, 'YITE-AE-FBN-2026-04', 4005, NULL, NULL, 2003, NULL, 'global', 'global:*', 'active', NOW(), 1, '{"seed":"initial_active_version"}', b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `standard_version_id` = VALUES(`standard_version_id`),
    `data_scope_type` = 'global',
    `data_scope_key` = 'global:*',
    `version_status` = 'active',
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_active_version` (
    `id`, `target_plan_id`, `data_scope_type`, `data_scope_key`, `version_id`, `version_no`,
    `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (71001, 4001, 'global', 'global:*', 70001, 'COMM-KSA-2026-04', b'0', 1, 1),
    (71002, 4002, 'global', 'global:*', 70002, 'COMM-UAE-2026-04', b'0', 1, 1),
    (71003, 4003, 'global', 'global:*', 70003, 'OUT-KSA-2026-04', b'0', 1, 1),
    (71004, 4004, 'global', 'global:*', 70004, 'OUT-UAE-2026-04', b'0', 1, 1),
    (71005, 4005, 'global', 'global:*', 70005, 'YITE-AE-FBN-2026-04', b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `version_id` = VALUES(`version_id`),
    `version_no` = VALUES(`version_no`),
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

INSERT INTO `file_mgmt_parse_version_item` (
    `id`, `version_id`, `target_plan_id`, `item_type`, `natural_key`, `natural_key_hash`,
    `version_payload_json`, `source_result_item_id`, `data_scope_type`, `data_scope_key`,
    `sort_no`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (88011, 70001, 4001, 'commission_rule',
     'KSA + Fashion > Watches + 全部 + <= 5000 SAR + 2025-09-01',
     'seed-comm-ksa-fashion-watches-le-5000',
     '{"country":"KSA","platform":"Noon","fulfillmentType":"FBN","parentCategoryName":null,"categoryName":"Fashion > Watches","categoryPath":"Fashion > Watches","brandRestriction":"全部","amountRangeLabel":"<= 5000 SAR","amountMin":null,"amountMinInclusive":null,"amountMax":5000,"amountMaxInclusive":true,"amountCurrency":"SAR","commissionRate":"15%","effectiveDate":"2025-09-01"}',
     NULL, 'global', 'global:*', 10, b'0', 1, 1),
    (88012, 70001, 4001, 'commission_rule',
     'KSA + Fashion > Watches + 全部 + > 5000 SAR + 2025-09-01',
     'seed-comm-ksa-fashion-watches-gt-5000',
     '{"country":"KSA","platform":"Noon","fulfillmentType":"FBN","parentCategoryName":null,"categoryName":"Fashion > Watches","categoryPath":"Fashion > Watches","brandRestriction":"全部","amountRangeLabel":"> 5000 SAR","amountMin":5000,"amountMinInclusive":false,"amountMax":null,"amountMaxInclusive":null,"amountCurrency":"SAR","commissionRate":"5%","effectiveDate":"2025-09-01"}',
     NULL, 'global', 'global:*', 20, b'0', 1, 1),
    (80001, 70005, 4005, 'logistics_channel_rule',
     '义特 + UAE + Dubai + 海运 + FBN普货',
     'seed-yite-ae-fbn-sea',
     '{"channelKey":"yite_ae_fbn_sea","country":"UAE","city":"Dubai","shippingMethod":"海运","feeItem":"FBN普货","billingRule":"26 CNY/KG，最低 12KG","leadTime":"18-25 天"}',
     NULL, 'global', 'global:*', 30, b'0', 1, 1),
    (80002, 70005, 4005, 'logistics_channel_rule',
     '义特 + UAE + Dubai + 空运 + FBN普货',
     'seed-yite-ae-fbn-air',
     '{"channelKey":"yite_ae_fbn_air","country":"UAE","city":"Dubai","shippingMethod":"空运","feeItem":"FBN普货","billingRule":"42 CNY/KG，时效 5-7 天","leadTime":"5-7 天"}',
     NULL, 'global', 'global:*', 40, b'0', 1, 1)
ON DUPLICATE KEY UPDATE
    `version_payload_json` = VALUES(`version_payload_json`),
    `sort_no` = VALUES(`sort_no`),
    `is_deleted` = b'0',
    `gmt_updated` = NOW();
