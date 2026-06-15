-- Competitor product daily detail snapshots and field-level change events.

CREATE TABLE IF NOT EXISTS `operations_competitor_product_snapshot` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `watch_product_id` BIGINT NOT NULL,
    `competitor_product_id` BIGINT DEFAULT NULL,
    `subject_type` VARCHAR(32) NOT NULL,
    `site_code` VARCHAR(32) NOT NULL,
    `noon_product_code` VARCHAR(80) NOT NULL,
    `code_type` VARCHAR(32) NOT NULL,
    `fact_date` DATE NOT NULL,
    `captured_at` DATETIME NOT NULL,
    `source_task_id` BIGINT DEFAULT NULL,
    `source_run_id` BIGINT DEFAULT NULL,
    `detail_url` VARCHAR(1000) DEFAULT NULL,
    `title_en` VARCHAR(500) DEFAULT NULL,
    `title_ar` VARCHAR(500) DEFAULT NULL,
    `brand` VARCHAR(200) DEFAULT NULL,
    `seller_name` VARCHAR(255) DEFAULT NULL,
    `price_amount` DECIMAL(18,4) DEFAULT NULL,
    `currency_code` VARCHAR(16) DEFAULT NULL,
    `rating` DECIMAL(4,2) DEFAULT NULL,
    `review_count` INT DEFAULT NULL,
    `main_image_url_raw` VARCHAR(1000) DEFAULT NULL,
    `main_image_url_normalized` VARCHAR(1000) DEFAULT NULL,
    `main_image_asset_key` VARCHAR(255) DEFAULT NULL,
    `supermall_enabled` BIT(1) DEFAULT NULL,
    `sold_recently_text` VARCHAR(255) DEFAULT NULL,
    `logistics_tags_json` JSON DEFAULT NULL,
    `badges_json` JSON DEFAULT NULL,
    `availability_status` VARCHAR(64) DEFAULT NULL,
    `snapshot_hash` CHAR(64) NOT NULL,
    `raw_detail_json` JSON DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_comp_snapshot_daily` (`watch_product_id`, `subject_type`, `noon_product_code`, `fact_date`),
    KEY `idx_ops_comp_snapshot_watch_date` (`watch_product_id`, `fact_date`),
    KEY `idx_ops_comp_snapshot_product_date` (`watch_product_id`, `competitor_product_id`, `fact_date`),
    KEY `idx_ops_comp_snapshot_code_date` (`site_code`, `noon_product_code`, `fact_date`),
    KEY `idx_ops_comp_snapshot_task` (`source_task_id`),
    KEY `idx_ops_comp_snapshot_run` (`source_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_competitor_product_change_event` (
    `id` BIGINT NOT NULL,
    `snapshot_id` BIGINT NOT NULL,
    `previous_snapshot_id` BIGINT DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `watch_product_id` BIGINT NOT NULL,
    `competitor_product_id` BIGINT DEFAULT NULL,
    `subject_type` VARCHAR(32) NOT NULL,
    `site_code` VARCHAR(32) NOT NULL,
    `noon_product_code` VARCHAR(80) NOT NULL,
    `fact_date` DATE NOT NULL,
    `field_key` VARCHAR(64) NOT NULL,
    `field_label` VARCHAR(64) NOT NULL,
    `change_type` VARCHAR(32) NOT NULL,
    `old_value_json` JSON DEFAULT NULL,
    `new_value_json` JSON DEFAULT NULL,
    `severity` VARCHAR(16) NOT NULL DEFAULT 'INFO',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ops_comp_change_watch_date` (`watch_product_id`, `fact_date`),
    KEY `idx_ops_comp_change_product_date` (`watch_product_id`, `noon_product_code`, `fact_date`),
    KEY `idx_ops_comp_change_field_date` (`field_key`, `fact_date`),
    KEY `idx_ops_comp_change_snapshot` (`snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_product_snapshot', GREATEST(COALESCE(MAX(`id`) + 1, 260000), 260000), NOW(), NOW()
FROM `operations_competitor_product_snapshot`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_product_change_event', GREATEST(COALESCE(MAX(`id`) + 1, 270000), 270000), NOW(), NOW()
FROM `operations_competitor_product_change_event`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
