-- Lifecycle rule configuration drafts backed by generic version publishing.

SET NAMES utf8mb4;

INSERT INTO `version_publish_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('operation_lifecycle_rule', 83000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `operation_lifecycle_rule` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `rule_version` VARCHAR(80) NOT NULL,
    `source_rule_version` VARCHAR(80) DEFAULT NULL,
    `new_max_age_days` INT NOT NULL,
    `new_min_age_days` INT NOT NULL,
    `high_price_threshold` DECIMAL(12,4) NOT NULL,
    `growth_min_sales_growth_rate` DECIMAL(10,4) NOT NULL,
    `growth_min_pv_growth_rate` DECIMAL(10,4) NOT NULL,
    `growth_min_monthly_sales` DECIMAL(12,4) NOT NULL,
    `growth_min_active_sales_days` INT NOT NULL,
    `growth_max_volatility` DECIMAL(10,4) NOT NULL,
    `stable_min_pv_growth_rate` DECIMAL(10,4) NOT NULL,
    `stable_volatility_min` DECIMAL(10,4) NOT NULL,
    `stable_volatility_max` DECIMAL(10,4) NOT NULL,
    `decline_max_volatility` DECIMAL(10,4) NOT NULL,
    `decline_max_sales_growth_rate` DECIMAL(10,4) NOT NULL,
    `long_tail_max_volatility` DECIMAL(10,4) NOT NULL,
    `long_tail_max_monthly_sales` DECIMAL(12,4) NOT NULL,
    `publish_record_id` BIGINT DEFAULT NULL,
    `publish_status` VARCHAR(40) NOT NULL,
    `publish_source_role` VARCHAR(60) NOT NULL DEFAULT 'operator',
    `publish_source_label` VARCHAR(80) NOT NULL DEFAULT '运营发布',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_operation_lifecycle_rule_scope_status` (`owner_user_id`, `store_code`, `site_code`, `publish_status`, `gmt_updated`, `id`),
    KEY `idx_operation_lifecycle_rule_publish` (`publish_record_id`),
    KEY `idx_operation_lifecycle_rule_version` (`rule_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
