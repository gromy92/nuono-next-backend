-- Business calendar and activity factor rule drafts backed by generic version publishing.

SET NAMES utf8mb4;

INSERT INTO `version_publish_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('operation_calendar_rule', 82000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `operation_calendar_rule` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `rule_name` VARCHAR(160) NOT NULL,
    `activity_type` VARCHAR(60) NOT NULL,
    `date_from` DATE NOT NULL,
    `date_to` DATE NOT NULL,
    `recurring_expression` VARCHAR(120) DEFAULT NULL,
    `target_scope_type` VARCHAR(40) NOT NULL,
    `target_scope_value` VARCHAR(500) DEFAULT NULL,
    `factor_value` DECIMAL(10,4) NOT NULL,
    `factor_purpose` VARCHAR(60) NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `publish_record_id` BIGINT DEFAULT NULL,
    `publish_status` VARCHAR(40) NOT NULL,
    `publish_source_role` VARCHAR(60) NOT NULL DEFAULT 'operator',
    `publish_source_label` VARCHAR(80) NOT NULL DEFAULT '运营发布',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_operation_calendar_rule_scope_active` (`owner_user_id`, `store_code`, `site_code`, `enabled`, `publish_status`),
    KEY `idx_operation_calendar_rule_publish` (`publish_record_id`),
    KEY `idx_operation_calendar_rule_date` (`date_from`, `date_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
