SET NAMES utf8mb4;

SET @add_in_transit_batch_active_reference_key = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'active_batch_reference_key'
    ),
    'SELECT ''in_transit_batch_active_reference_key_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `active_batch_reference_key` VARCHAR(160) GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b''0'' AND NULLIF(TRIM(`batch_reference_no`), '''') IS NOT NULL THEN UPPER(TRIM(`batch_reference_no`)) ELSE NULL END) STORED AFTER `is_deleted`'
);
PREPARE add_in_transit_batch_active_reference_key_stmt FROM @add_in_transit_batch_active_reference_key;
EXECUTE add_in_transit_batch_active_reference_key_stmt;
DEALLOCATE PREPARE add_in_transit_batch_active_reference_key_stmt;

SET @add_in_transit_batch_reference_active_idx = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND index_name = 'uk_in_transit_batch_reference_active'
    ),
    'SELECT ''uk_in_transit_batch_reference_active_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD UNIQUE KEY `uk_in_transit_batch_reference_active` (`owner_user_id`, `active_batch_reference_key`)'
);
PREPARE add_in_transit_batch_reference_active_idx_stmt FROM @add_in_transit_batch_reference_active_idx;
EXECUTE add_in_transit_batch_reference_active_idx_stmt;
DEALLOCATE PREPARE add_in_transit_batch_reference_active_idx_stmt;
