-- Noon official warehouse automatic appointment state.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `official_warehouse_appointment` (
    `id` BIGINT NOT NULL,
    `asn_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `store_name` VARCHAR(200) DEFAULT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `project_code` VARCHAR(100) DEFAULT NULL,
    `partner_id` VARCHAR(80) DEFAULT NULL,
    `local_asn_no` VARCHAR(120) NOT NULL,
    `noon_asn_nr` VARCHAR(120) NOT NULL,
    `total_units` INT NOT NULL,
    `warehouse_from` VARCHAR(120) DEFAULT NULL,
    `warehouse_to_partner_code` VARCHAR(80) NOT NULL,
    `warehouse_to_code` VARCHAR(100) DEFAULT NULL,
    `ap_start_date` DATE NOT NULL,
    `ap_end_date` DATE NOT NULL,
    `ap_time_range` VARCHAR(500) DEFAULT NULL,
    `is_available_today` BIT(1) NOT NULL DEFAULT b'0',
    `status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    `appointment_date` DATE DEFAULT NULL,
    `appointment_slot_id` INT DEFAULT NULL,
    `appointment_time` VARCHAR(120) DEFAULT NULL,
    `gate` VARCHAR(200) DEFAULT NULL,
    `docks` VARCHAR(500) DEFAULT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `last_attempt_at` DATETIME DEFAULT NULL,
    `next_attempt_at` DATETIME DEFAULT NULL,
    `ap_success_time` DATETIME DEFAULT NULL,
    `error_stage` VARCHAR(80) DEFAULT NULL,
    `failure_type` VARCHAR(80) DEFAULT NULL,
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_official_warehouse_appointment_asn` (`asn_id`),
    KEY `idx_official_warehouse_appointment_noon_asn` (`noon_asn_nr`),
    KEY `idx_official_warehouse_appointment_store` (`owner_user_id`, `store_code`, `site_code`),
    KEY `idx_official_warehouse_appointment_status` (`status`, `next_attempt_at`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
  ('official_warehouse_appointment', 610000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();
