SET NAMES utf8mb4;

SET @add_in_transit_batch_estimated_arrival_source = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'estimated_arrival_source'
    ),
    'SELECT ''in_transit_batch_estimated_arrival_source_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `estimated_arrival_source` VARCHAR(40) DEFAULT NULL AFTER `estimated_arrival_at`'
);
PREPARE add_in_transit_batch_estimated_arrival_source_stmt FROM @add_in_transit_batch_estimated_arrival_source;
EXECUTE add_in_transit_batch_estimated_arrival_source_stmt;
DEALLOCATE PREPARE add_in_transit_batch_estimated_arrival_source_stmt;

SET @add_in_transit_batch_estimated_arrival_source_detail = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'estimated_arrival_source_detail'
    ),
    'SELECT ''in_transit_batch_estimated_arrival_source_detail_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `estimated_arrival_source_detail` VARCHAR(500) DEFAULT NULL AFTER `estimated_arrival_source`'
);
PREPARE add_in_transit_batch_estimated_arrival_source_detail_stmt FROM @add_in_transit_batch_estimated_arrival_source_detail;
EXECUTE add_in_transit_batch_estimated_arrival_source_detail_stmt;
DEALLOCATE PREPARE add_in_transit_batch_estimated_arrival_source_detail_stmt;

SET @add_in_transit_batch_estimated_arrival_updated_at = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'estimated_arrival_updated_at'
    ),
    'SELECT ''in_transit_batch_estimated_arrival_updated_at_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `estimated_arrival_updated_at` DATETIME DEFAULT NULL AFTER `estimated_arrival_source_detail`'
);
PREPARE add_in_transit_batch_estimated_arrival_updated_at_stmt FROM @add_in_transit_batch_estimated_arrival_updated_at;
EXECUTE add_in_transit_batch_estimated_arrival_updated_at_stmt;
DEALLOCATE PREPARE add_in_transit_batch_estimated_arrival_updated_at_stmt;

SET @add_in_transit_batch_estimated_arrival_updated_by = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'estimated_arrival_updated_by'
    ),
    'SELECT ''in_transit_batch_estimated_arrival_updated_by_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `estimated_arrival_updated_by` BIGINT DEFAULT NULL AFTER `estimated_arrival_updated_at`'
);
PREPARE add_in_transit_batch_estimated_arrival_updated_by_stmt FROM @add_in_transit_batch_estimated_arrival_updated_by;
EXECUTE add_in_transit_batch_estimated_arrival_updated_by_stmt;
DEALLOCATE PREPARE add_in_transit_batch_estimated_arrival_updated_by_stmt;

SET @add_in_transit_batch_eta_source_idx = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND index_name = 'idx_in_transit_batch_eta_source'
    ),
    'SELECT ''idx_in_transit_batch_eta_source_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD KEY `idx_in_transit_batch_eta_source` (`owner_user_id`, `estimated_arrival_source`, `estimated_arrival_at`, `is_deleted`)'
);
PREPARE add_in_transit_batch_eta_source_idx_stmt FROM @add_in_transit_batch_eta_source_idx;
EXECUTE add_in_transit_batch_eta_source_idx_stmt;
DEALLOCATE PREPARE add_in_transit_batch_eta_source_idx_stmt;

UPDATE `in_transit_batch`
SET estimated_arrival_source = 'LEGACY_IMPORTED'
WHERE `estimated_arrival_at` IS NOT NULL
  AND `estimated_arrival_source` IS NULL
  AND `is_deleted` = b'0';
