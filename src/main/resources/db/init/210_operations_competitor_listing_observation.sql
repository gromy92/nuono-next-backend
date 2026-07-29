-- Deduplicate competitor list observations across watch products for one business day.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `operations_competitor_listing_observation` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(32) NOT NULL,
    `noon_product_code` VARCHAR(80) NOT NULL,
    `code_type` VARCHAR(32) NOT NULL,
    `fact_date` DATE NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `acquisition_mode` VARCHAR(32) NOT NULL,
    `lease_token` VARCHAR(160) DEFAULT NULL,
    `canonical_url` VARCHAR(1000) DEFAULT NULL,
    `title_en` VARCHAR(500) DEFAULT NULL,
    `title_ar` VARCHAR(500) DEFAULT NULL,
    `image_url` VARCHAR(1000) DEFAULT NULL,
    `price_amount` DECIMAL(18,4) DEFAULT NULL,
    `currency_code` VARCHAR(16) DEFAULT NULL,
    `tags_json` JSON DEFAULT NULL,
    `source_url` VARCHAR(1000) DEFAULT NULL,
    `parser_version` VARCHAR(80) DEFAULT NULL,
    `provider_http_status` INT DEFAULT NULL,
    `response_hash` VARCHAR(128) DEFAULT NULL,
    `captured_at` DATETIME DEFAULT NULL,
    `last_error_code` VARCHAR(128) DEFAULT NULL,
    `last_error_message` VARCHAR(1024) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_comp_listing_observation_daily` (
        `owner_user_id`, `store_code`, `site_code`, `noon_product_code`, `fact_date`
    ),
    KEY `idx_ops_comp_listing_observation_status` (`status`, `gmt_updated`),
    KEY `idx_ops_comp_listing_observation_code` (`site_code`, `noon_product_code`, `fact_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `operations_competitor_analysis_id_sequence` (
    `sequence_name`, `next_id`, `gmt_create`, `gmt_updated`
)
SELECT
    'operations_competitor_listing_observation',
    GREATEST(COALESCE(MAX(`id`) + 1, 280000), 280000),
    NOW(),
    NOW()
FROM `operations_competitor_listing_observation`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
