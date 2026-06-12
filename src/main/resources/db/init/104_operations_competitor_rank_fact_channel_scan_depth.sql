-- Support separate natural/sponsored rank facts and explicit scan depth.

SET NAMES utf8mb4;

SET @ops_comp_keyword_run_add_requested_result_limit := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_keyword_run'
              AND COLUMN_NAME = 'requested_result_limit'
        ),
        'ALTER TABLE `operations_competitor_keyword_run` ADD COLUMN `requested_result_limit` INT DEFAULT NULL AFTER `result_count`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_keyword_run_add_requested_result_limit;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_rank_fact_add_rank_channel := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_rank_fact'
              AND COLUMN_NAME = 'rank_channel'
        ),
        'ALTER TABLE `operations_competitor_rank_fact` ADD COLUMN `rank_channel` VARCHAR(32) NOT NULL DEFAULT ''ORGANIC'' AFTER `tracked_product_type`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_rank_fact_add_rank_channel;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_rank_fact_add_scan_depth := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_rank_fact'
              AND COLUMN_NAME = 'scan_depth'
        ),
        'ALTER TABLE `operations_competitor_rank_fact` ADD COLUMN `scan_depth` INT DEFAULT NULL AFTER `rank_no`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_rank_fact_add_scan_depth;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `operations_competitor_rank_fact`
SET `rank_channel` = CASE WHEN `is_sponsored` = b'1' THEN 'SPONSORED' ELSE 'ORGANIC' END
WHERE `rank_channel` = 'ORGANIC'
  AND `is_sponsored` = b'1';

SET @ops_comp_rank_fact_drop_old_unique := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_rank_fact'
              AND INDEX_NAME = 'uk_ops_comp_rank_fact_run_product'
        ),
        'ALTER TABLE `operations_competitor_rank_fact` DROP INDEX `uk_ops_comp_rank_fact_run_product`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_rank_fact_drop_old_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_rank_fact_add_channel_unique := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_rank_fact'
              AND INDEX_NAME = 'uk_ops_comp_rank_fact_run_product_channel'
        ),
        'ALTER TABLE `operations_competitor_rank_fact` ADD UNIQUE KEY `uk_ops_comp_rank_fact_run_product_channel` (`keyword_run_id`, `tracked_product_type`, `noon_product_code`, `rank_channel`)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_rank_fact_add_channel_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
