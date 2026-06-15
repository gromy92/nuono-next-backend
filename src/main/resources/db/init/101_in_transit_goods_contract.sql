SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `in_transit_forwarder` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `forwarder_code` VARCHAR(80) NOT NULL,
    `forwarder_name` VARCHAR(160) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_in_transit_forwarder_code` (`owner_user_id`, `forwarder_code`),
    KEY `idx_in_transit_forwarder_owner_status` (`owner_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `in_transit_forwarder_alias` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `standard_forwarder_id` BIGINT NOT NULL,
    `raw_forwarder_name` VARCHAR(255) NOT NULL,
    `normalized_raw_forwarder_name` VARCHAR(255) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b'0' THEN 1 ELSE NULL END) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_in_transit_forwarder_alias_active` (`owner_user_id`, `normalized_raw_forwarder_name`, `active_unique_key`),
    KEY `idx_in_transit_forwarder_alias_forwarder` (`standard_forwarder_id`),
    KEY `idx_in_transit_forwarder_alias_owner_status` (`owner_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `in_transit_contract_probe` (
    `id` BIGINT NOT NULL,
    `transport_mode` ENUM('SEA','AIR') NOT NULL,
    `batch_status` ENUM('draft','pending_shipment','shipped','in_transit','customs_clearance','delivering','warehouse_received','exception','completed','cancelled') NOT NULL,
    `node_status` ENUM('created','handed_to_forwarder','departed_origin','in_transit','arrived_port','customs_clearance','customs_released','delivering','warehouse_received','exception','cancelled') NOT NULL,
    `quality_status` ENUM('forwarder_unmatched','forwarder_matched') NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
