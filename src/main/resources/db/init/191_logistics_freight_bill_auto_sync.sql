-- Independent third-party actual-freight-bill pull state.
-- Every switch defaults off; applying this migration cannot enable provider calls or financial writes.

SET NAMES utf8mb4;

ALTER TABLE `logistics_forwarder_account`
  ADD COLUMN `freight_bill_schedule_enabled` BIT(1) NOT NULL DEFAULT b'0' AFTER `commit_enabled`,
  ADD COLUMN `freight_bill_commit_enabled` BIT(1) NOT NULL DEFAULT b'0' AFTER `freight_bill_schedule_enabled`,
  ADD COLUMN `freight_bill_last_preview_status` VARCHAR(30) DEFAULT NULL AFTER `last_failure_message`,
  ADD COLUMN `freight_bill_last_sync_status` VARCHAR(30) DEFAULT NULL AFTER `freight_bill_last_preview_status`,
  ADD COLUMN `freight_bill_last_task_id` BIGINT DEFAULT NULL AFTER `freight_bill_last_sync_status`,
  ADD COLUMN `freight_bill_last_synced_at` DATETIME DEFAULT NULL AFTER `freight_bill_last_task_id`,
  ADD COLUMN `freight_bill_next_eligible_at` DATETIME DEFAULT NULL AFTER `freight_bill_last_synced_at`,
  ADD COLUMN `freight_bill_cooldown_until` DATETIME DEFAULT NULL AFTER `freight_bill_next_eligible_at`,
  ADD COLUMN `freight_bill_last_failure_code` VARCHAR(80) DEFAULT NULL AFTER `freight_bill_cooldown_until`,
  ADD COLUMN `freight_bill_last_failure_message` VARCHAR(500) DEFAULT NULL AFTER `freight_bill_last_failure_code`,
  ADD KEY `idx_logistics_forwarder_account_freight_bill_due` (
    `enabled`, `freight_bill_schedule_enabled`, `freight_bill_next_eligible_at`,
    `freight_bill_cooldown_until`, `gmt_updated`
  );
