-- Operations configuration suite bundle root.
-- Activity factors and lifecycle rules will attach to this bundle in later slices.

SET NAMES utf8mb4;

INSERT INTO `version_publish_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('operation_config_bundle', 86000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `operation_config_bundle` (
    `id` BIGINT NOT NULL,
    `publish_record_id` BIGINT NOT NULL,
    `version_no` VARCHAR(80) NOT NULL,
    `display_name` VARCHAR(160) DEFAULT NULL,
    `publish_source_role` VARCHAR(60) NOT NULL,
    `publish_source_label` VARCHAR(80) NOT NULL,
    `scope_summary` VARCHAR(1000) NOT NULL,
    `affected_store_count` INT NOT NULL DEFAULT 0,
    `activity_rule_count` INT NOT NULL DEFAULT 0,
    `lifecycle_rule_summary` VARCHAR(200) NOT NULL DEFAULT '未配置',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_operation_config_bundle_publish` (`publish_record_id`),
    UNIQUE KEY `uk_operation_config_bundle_version` (`version_no`),
    KEY `idx_operation_config_bundle_updated` (`gmt_updated`, `id`),
    KEY `idx_operation_config_bundle_source` (`publish_source_role`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `operation_config_bundle_scope` (
    `bundle_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_operation_config_bundle_scope` (`bundle_id`, `owner_user_id`, `store_code`, `site_code`),
    KEY `idx_operation_config_bundle_scope_store` (`owner_user_id`, `store_code`, `site_code`),
    CONSTRAINT `fk_operation_config_bundle_scope_bundle`
        FOREIGN KEY (`bundle_id`) REFERENCES `operation_config_bundle` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
