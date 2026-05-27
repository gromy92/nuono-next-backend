-- Record manual source collection start and finish timestamps for duration display.

SET NAMES utf8mb4;

SET @add_product_selection_source_collection_started_at := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND COLUMN_NAME = 'collection_started_at'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `collection_started_at` DATETIME DEFAULT NULL AFTER `collected_at`'
  )
);
PREPARE stmt FROM @add_product_selection_source_collection_started_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_selection_source_collection_finished_at := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND COLUMN_NAME = 'collection_finished_at'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `collection_finished_at` DATETIME DEFAULT NULL AFTER `collection_started_at`'
  )
);
PREPARE stmt FROM @add_product_selection_source_collection_finished_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_selection_source_collection`
SET `collection_started_at` = COALESCE(`collection_started_at`, `gmt_create`, `collected_at`),
    `collection_finished_at` = CASE
      WHEN `status` IN ('success', 'failed') THEN COALESCE(`collection_finished_at`, `collected_at`, `gmt_updated`)
      ELSE `collection_finished_at`
    END
WHERE `collection_started_at` IS NULL
   OR (`status` IN ('success', 'failed') AND `collection_finished_at` IS NULL);
