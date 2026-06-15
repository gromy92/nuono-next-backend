-- Operations competitor analysis first-phase schema.

CREATE TABLE IF NOT EXISTS `operations_competitor_watch_product` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(32) NOT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `product_master_id` BIGINT DEFAULT NULL,
    `product_variant_id` BIGINT DEFAULT NULL,
    `product_site_offer_id` BIGINT DEFAULT NULL,
    `sku_parent` VARCHAR(100) DEFAULT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `child_sku` VARCHAR(160) DEFAULT NULL,
    `self_noon_product_code` VARCHAR(80) NOT NULL,
    `self_code_type` VARCHAR(32) NOT NULL,
    `title_snapshot` VARCHAR(500) DEFAULT NULL,
    `brand_snapshot` VARCHAR(200) DEFAULT NULL,
    `image_url_snapshot` VARCHAR(1000) DEFAULT NULL,
    `product_fulltype_snapshot` VARCHAR(300) DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `latest_run_id` BIGINT DEFAULT NULL,
    `latest_run_status` VARCHAR(32) DEFAULT NULL,
    `latest_run_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_natural_slot` VARCHAR(512)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(
                    `owner_user_id`, '|', `store_code`, '|', `site_code`, '|',
                    `partner_sku`, '|', `self_noon_product_code`
                )
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_comp_watch_active` (`active_natural_slot`),
    KEY `idx_ops_comp_watch_scope` (`owner_user_id`, `store_code`, `site_code`, `status`),
    KEY `idx_ops_comp_watch_partner` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`),
    KEY `idx_ops_comp_watch_noon_code` (`owner_user_id`, `self_noon_product_code`),
    KEY `idx_ops_comp_watch_offer` (`product_site_offer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_competitor_keyword` (
    `id` BIGINT NOT NULL,
    `watch_product_id` BIGINT NOT NULL,
    `keyword` VARCHAR(255) NOT NULL,
    `keyword_norm` VARCHAR(255) NOT NULL,
    `locale` VARCHAR(32) DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `display_order` INT NOT NULL DEFAULT 0,
    `last_provider_status` VARCHAR(32) DEFAULT NULL,
    `last_succeeded_at` DATETIME DEFAULT NULL,
    `last_error_code` VARCHAR(128) DEFAULT NULL,
    `last_error_message` VARCHAR(1024) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_keyword_slot` VARCHAR(320)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`watch_product_id`, '|', `keyword_norm`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_comp_keyword_active` (`active_keyword_slot`),
    KEY `idx_ops_comp_keyword_watch` (`watch_product_id`, `status`, `display_order`),
    KEY `idx_ops_comp_keyword_norm` (`keyword_norm`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_competitor_product` (
    `id` BIGINT NOT NULL,
    `watch_product_id` BIGINT NOT NULL,
    `noon_product_code` VARCHAR(80) NOT NULL,
    `code_type` VARCHAR(32) NOT NULL,
    `canonical_url` VARCHAR(1000) DEFAULT NULL,
    `title_snapshot` VARCHAR(500) DEFAULT NULL,
    `brand_snapshot` VARCHAR(200) DEFAULT NULL,
    `image_url_snapshot` VARCHAR(1000) DEFAULT NULL,
    `price_amount_snapshot` DECIMAL(14,2) DEFAULT NULL,
    `currency_code_snapshot` VARCHAR(16) DEFAULT NULL,
    `rating_snapshot` DECIMAL(4,2) DEFAULT NULL,
    `review_count_snapshot` INT DEFAULT NULL,
    `source_type` VARCHAR(32) NOT NULL DEFAULT 'SEARCH_DISCOVERY',
    `review_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `confirmed_by` BIGINT DEFAULT NULL,
    `confirmed_at` DATETIME DEFAULT NULL,
    `first_seen_at` DATETIME DEFAULT NULL,
    `last_seen_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_product_slot` VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`watch_product_id`, '|', `noon_product_code`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_comp_product_active` (`active_product_slot`),
    KEY `idx_ops_comp_product_watch_status` (`watch_product_id`, `review_status`),
    KEY `idx_ops_comp_product_code` (`noon_product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_competitor_keyword_product` (
    `id` BIGINT NOT NULL,
    `keyword_id` BIGINT NOT NULL,
    `competitor_product_id` BIGINT NOT NULL,
    `relation_status` VARCHAR(32) NOT NULL DEFAULT 'DISCOVERED',
    `first_seen_run_id` BIGINT DEFAULT NULL,
    `last_seen_run_id` BIGINT DEFAULT NULL,
    `first_seen_rank_no` INT DEFAULT NULL,
    `last_seen_rank_no` INT DEFAULT NULL,
    `last_seen_sponsored` BIT(1) DEFAULT NULL,
    `last_seen_at` DATETIME DEFAULT NULL,
    `ignored_by` BIGINT DEFAULT NULL,
    `ignored_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_relation_slot` VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`keyword_id`, '|', `competitor_product_id`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_comp_keyword_product_active` (`active_relation_slot`),
    KEY `idx_ops_comp_keyword_product_keyword` (`keyword_id`, `relation_status`),
    KEY `idx_ops_comp_keyword_product_product` (`competitor_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_competitor_search_run` (
    `id` BIGINT NOT NULL,
    `watch_product_id` BIGINT NOT NULL,
    `task_id` BIGINT DEFAULT NULL,
    `trigger_mode` VARCHAR(32) NOT NULL DEFAULT 'MANUAL_REFRESH',
    `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    `requested_by` BIGINT DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `keyword_total` INT NOT NULL DEFAULT 0,
    `keyword_success` INT NOT NULL DEFAULT 0,
    `keyword_failed` INT NOT NULL DEFAULT 0,
    `candidate_upserted_count` INT NOT NULL DEFAULT 0,
    `rank_fact_written_count` INT NOT NULL DEFAULT 0,
    `error_code` VARCHAR(128) DEFAULT NULL,
    `error_message` VARCHAR(1024) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ops_comp_search_run_watch` (`watch_product_id`, `id`),
    KEY `idx_ops_comp_search_run_task` (`task_id`),
    KEY `idx_ops_comp_search_run_status` (`status`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_competitor_keyword_run` (
    `id` BIGINT NOT NULL,
    `search_run_id` BIGINT NOT NULL,
    `keyword_id` BIGINT NOT NULL,
    `keyword_snapshot` VARCHAR(255) NOT NULL,
    `locale_snapshot` VARCHAR(32) DEFAULT NULL,
    `provider_status` VARCHAR(32) NOT NULL DEFAULT 'FAILED',
    `result_count` INT NOT NULL DEFAULT 0,
    `source_url` VARCHAR(1000) DEFAULT NULL,
    `parser_version` VARCHAR(80) DEFAULT NULL,
    `provider_http_status` INT DEFAULT NULL,
    `response_hash` VARCHAR(128) DEFAULT NULL,
    `captured_at` DATETIME DEFAULT NULL,
    `error_code` VARCHAR(128) DEFAULT NULL,
    `error_message` VARCHAR(1024) DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ops_comp_keyword_run_search` (`search_run_id`, `keyword_id`),
    KEY `idx_ops_comp_keyword_run_keyword` (`keyword_id`, `id`),
    KEY `idx_ops_comp_keyword_run_provider` (`provider_status`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_competitor_search_result` (
    `id` BIGINT NOT NULL,
    `keyword_run_id` BIGINT NOT NULL,
    `result_position` INT NOT NULL,
    `noon_product_code` VARCHAR(80) NOT NULL,
    `code_type` VARCHAR(32) NOT NULL,
    `canonical_url` VARCHAR(1000) DEFAULT NULL,
    `title_snapshot` VARCHAR(500) DEFAULT NULL,
    `brand_snapshot` VARCHAR(200) DEFAULT NULL,
    `image_url_snapshot` VARCHAR(1000) DEFAULT NULL,
    `price_amount` DECIMAL(14,2) DEFAULT NULL,
    `currency_code` VARCHAR(16) DEFAULT NULL,
    `rating` DECIMAL(4,2) DEFAULT NULL,
    `review_count` INT DEFAULT NULL,
    `is_sponsored` BIT(1) NOT NULL DEFAULT b'0',
    `raw_result_json` LONGTEXT DEFAULT NULL,
    `captured_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_comp_search_result_position` (`keyword_run_id`, `result_position`),
    KEY `idx_ops_comp_search_result_code` (`keyword_run_id`, `noon_product_code`),
    KEY `idx_ops_comp_search_result_product` (`noon_product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Allowed rank_status values: RANKED, NOT_IN_TOP_20, NOT_IN_SCAN_DEPTH.
CREATE TABLE IF NOT EXISTS `operations_competitor_rank_fact` (
    `id` BIGINT NOT NULL,
    `watch_product_id` BIGINT NOT NULL,
    `keyword_id` BIGINT NOT NULL,
    `keyword_run_id` BIGINT NOT NULL,
    `search_run_id` BIGINT NOT NULL,
    `fact_time` DATETIME NOT NULL,
    `fact_date` DATE NOT NULL,
    `tracked_product_type` VARCHAR(32) NOT NULL,
    `noon_product_code` VARCHAR(80) NOT NULL,
    `rank_status` VARCHAR(32) NOT NULL,
    `rank_no` INT DEFAULT NULL,
    `is_sponsored` BIT(1) NOT NULL DEFAULT b'0',
    `price_amount` DECIMAL(14,2) DEFAULT NULL,
    `currency_code` VARCHAR(16) DEFAULT NULL,
    `rating` DECIMAL(4,2) DEFAULT NULL,
    `review_count` INT DEFAULT NULL,
    `source_result_id` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_comp_rank_fact_run_product` (`keyword_run_id`, `tracked_product_type`, `noon_product_code`),
    KEY `idx_ops_comp_rank_fact_history` (`watch_product_id`, `keyword_id`, `noon_product_code`, `fact_time`),
    KEY `idx_ops_comp_rank_fact_date` (`watch_product_id`, `fact_date`),
    KEY `idx_ops_comp_rank_fact_search` (`search_run_id`, `keyword_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `operations_competitor_analysis_id_sequence` (
    `sequence_name` VARCHAR(100) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_watch_product', GREATEST(COALESCE(MAX(`id`) + 1, 180000), 180000), NOW(), NOW()
FROM `operations_competitor_watch_product`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_keyword', GREATEST(COALESCE(MAX(`id`) + 1, 190000), 190000), NOW(), NOW()
FROM `operations_competitor_keyword`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_product', GREATEST(COALESCE(MAX(`id`) + 1, 200000), 200000), NOW(), NOW()
FROM `operations_competitor_product`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_keyword_product', GREATEST(COALESCE(MAX(`id`) + 1, 210000), 210000), NOW(), NOW()
FROM `operations_competitor_keyword_product`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_search_run', GREATEST(COALESCE(MAX(`id`) + 1, 220000), 220000), NOW(), NOW()
FROM `operations_competitor_search_run`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_keyword_run', GREATEST(COALESCE(MAX(`id`) + 1, 230000), 230000), NOW(), NOW()
FROM `operations_competitor_keyword_run`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_search_result', GREATEST(COALESCE(MAX(`id`) + 1, 240000), 240000), NOW(), NOW()
FROM `operations_competitor_search_result`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `operations_competitor_analysis_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'operations_competitor_rank_fact', GREATEST(COALESCE(MAX(`id`) + 1, 250000), 250000), NOW(), NOW()
FROM `operations_competitor_rank_fact`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
