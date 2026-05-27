-- User-facing typed operations configuration version library.
-- Each row is either a business calendar version or a product lifecycle version.

SET NAMES utf8mb4;

INSERT INTO `version_publish_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('operation_config_typed_version', 88000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `operation_config_typed_version` (
    `id` BIGINT NOT NULL,
    `version_no` VARCHAR(80) NOT NULL,
    `display_name` VARCHAR(160) NOT NULL,
    `config_type` VARCHAR(40) NOT NULL,
    `status` VARCHAR(40) NOT NULL,
    `source_version_no` VARCHAR(80) DEFAULT NULL,
    `source_label` VARCHAR(120) DEFAULT NULL,
    `summary` VARCHAR(240) DEFAULT NULL,
    `item_count` INT NOT NULL DEFAULT 0,
    `scope_summary` VARCHAR(1000) NOT NULL DEFAULT '未设置范围',
    `content_json` MEDIUMTEXT NOT NULL,
    `audit_json` MEDIUMTEXT DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_operation_config_typed_version_no` (`version_no`),
    KEY `idx_operation_config_typed_version_type_status` (`config_type`, `status`, `gmt_updated`, `id`),
    KEY `idx_operation_config_typed_version_updated` (`gmt_updated`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @operation_config_typed_version_add_audit_json := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operation_config_typed_version'
              AND COLUMN_NAME = 'audit_json'
        ),
        'SELECT 1',
        'ALTER TABLE `operation_config_typed_version` ADD COLUMN `audit_json` MEDIUMTEXT DEFAULT NULL AFTER `content_json`'
    )
);
PREPARE stmt FROM @operation_config_typed_version_add_audit_json;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
