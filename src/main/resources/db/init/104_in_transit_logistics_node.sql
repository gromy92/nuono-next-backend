SET NAMES utf8mb4;

SET @add_latest_node_status = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'latest_node_status'
    ),
    'SELECT ''latest_node_status_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `latest_node_status` ENUM(''created'',''handed_to_forwarder'',''departed_origin'',''in_transit'',''arrived_port'',''customs_clearance'',''customs_released'',''delivering'',''warehouse_received'',''exception'',''cancelled'') DEFAULT NULL'
);
PREPARE add_latest_node_status_stmt FROM @add_latest_node_status;
EXECUTE add_latest_node_status_stmt;
DEALLOCATE PREPARE add_latest_node_status_stmt;

SET @add_latest_node_happened_at = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'latest_node_happened_at'
    ),
    'SELECT ''latest_node_happened_at_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `latest_node_happened_at` DATETIME DEFAULT NULL'
);
PREPARE add_latest_node_happened_at_stmt FROM @add_latest_node_happened_at;
EXECUTE add_latest_node_happened_at_stmt;
DEALLOCATE PREPARE add_latest_node_happened_at_stmt;

SET @add_latest_node_description = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_batch'
          AND column_name = 'latest_node_description'
    ),
    'SELECT ''latest_node_description_exists'' AS stage',
    'ALTER TABLE `in_transit_batch` ADD COLUMN `latest_node_description` VARCHAR(1000) DEFAULT NULL'
);
PREPARE add_latest_node_description_stmt FROM @add_latest_node_description;
EXECUTE add_latest_node_description_stmt;
DEALLOCATE PREPARE add_latest_node_description_stmt;

CREATE TABLE IF NOT EXISTS `in_transit_logistics_node` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `node_status` ENUM('created','handed_to_forwarder','departed_origin','in_transit','arrived_port','customs_clearance','customs_released','delivering','warehouse_received','exception','cancelled') NOT NULL,
    `node_happened_at` DATETIME NOT NULL,
    `description` VARCHAR(1000) DEFAULT NULL,
    `operator_name` VARCHAR(160) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_in_transit_logistics_node_batch_time` (`owner_user_id`, `batch_id`, `node_happened_at`, `id`),
    KEY `idx_in_transit_logistics_node_status` (`owner_user_id`, `node_status`, `node_happened_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
