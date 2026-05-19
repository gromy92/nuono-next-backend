CREATE TABLE IF NOT EXISTS `noon_attribute_template` (
    `id` BIGINT NOT NULL,
    `project_code` VARCHAR(40) NOT NULL,
    `store_code` VARCHAR(40) NOT NULL,
    `product_fulltype` VARCHAR(255) NOT NULL,
    `source` VARCHAR(40) NOT NULL DEFAULT 'noon',
    `status` VARCHAR(40) NOT NULL DEFAULT 'ready',
    `template_hash` VARCHAR(128) DEFAULT NULL,
    `raw_json` LONGTEXT NOT NULL,
    `normalized_json` LONGTEXT DEFAULT NULL,
    `fetched_at` DATETIME NOT NULL,
    `error_message` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_attribute_template_scope` (`project_code`, `store_code`, `product_fulltype`),
    KEY `idx_noon_attribute_template_fulltype` (`product_fulltype`),
    KEY `idx_noon_attribute_template_fetched_at` (`fetched_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'noon_attribute_template', GREATEST(COALESCE(MAX(`id`), 58000), 58000), NOW(), NOW() FROM `noon_attribute_template`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
