-- Harden manual source collection task allocation and scheduler claiming.

SET NAMES utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT
  'product_selection_source_collection',
  GREATEST(COALESCE(MAX(`id`), 86000), 86000),
  NOW(),
  NOW()
FROM `product_selection_source_collection`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

SET @add_product_selection_source_collection_locked_at := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND COLUMN_NAME = 'locked_at'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `locked_at` DATETIME DEFAULT NULL AFTER `failure_message`'
  )
);
PREPARE stmt FROM @add_product_selection_source_collection_locked_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_locked_by := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND COLUMN_NAME = 'locked_by'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `locked_by` VARCHAR(120) DEFAULT NULL AFTER `locked_at`'
  )
);
PREPARE stmt FROM @add_product_selection_source_collection_locked_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_attempt_count := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND COLUMN_NAME = 'attempt_count'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 0 AFTER `locked_by`'
  )
);
PREPARE stmt FROM @add_product_selection_source_collection_attempt_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_lock_index := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND INDEX_NAME = 'idx_product_selection_source_claim'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD KEY `idx_product_selection_source_claim` (`status`, `source_type`, `locked_at`, `collected_at`)'
  )
);
PREPARE stmt FROM @add_product_selection_source_collection_lock_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
