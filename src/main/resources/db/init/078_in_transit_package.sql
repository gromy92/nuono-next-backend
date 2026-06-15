SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `in_transit_package` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `box_no` VARCHAR(160) NOT NULL,
    `external_box_no` VARCHAR(160) DEFAULT NULL,
    `tracking_no` VARCHAR(160) DEFAULT NULL,
    `weight_kg` DECIMAL(18,6) DEFAULT NULL,
    `length_cm` DECIMAL(18,6) DEFAULT NULL,
    `width_cm` DECIMAL(18,6) DEFAULT NULL,
    `height_cm` DECIMAL(18,6) DEFAULT NULL,
    `volume_cbm` DECIMAL(18,6) DEFAULT NULL,
    `measured_weight_kg` DECIMAL(18,6) DEFAULT NULL,
    `measured_length_cm` DECIMAL(18,6) DEFAULT NULL,
    `measured_width_cm` DECIMAL(18,6) DEFAULT NULL,
    `measured_height_cm` DECIMAL(18,6) DEFAULT NULL,
    `measured_volume_cbm` DECIMAL(18,6) DEFAULT NULL,
    `package_status` VARCHAR(60) DEFAULT NULL,
    `logistics_status` VARCHAR(60) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b'0' THEN 1 ELSE NULL END) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_in_transit_package_box` (`owner_user_id`, `batch_id`, `box_no`, `active_unique_key`),
    KEY `idx_in_transit_package_external_box` (`owner_user_id`, `external_box_no`, `is_deleted`),
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

SET @add_package_external_box_no = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'external_box_no'
    ),
    'SELECT ''external_box_no_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `external_box_no` VARCHAR(160) DEFAULT NULL AFTER `box_no`'
);
PREPARE add_package_external_box_no_stmt FROM @add_package_external_box_no;
EXECUTE add_package_external_box_no_stmt;
DEALLOCATE PREPARE add_package_external_box_no_stmt;

SET @add_package_weight_kg = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'weight_kg'
    ),
    'SELECT ''weight_kg_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `weight_kg` DECIMAL(18,6) DEFAULT NULL AFTER `tracking_no`'
);
PREPARE add_package_weight_kg_stmt FROM @add_package_weight_kg;
EXECUTE add_package_weight_kg_stmt;
DEALLOCATE PREPARE add_package_weight_kg_stmt;

SET @add_package_length_cm = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'length_cm'
    ),
    'SELECT ''length_cm_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `length_cm` DECIMAL(18,6) DEFAULT NULL AFTER `weight_kg`'
);
PREPARE add_package_length_cm_stmt FROM @add_package_length_cm;
EXECUTE add_package_length_cm_stmt;
DEALLOCATE PREPARE add_package_length_cm_stmt;

SET @add_package_width_cm = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'width_cm'
    ),
    'SELECT ''width_cm_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `width_cm` DECIMAL(18,6) DEFAULT NULL AFTER `length_cm`'
);
PREPARE add_package_width_cm_stmt FROM @add_package_width_cm;
EXECUTE add_package_width_cm_stmt;
DEALLOCATE PREPARE add_package_width_cm_stmt;

SET @add_package_height_cm = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'height_cm'
    ),
    'SELECT ''height_cm_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `height_cm` DECIMAL(18,6) DEFAULT NULL AFTER `width_cm`'
);
PREPARE add_package_height_cm_stmt FROM @add_package_height_cm;
EXECUTE add_package_height_cm_stmt;
DEALLOCATE PREPARE add_package_height_cm_stmt;

SET @add_package_volume_cbm = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'volume_cbm'
    ),
    'SELECT ''volume_cbm_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `volume_cbm` DECIMAL(18,6) DEFAULT NULL AFTER `weight_kg`'
);
PREPARE add_package_volume_cbm_stmt FROM @add_package_volume_cbm;
EXECUTE add_package_volume_cbm_stmt;
DEALLOCATE PREPARE add_package_volume_cbm_stmt;

SET @add_package_measured_weight_kg = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'measured_weight_kg'
    ),
    'SELECT ''measured_weight_kg_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `measured_weight_kg` DECIMAL(18,6) DEFAULT NULL AFTER `volume_cbm`'
);
PREPARE add_package_measured_weight_kg_stmt FROM @add_package_measured_weight_kg;
EXECUTE add_package_measured_weight_kg_stmt;
DEALLOCATE PREPARE add_package_measured_weight_kg_stmt;

SET @add_package_measured_length_cm = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'measured_length_cm'
    ),
    'SELECT ''measured_length_cm_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `measured_length_cm` DECIMAL(18,6) DEFAULT NULL AFTER `measured_weight_kg`'
);
PREPARE add_package_measured_length_cm_stmt FROM @add_package_measured_length_cm;
EXECUTE add_package_measured_length_cm_stmt;
DEALLOCATE PREPARE add_package_measured_length_cm_stmt;

SET @add_package_measured_width_cm = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'measured_width_cm'
    ),
    'SELECT ''measured_width_cm_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `measured_width_cm` DECIMAL(18,6) DEFAULT NULL AFTER `measured_length_cm`'
);
PREPARE add_package_measured_width_cm_stmt FROM @add_package_measured_width_cm;
EXECUTE add_package_measured_width_cm_stmt;
DEALLOCATE PREPARE add_package_measured_width_cm_stmt;

SET @add_package_measured_height_cm = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'measured_height_cm'
    ),
    'SELECT ''measured_height_cm_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `measured_height_cm` DECIMAL(18,6) DEFAULT NULL AFTER `measured_width_cm`'
);
PREPARE add_package_measured_height_cm_stmt FROM @add_package_measured_height_cm;
EXECUTE add_package_measured_height_cm_stmt;
DEALLOCATE PREPARE add_package_measured_height_cm_stmt;

SET @add_package_measured_volume_cbm = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'measured_volume_cbm'
    ),
    'SELECT ''measured_volume_cbm_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `measured_volume_cbm` DECIMAL(18,6) DEFAULT NULL AFTER `measured_height_cm`'
);
PREPARE add_package_measured_volume_cbm_stmt FROM @add_package_measured_volume_cbm;
EXECUTE add_package_measured_volume_cbm_stmt;
DEALLOCATE PREPARE add_package_measured_volume_cbm_stmt;

SET @add_package_status = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'package_status'
    ),
    'SELECT ''package_status_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `package_status` VARCHAR(60) DEFAULT NULL AFTER `measured_volume_cbm`'
);
PREPARE add_package_status_stmt FROM @add_package_status;
EXECUTE add_package_status_stmt;
DEALLOCATE PREPARE add_package_status_stmt;

SET @add_package_logistics_status = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'logistics_status'
    ),
    'SELECT ''logistics_status_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `logistics_status` VARCHAR(60) DEFAULT NULL AFTER `package_status`'
);
PREPARE add_package_logistics_status_stmt FROM @add_package_logistics_status;
EXECUTE add_package_logistics_status_stmt;
DEALLOCATE PREPARE add_package_logistics_status_stmt;

SET @add_package_external_box_idx = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND index_name = 'idx_in_transit_package_external_box'
    ),
    'SELECT ''idx_in_transit_package_external_box_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD KEY `idx_in_transit_package_external_box` (`owner_user_id`, `external_box_no`, `is_deleted`)'
);
PREPARE add_package_external_box_idx_stmt FROM @add_package_external_box_idx;
EXECUTE add_package_external_box_idx_stmt;
DEALLOCATE PREPARE add_package_external_box_idx_stmt;

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
