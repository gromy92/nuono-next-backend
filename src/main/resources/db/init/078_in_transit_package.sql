SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `in_transit_package` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `box_no` VARCHAR(160) NOT NULL,
    `tracking_no` VARCHAR(160) DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b'0' THEN 1 ELSE NULL END) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_in_transit_package_box` (`owner_user_id`, `batch_id`, `box_no`, `active_unique_key`),
    KEY `idx_in_transit_package_batch` (`owner_user_id`, `batch_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_line_package_id = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND column_name = 'package_id'
    ),
    'SELECT ''package_id_exists'' AS stage',
    'ALTER TABLE `in_transit_goods_line` ADD COLUMN `package_id` BIGINT DEFAULT NULL AFTER `batch_id`'
);
PREPARE add_line_package_id_stmt FROM @add_line_package_id;
EXECUTE add_line_package_id_stmt;
DEALLOCATE PREPARE add_line_package_id_stmt;

SET @add_line_box_no = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND column_name = 'box_no'
    ),
    'SELECT ''box_no_exists'' AS stage',
    'ALTER TABLE `in_transit_goods_line` ADD COLUMN `box_no` VARCHAR(160) DEFAULT NULL AFTER `package_id`'
);
PREPARE add_line_box_no_stmt FROM @add_line_box_no;
EXECUTE add_line_box_no_stmt;
DEALLOCATE PREPARE add_line_box_no_stmt;

SET @add_line_package_idx = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND index_name = 'idx_in_transit_goods_line_package'
    ),
    'SELECT ''idx_in_transit_goods_line_package_exists'' AS stage',
    'ALTER TABLE `in_transit_goods_line` ADD KEY `idx_in_transit_goods_line_package` (`owner_user_id`, `batch_id`, `package_id`, `is_deleted`)'
);
PREPARE add_line_package_idx_stmt FROM @add_line_package_idx;
EXECUTE add_line_package_idx_stmt;
DEALLOCATE PREPARE add_line_package_idx_stmt;
