-- Add source-collection backoff scheduling for Noon risk-control cooldowns.

SET NAMES utf8mb4;

SET @add_product_selection_source_collection_next_run_at := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND COLUMN_NAME = 'next_run_at'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `next_run_at` DATETIME DEFAULT NULL AFTER `attempt_count`'
  )
);
PREPARE stmt FROM @add_product_selection_source_collection_next_run_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_backoff_index := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND INDEX_NAME = 'idx_product_selection_source_backoff_claim'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD KEY `idx_product_selection_source_backoff_claim` (`status`, `source_type`, `next_run_at`, `locked_at`, `collected_at`)'
  )
);
PREPARE stmt FROM @add_product_selection_source_collection_backoff_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
