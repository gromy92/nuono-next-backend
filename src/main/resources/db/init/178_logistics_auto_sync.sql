SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `logistics_forwarder_account` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `operator_user_id` BIGINT NOT NULL,
  `source_system` VARCHAR(30) NOT NULL,
  `forwarder_name` VARCHAR(80) NOT NULL,
  `login_account` VARCHAR(160) NOT NULL,
  `login_account_hash` VARCHAR(64) NOT NULL,
  `password_cipher` TEXT DEFAULT NULL,
  `enabled` BIT(1) NOT NULL DEFAULT b'0',
  `schedule_enabled` BIT(1) NOT NULL DEFAULT b'0',
  `commit_enabled` BIT(1) NOT NULL DEFAULT b'0',
  `schedule_window_start` TIME DEFAULT NULL,
  `schedule_window_end` TIME DEFAULT NULL,
  `min_interval_hours` INT NOT NULL DEFAULT 24,
  `verification_status` VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
  `last_login_status` VARCHAR(30) DEFAULT NULL,
  `last_preview_status` VARCHAR(30) DEFAULT NULL,
  `last_sync_status` VARCHAR(30) DEFAULT NULL,
  `last_task_id` BIGINT DEFAULT NULL,
  `last_synced_at` DATETIME DEFAULT NULL,
  `next_eligible_at` DATETIME DEFAULT NULL,
  `cooldown_until` DATETIME DEFAULT NULL,
  `last_failure_code` VARCHAR(80) DEFAULT NULL,
  `last_failure_message` VARCHAR(500) DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_logistics_forwarder_account_active` (`owner_user_id`, `source_system`, `login_account_hash`, `is_deleted`),
  KEY `idx_logistics_forwarder_account_due` (`enabled`, `schedule_enabled`, `next_eligible_at`, `cooldown_until`, `gmt_updated`),
  KEY `idx_logistics_forwarder_account_owner` (`owner_user_id`, `source_system`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT
  'logistics_forwarder_account',
  GREATEST(COALESCE(MAX(`id`) + 1, 180000), 180000),
  NOW(),
  NOW()
FROM `logistics_forwarder_account`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();
