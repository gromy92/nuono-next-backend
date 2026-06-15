-- Renamed from 063_product_lifecycle_job_recalculation_audit.sql to keep migration prefixes unique.
-- Record controlled lifecycle recalculation trigger metadata.

SET NAMES utf8mb4;

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

SET @add_product_lifecycle_job_trigger_idx := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_lifecycle_job'
        AND INDEX_NAME = 'idx_product_lifecycle_job_trigger'
    ),
    'SELECT 1',
    'ALTER TABLE `product_lifecycle_job` ADD KEY `idx_product_lifecycle_job_trigger` (`triggered_by_user_id`, `trigger_source`, `started_at`)'
  )
);
PREPARE stmt FROM @add_product_lifecycle_job_trigger_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
