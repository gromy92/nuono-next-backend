SET NAMES utf8mb4;

SET @add_in_transit_batch_box_count = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'box_count'
    ),
    'SELECT ''in_transit_batch_box_count_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `box_count` INT DEFAULT NULL AFTER `missing_fields_json`'
);
PREPARE add_in_transit_batch_box_count_stmt FROM @add_in_transit_batch_box_count;
EXECUTE add_in_transit_batch_box_count_stmt;
DEALLOCATE PREPARE add_in_transit_batch_box_count_stmt;

UPDATE `in_transit_batch` batch
LEFT JOIN (
    SELECT
        `owner_user_id`,
        `batch_id`,
        COUNT(DISTINCT NULLIF(TRIM(`box_no`), '')) AS `box_count`
    FROM `in_transit_goods_line`
    WHERE `is_deleted` = b'0'
    GROUP BY `owner_user_id`, `batch_id`
) line_aggregate
  ON line_aggregate.`owner_user_id` = batch.`owner_user_id`
 AND line_aggregate.`batch_id` = batch.`id`
SET batch.`box_count` = line_aggregate.`box_count`
WHERE batch.`is_deleted` = b'0';

SET @drop_in_transit_batch_remark = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'remark'
    ),
    'ALTER TABLE `in_transit_batch` DROP COLUMN `remark`',
    'SELECT ''in_transit_batch_remark_absent'' AS stage'
);
PREPARE drop_in_transit_batch_remark_stmt FROM @drop_in_transit_batch_remark;
EXECUTE drop_in_transit_batch_remark_stmt;
DEALLOCATE PREPARE drop_in_transit_batch_remark_stmt;

SET @drop_in_transit_goods_line_remark = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND column_name = 'remark'
    ),
    'ALTER TABLE `in_transit_goods_line` DROP COLUMN `remark`',
    'SELECT ''in_transit_goods_line_remark_absent'' AS stage'
);
PREPARE drop_in_transit_goods_line_remark_stmt FROM @drop_in_transit_goods_line_remark;
EXECUTE drop_in_transit_goods_line_remark_stmt;
DEALLOCATE PREPARE drop_in_transit_goods_line_remark_stmt;

SET @drop_in_transit_package_remark = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'remark'
    ),
    'ALTER TABLE `in_transit_package` DROP COLUMN `remark`',
    'SELECT ''in_transit_package_remark_absent'' AS stage'
);
PREPARE drop_in_transit_package_remark_stmt FROM @drop_in_transit_package_remark;
EXECUTE drop_in_transit_package_remark_stmt;
DEALLOCATE PREPARE drop_in_transit_package_remark_stmt;
