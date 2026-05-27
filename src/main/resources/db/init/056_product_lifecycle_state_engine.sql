-- Product lifecycle state engine DEFAULT_V1 persistent state.
-- Current state is scoped by owner + store + site + partner SKU + SKU.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sales_data_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sales_data_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('product_lifecycle_current_state', 70000, NOW(), NOW()),
    ('product_lifecycle_history', 71000, NOW(), NOW()),
    ('product_lifecycle_job', 72000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `product_lifecycle_current_state` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) NOT NULL,
    `lifecycle_code` VARCHAR(40) NOT NULL,
    `lifecycle_label` VARCHAR(80) NOT NULL,
    `rule_version` VARCHAR(80) NOT NULL,
    `analysis_date` DATE NOT NULL,
    `listing_date` DATE DEFAULT NULL,
    `listing_date_source` VARCHAR(40) DEFAULT NULL,
    `quality_state` VARCHAR(40) NOT NULL,
    `explanation` VARCHAR(1000) DEFAULT NULL,
    `evidence_json` LONGTEXT DEFAULT NULL,
    `last_job_id` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_lifecycle_current_scope` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `sku`),
    KEY `idx_product_lifecycle_current_scope_state` (`owner_user_id`, `store_code`, `site_code`, `lifecycle_code`, `quality_state`),
    KEY `idx_product_lifecycle_current_analysis` (`analysis_date`, `rule_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_lifecycle_history` (
    `id` BIGINT NOT NULL,
    `current_state_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) NOT NULL,
    `previous_lifecycle_code` VARCHAR(40) DEFAULT NULL,
    `previous_lifecycle_label` VARCHAR(80) DEFAULT NULL,
    `lifecycle_code` VARCHAR(40) NOT NULL,
    `lifecycle_label` VARCHAR(80) NOT NULL,
    `rule_version` VARCHAR(80) NOT NULL,
    `analysis_date` DATE NOT NULL,
    `transition_reason` VARCHAR(160) DEFAULT NULL,
    `evidence_json` LONGTEXT DEFAULT NULL,
    `changed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_lifecycle_history_scope` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `sku`, `changed_at`),
    KEY `idx_product_lifecycle_history_state` (`owner_user_id`, `store_code`, `site_code`, `lifecycle_code`, `analysis_date`),
    KEY `idx_product_lifecycle_history_current` (`current_state_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_lifecycle_job` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `anchor_date` DATE NOT NULL,
    `rule_version` VARCHAR(80) NOT NULL,
    `status` VARCHAR(40) NOT NULL,
    `processed_count` INT NOT NULL DEFAULT 0,
    `changed_count` INT NOT NULL DEFAULT 0,
    `held_count` INT NOT NULL DEFAULT 0,
    `data_insufficient_count` INT NOT NULL DEFAULT 0,
    `failure_summary_json` LONGTEXT DEFAULT NULL,
    `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finished_at` DATETIME DEFAULT NULL,
    `triggered_by_user_id` BIGINT DEFAULT NULL,
    `trigger_source` VARCHAR(80) DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_lifecycle_job_scope` (`owner_user_id`, `store_code`, `site_code`, `anchor_date`, `rule_version`),
    KEY `idx_product_lifecycle_job_status` (`status`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_product_lifecycle_job_triggered_by := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_lifecycle_job'
        AND COLUMN_NAME = 'triggered_by_user_id'
    ),
    'SELECT 1',
    'ALTER TABLE `product_lifecycle_job` ADD COLUMN `triggered_by_user_id` BIGINT DEFAULT NULL AFTER `finished_at`'
  )
);
PREPARE stmt FROM @add_product_lifecycle_job_triggered_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_lifecycle_job_trigger_source := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_lifecycle_job'
        AND COLUMN_NAME = 'trigger_source'
    ),
    'SELECT 1',
    'ALTER TABLE `product_lifecycle_job` ADD COLUMN `trigger_source` VARCHAR(80) DEFAULT NULL AFTER `triggered_by_user_id`'
  )
);
PREPARE stmt FROM @add_product_lifecycle_job_trigger_source;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
