-- Pre-order profit selection pool first real backend slice.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `pre_order_profit_candidate` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `title` VARCHAR(500) DEFAULT NULL,
    `sku_hint` VARCHAR(160) DEFAULT NULL,
    `purchase_url` VARCHAR(1000) DEFAULT NULL,
    `purchase_price_rmb` DECIMAL(18,4) DEFAULT NULL,
    `length_cm` DECIMAL(18,4) DEFAULT NULL,
    `width_cm` DECIMAL(18,4) DEFAULT NULL,
    `height_cm` DECIMAL(18,4) DEFAULT NULL,
    `actual_weight_kg` DECIMAL(18,4) DEFAULT NULL,
    `category_id` VARCHAR(120) DEFAULT NULL,
    `category_label` VARCHAR(200) DEFAULT NULL,
    `logistics_carrier_id` VARCHAR(120) DEFAULT NULL,
    `logistics_carrier_label` VARCHAR(200) DEFAULT NULL,
    `sale_price` DECIMAL(18,4) DEFAULT NULL,
    `target_margin_rate` DECIMAL(10,4) DEFAULT NULL,
    `candidate_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `notes` VARCHAR(1000) DEFAULT NULL,
    `latest_calculation_status` VARCHAR(32) DEFAULT NULL,
    `latest_calculation_json` LONGTEXT DEFAULT NULL,
    `ai_summary` TEXT DEFAULT NULL,
    `ai_result_snapshot_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_pre_order_profit_candidate_scope` (`owner_user_id`, `store_code`, `site_code`, `is_deleted`, `gmt_updated`),
    KEY `idx_pre_order_profit_candidate_status` (`owner_user_id`, `store_code`, `latest_calculation_status`, `is_deleted`),
    KEY `idx_pre_order_profit_candidate_rule` (`owner_user_id`, `store_code`, `category_id`, `logistics_carrier_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `pre_order_profit_competitor` (
    `id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `title` VARCHAR(500) NOT NULL,
    `url` VARCHAR(1000) DEFAULT NULL,
    `platform` VARCHAR(80) DEFAULT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `price` DECIMAL(18,4) DEFAULT NULL,
    `currency` VARCHAR(16) DEFAULT NULL,
    `seller_name` VARCHAR(200) DEFAULT NULL,
    `notes` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_pre_order_profit_competitor_candidate` (`candidate_id`, `is_deleted`, `gmt_updated`),
    KEY `idx_pre_order_profit_competitor_scope` (`owner_user_id`, `site_code`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `pre_order_profit_purchase_order` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `name` VARCHAR(200) NOT NULL,
    `notes` VARCHAR(1000) DEFAULT NULL,
    `source_type` VARCHAR(32) NOT NULL DEFAULT 'SELECTION_POOL',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_pre_order_profit_po_scope` (`owner_user_id`, `store_code`, `site_code`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `pre_order_profit_purchase_order_item` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_item_slot` VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`purchase_order_id`, '|', `candidate_id`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pre_order_profit_po_item_active` (`active_item_slot`),
    KEY `idx_pre_order_profit_po_item_order` (`purchase_order_id`, `is_deleted`),
    KEY `idx_pre_order_profit_po_item_candidate` (`candidate_id`, `is_deleted`),
    KEY `idx_pre_order_profit_po_item_scope` (`owner_user_id`, `store_code`, `site_code`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'pre_order_profit_candidate', GREATEST(COALESCE(MAX(`id`) + 1, 260000), 260000), NOW(), NOW()
FROM `pre_order_profit_candidate`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'pre_order_profit_competitor', GREATEST(COALESCE(MAX(`id`) + 1, 270000), 270000), NOW(), NOW()
FROM `pre_order_profit_competitor`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'pre_order_profit_purchase_order', GREATEST(COALESCE(MAX(`id`) + 1, 280000), 280000), NOW(), NOW()
FROM `pre_order_profit_purchase_order`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'pre_order_profit_purchase_order_item', GREATEST(COALESCE(MAX(`id`) + 1, 290000), 290000), NOW(), NOW()
FROM `pre_order_profit_purchase_order_item`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
