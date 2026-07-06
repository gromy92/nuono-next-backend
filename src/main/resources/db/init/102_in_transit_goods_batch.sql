SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `in_transit_batch` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `standard_forwarder_id` BIGINT DEFAULT NULL,
    `raw_forwarder_name` VARCHAR(255) DEFAULT NULL,
    `normalized_raw_forwarder_name` VARCHAR(255) DEFAULT NULL,
    `forwarder_quality_status` VARCHAR(60) NOT NULL DEFAULT 'forwarder_unmatched',
    `transport_mode` ENUM('SEA','AIR') DEFAULT NULL,
    `batch_status` ENUM('draft','pending_shipment','shipped','in_transit','customs_clearance','delivering','warehouse_received','exception','completed','cancelled') NOT NULL DEFAULT 'draft',
    `target_store_code` VARCHAR(80) DEFAULT NULL,
    `target_site_code` VARCHAR(80) DEFAULT NULL,
    `target_warehouse_name` VARCHAR(160) DEFAULT NULL,
    `departure_date` DATE DEFAULT NULL,
    `eta_date` DATE DEFAULT NULL,
    `tracking_no` VARCHAR(160) DEFAULT NULL,
    `container_no` VARCHAR(160) DEFAULT NULL,
    `batch_reference_no` VARCHAR(160) DEFAULT NULL,
    `external_shipment_no` VARCHAR(160) DEFAULT NULL,
    `source_created_at` DATETIME DEFAULT NULL,
    `estimated_departure_at` DATETIME DEFAULT NULL,
    `estimated_arrival_at` DATETIME DEFAULT NULL,
    `estimated_arrival_source` VARCHAR(40) DEFAULT NULL,
    `estimated_arrival_source_detail` VARCHAR(500) DEFAULT NULL,
    `estimated_arrival_updated_at` DATETIME DEFAULT NULL,
    `estimated_arrival_updated_by` BIGINT DEFAULT NULL,
    `delivery_appointment_text` VARCHAR(120) DEFAULT NULL,
    `missing_fields_json` LONGTEXT DEFAULT NULL,
    `box_count` INT DEFAULT NULL,
    `sku_count` INT DEFAULT NULL,
    `shipped_quantity_total` INT DEFAULT NULL,
    `received_quantity_total` INT DEFAULT NULL,
    `remaining_quantity_total` INT DEFAULT NULL,
    `carton_count_total` INT DEFAULT NULL,
    `total_weight_kg` DECIMAL(18,6) DEFAULT NULL,
    `total_volume_cbm` DECIMAL(18,6) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_in_transit_batch_filter` (`owner_user_id`, `standard_forwarder_id`, `transport_mode`, `target_store_code`, `target_site_code`, `batch_status`),
    KEY `idx_in_transit_batch_eta` (`owner_user_id`, `eta_date`, `batch_status`),
    KEY `idx_in_transit_batch_eta_source` (`owner_user_id`, `estimated_arrival_source`, `estimated_arrival_at`, `is_deleted`),
    KEY `idx_in_transit_batch_raw_forwarder` (`owner_user_id`, `normalized_raw_forwarder_name`),
    KEY `idx_in_transit_batch_tracking` (`owner_user_id`, `tracking_no`, `container_no`, `batch_reference_no`),
    KEY `idx_in_transit_batch_external_shipment` (`owner_user_id`, `external_shipment_no`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_in_transit_batch_external_shipment_no = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'external_shipment_no'
    ),
    'SELECT ''in_transit_batch_external_shipment_no_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `external_shipment_no` VARCHAR(160) DEFAULT NULL AFTER `batch_reference_no`'
);
PREPARE add_in_transit_batch_external_shipment_no_stmt FROM @add_in_transit_batch_external_shipment_no;
EXECUTE add_in_transit_batch_external_shipment_no_stmt;
DEALLOCATE PREPARE add_in_transit_batch_external_shipment_no_stmt;

SET @add_in_transit_batch_source_created_at = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'source_created_at'
    ),
    'SELECT ''in_transit_batch_source_created_at_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `source_created_at` DATETIME DEFAULT NULL AFTER `external_shipment_no`'
);
PREPARE add_in_transit_batch_source_created_at_stmt FROM @add_in_transit_batch_source_created_at;
EXECUTE add_in_transit_batch_source_created_at_stmt;
DEALLOCATE PREPARE add_in_transit_batch_source_created_at_stmt;

SET @add_in_transit_batch_estimated_departure_at = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'estimated_departure_at'
    ),
    'SELECT ''in_transit_batch_estimated_departure_at_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `estimated_departure_at` DATETIME DEFAULT NULL AFTER `source_created_at`'
);
PREPARE add_in_transit_batch_estimated_departure_at_stmt FROM @add_in_transit_batch_estimated_departure_at;
EXECUTE add_in_transit_batch_estimated_departure_at_stmt;
DEALLOCATE PREPARE add_in_transit_batch_estimated_departure_at_stmt;

SET @add_in_transit_batch_estimated_arrival_at = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'estimated_arrival_at'
    ),
    'SELECT ''in_transit_batch_estimated_arrival_at_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `estimated_arrival_at` DATETIME DEFAULT NULL AFTER `estimated_departure_at`'
);
PREPARE add_in_transit_batch_estimated_arrival_at_stmt FROM @add_in_transit_batch_estimated_arrival_at;
EXECUTE add_in_transit_batch_estimated_arrival_at_stmt;
DEALLOCATE PREPARE add_in_transit_batch_estimated_arrival_at_stmt;

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

SET @add_in_transit_batch_delivery_appointment_text = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'delivery_appointment_text'
    ),
    'SELECT ''in_transit_batch_delivery_appointment_text_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `delivery_appointment_text` VARCHAR(120) DEFAULT NULL AFTER `estimated_arrival_at`'
);
PREPARE add_in_transit_batch_delivery_appointment_text_stmt FROM @add_in_transit_batch_delivery_appointment_text;
EXECUTE add_in_transit_batch_delivery_appointment_text_stmt;
DEALLOCATE PREPARE add_in_transit_batch_delivery_appointment_text_stmt;

SET @add_in_transit_batch_external_shipment_idx = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND index_name = 'idx_in_transit_batch_external_shipment'
    ),
    'SELECT ''idx_in_transit_batch_external_shipment_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD KEY `idx_in_transit_batch_external_shipment` (`owner_user_id`, `external_shipment_no`, `is_deleted`)'
);
PREPARE add_in_transit_batch_external_shipment_idx_stmt FROM @add_in_transit_batch_external_shipment_idx;
EXECUTE add_in_transit_batch_external_shipment_idx_stmt;
DEALLOCATE PREPARE add_in_transit_batch_external_shipment_idx_stmt;

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
