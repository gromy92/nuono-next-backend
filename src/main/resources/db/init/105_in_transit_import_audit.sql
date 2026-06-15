SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `in_transit_import_batch` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `file_name` VARCHAR(255) DEFAULT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `status` ENUM('previewed','imported','failed') NOT NULL DEFAULT 'previewed',
    `total_row_count` INT NOT NULL DEFAULT 0,
    `valid_row_count` INT NOT NULL DEFAULT 0,
    `error_count` INT NOT NULL DEFAULT 0,
    `warning_count` INT NOT NULL DEFAULT 0,
    `summary_json` LONGTEXT DEFAULT NULL,
    `raw_preview_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_in_transit_import_batch_owner_status` (`owner_user_id`, `status`, `is_deleted`),
    KEY `idx_in_transit_import_batch_updated` (`owner_user_id`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
