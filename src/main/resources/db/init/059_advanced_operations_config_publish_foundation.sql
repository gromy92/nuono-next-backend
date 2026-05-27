-- Generic version publishing foundation for advanced operations configuration.
-- Domain content stays in feature-owned tables; this layer stores publish metadata and audit evidence only.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `version_publish_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `version_publish_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('version_publish_record', 80000, NOW(), NOW()),
    ('version_publish_audit_log', 81000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `version_publish_record` (
    `id` BIGINT NOT NULL,
    `domain_type` VARCHAR(80) NOT NULL,
    `domain_ref_id` BIGINT NOT NULL,
    `version_no` VARCHAR(80) NOT NULL,
    `status` VARCHAR(40) NOT NULL,
    `scope_summary` VARCHAR(1000) DEFAULT NULL,
    `previous_version_id` BIGINT DEFAULT NULL,
    `published_by` BIGINT DEFAULT NULL,
    `published_at` DATETIME DEFAULT NULL,
    `change_summary` VARCHAR(1000) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version_publish_domain_version` (`domain_type`, `domain_ref_id`, `version_no`),
    KEY `idx_version_publish_current` (`domain_type`, `domain_ref_id`, `status`, `published_at`, `id`),
    KEY `idx_version_publish_domain_history` (`domain_type`, `domain_ref_id`, `gmt_create`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `version_publish_audit_log` (
    `id` BIGINT NOT NULL,
    `publish_record_id` BIGINT DEFAULT NULL,
    `domain_type` VARCHAR(80) NOT NULL,
    `domain_ref_id` BIGINT DEFAULT NULL,
    `action` VARCHAR(60) NOT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `operator_role` VARCHAR(80) DEFAULT NULL,
    `before_snapshot` LONGTEXT DEFAULT NULL,
    `after_snapshot` LONGTEXT DEFAULT NULL,
    `message` VARCHAR(1000) DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_version_publish_audit_domain` (`domain_type`, `domain_ref_id`, `id`),
    KEY `idx_version_publish_audit_record` (`publish_record_id`, `id`),
    KEY `idx_version_publish_audit_operator` (`operator_user_id`, `operator_role`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
