-- Nuono Next procurement candidate pool V1
-- Purpose:
-- 1. Add the procurement candidate-pool aggregate for modules 2-4.
-- 2. Keep existing procurement_candidate and procurement_auto_inquiry_task compatible.
-- 3. Support automatic max-5 pool inquiry, candidate replacement, final 2 picks, snapshots, and operation logs.

CREATE TABLE IF NOT EXISTS `procurement_candidate_pool` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `demand_item_id` BIGINT NOT NULL,
    `pool_no` VARCHAR(40) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'POOL_CREATED',
    `max_pool_size` INT NOT NULL DEFAULT 5,
    `candidate_source_limit` INT NOT NULL DEFAULT 10,
    `assigned_buyer_id` BIGINT DEFAULT NULL,
    `current_snapshot_id` BIGINT DEFAULT NULL,
    `auto_created_at` DATETIME DEFAULT NULL,
    `inquiry_started_at` DATETIME DEFAULT NULL,
    `inquiry_finished_at` DATETIME DEFAULT NULL,
    `final_confirmed_at` DATETIME DEFAULT NULL,
    `summary_ready_at` DATETIME DEFAULT NULL,
    `summary_text` LONGTEXT DEFAULT NULL,
    `summary_input_snapshot_id` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_candidate_pool_no` (`pool_no`),
    KEY `idx_procurement_candidate_pool_owner` (`owner_user_id`),
    KEY `idx_procurement_candidate_pool_demand` (`demand_item_id`),
    KEY `idx_procurement_candidate_pool_status` (`status`),
    KEY `idx_procurement_candidate_pool_buyer` (`assigned_buyer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_candidate_pool_item` (
    `id` BIGINT NOT NULL,
    `pool_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `demand_item_id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `source_rank_no` INT DEFAULT NULL,
    `pool_rank_no` INT DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'IN_POOL_WAITING_SEND',
    `join_source` VARCHAR(30) NOT NULL DEFAULT 'SYSTEM_AUTO',
    `inquiry_task_id` BIGINT DEFAULT NULL,
    `joined_at` DATETIME DEFAULT NULL,
    `first_sent_at` DATETIME DEFAULT NULL,
    `no_reply_deadline_at` DATETIME DEFAULT NULL,
    `last_follow_up_at` DATETIME DEFAULT NULL,
    `last_reply_at` DATETIME DEFAULT NULL,
    `closed_at` DATETIME DEFAULT NULL,
    `removed_at` DATETIME DEFAULT NULL,
    `removed_by` BIGINT DEFAULT NULL,
    `remove_reason` VARCHAR(500) DEFAULT NULL,
    `quote_price_text` VARCHAR(100) DEFAULT NULL,
    `quote_moq_text` VARCHAR(100) DEFAULT NULL,
    `quote_delivery_text` VARCHAR(100) DEFAULT NULL,
    `reply_summary` VARCHAR(1000) DEFAULT NULL,
    `risk_note` VARCHAR(1000) DEFAULT NULL,
    `is_current` BIT(1) NOT NULL DEFAULT b'1',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_pool_item_pool` (`pool_id`),
    KEY `idx_procurement_pool_item_demand` (`demand_item_id`),
    KEY `idx_procurement_pool_item_candidate` (`candidate_id`),
    KEY `idx_procurement_pool_item_status` (`status`),
    KEY `idx_procurement_pool_item_current` (`pool_id`, `is_current`, `is_deleted`, `status`),
    KEY `idx_procurement_pool_item_task` (`inquiry_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_candidate_pool_snapshot` (
    `id` BIGINT NOT NULL,
    `pool_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `demand_item_id` BIGINT NOT NULL,
    `snapshot_type` VARCHAR(40) NOT NULL,
    `snapshot_version` INT NOT NULL DEFAULT 1,
    `pool_status` VARCHAR(40) NOT NULL,
    `item_count` INT NOT NULL DEFAULT 0,
    `snapshot_json` LONGTEXT NOT NULL,
    `remark` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_pool_snapshot_pool` (`pool_id`),
    KEY `idx_procurement_pool_snapshot_demand` (`demand_item_id`),
    KEY `idx_procurement_pool_snapshot_type` (`snapshot_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_final_candidate` (
    `id` BIGINT NOT NULL,
    `pool_id` BIGINT NOT NULL,
    `pool_item_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `demand_item_id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `final_pick_type` VARCHAR(20) NOT NULL,
    `snapshot_id` BIGINT DEFAULT NULL,
    `decision_note` VARCHAR(1000) DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `confirmed_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_final_candidate_pool` (`pool_id`),
    KEY `idx_procurement_final_candidate_demand` (`demand_item_id`),
    KEY `idx_procurement_final_candidate_candidate` (`candidate_id`),
    KEY `idx_procurement_final_candidate_pick` (`pool_id`, `final_pick_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_pool_operation_log` (
    `id` BIGINT NOT NULL,
    `pool_id` BIGINT NOT NULL,
    `pool_item_id` BIGINT DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `demand_item_id` BIGINT NOT NULL,
    `candidate_id` BIGINT DEFAULT NULL,
    `offer_id` VARCHAR(100) DEFAULT NULL,
    `operation_type` VARCHAR(60) NOT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `operator_role` VARCHAR(40) DEFAULT NULL,
    `before_status` VARCHAR(40) DEFAULT NULL,
    `after_status` VARCHAR(40) DEFAULT NULL,
    `snapshot_id` BIGINT DEFAULT NULL,
    `operation_reason` VARCHAR(500) DEFAULT NULL,
    `detail_json` LONGTEXT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_pool_log_pool` (`pool_id`),
    KEY `idx_procurement_pool_log_item` (`pool_item_id`),
    KEY `idx_procurement_pool_log_demand` (`demand_item_id`),
    KEY `idx_procurement_pool_log_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @procurement_auto_inquiry_task_add_pool_id := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'pool_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `pool_id` BIGINT DEFAULT NULL AFTER `candidate_id`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_pool_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_pool_item_id := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'pool_item_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `pool_item_id` BIGINT DEFAULT NULL AFTER `pool_id`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_pool_item_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_no_reply_deadline_at := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'no_reply_deadline_at'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `no_reply_deadline_at` DATETIME DEFAULT NULL AFTER `sent_at`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_no_reply_deadline_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_pool_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND INDEX_NAME = 'idx_procurement_auto_inquiry_task_pool'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD KEY `idx_procurement_auto_inquiry_task_pool` (`pool_id`)'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_pool_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_pool_item_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND INDEX_NAME = 'idx_procurement_auto_inquiry_task_pool_item'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD KEY `idx_procurement_auto_inquiry_task_pool_item` (`pool_item_id`)'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_pool_item_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_assigned_buyer_id := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'assigned_buyer_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `assigned_buyer_id` BIGINT DEFAULT NULL AFTER `status`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_assigned_buyer_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_current_pool_id := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'current_pool_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `current_pool_id` BIGINT DEFAULT NULL AFTER `assigned_buyer_id`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_current_pool_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_assigned_buyer_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND INDEX_NAME = 'idx_procurement_demand_item_buyer'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD KEY `idx_procurement_demand_item_buyer` (`assigned_buyer_id`)'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_assigned_buyer_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_current_pool_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND INDEX_NAME = 'idx_procurement_demand_item_current_pool'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD KEY `idx_procurement_demand_item_current_pool` (`current_pool_id`)'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_current_pool_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
