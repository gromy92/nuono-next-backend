-- Procurement purchase order V1.
-- Scope: purchase-order container for product-archive PSKU lines and 1688 Top5 collection progress.

CREATE TABLE IF NOT EXISTS `procurement_purchase_order` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `order_no` VARCHAR(60) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    `collection_status` VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    `progress_percent` INT NOT NULL DEFAULT 0,
    `site_codes_json` TEXT NOT NULL,
    `project_code_cache` VARCHAR(100) DEFAULT NULL,
    `project_name_cache` VARCHAR(200) DEFAULT NULL,
    `anchor_store_code_cache` VARCHAR(100) NOT NULL,
    `item_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `collecting_item_count` INT NOT NULL DEFAULT 0,
    `abnormal_item_count` INT NOT NULL DEFAULT 0,
    `submitted_collect_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_purchase_order_no` (`order_no`),
    KEY `idx_procurement_purchase_order_store_status` (`logical_store_id`, `status`, `gmt_updated`),
    KEY `idx_procurement_purchase_order_owner_store` (`owner_user_id`, `logical_store_id`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_purchase_order_item` (
    `id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `sku_parent` VARCHAR(100) DEFAULT NULL,
    `partner_sku` VARCHAR(100) NOT NULL,
    `child_sku` VARCHAR(100) DEFAULT NULL,
    `title_cache` VARCHAR(500) DEFAULT NULL,
    `image_url_cache` VARCHAR(1000) DEFAULT NULL,
    `source_type` VARCHAR(40) NOT NULL DEFAULT 'STORE_ARCHIVE',
    `manual_selection_source_collection_id` BIGINT DEFAULT NULL,
    `sourcing_spec_text` VARCHAR(200) DEFAULT NULL,
    `sourcing_size_text` VARCHAR(200) DEFAULT NULL,
    `sourcing_color_text` VARCHAR(200) DEFAULT NULL,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `site_count` INT NOT NULL DEFAULT 0,
    `collection_status` VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    `progress_percent` INT NOT NULL DEFAULT 0,
    `candidate_count` INT NOT NULL DEFAULT 0,
    `recommended_count` INT NOT NULL DEFAULT 0,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(500) DEFAULT NULL,
    `latest_collection_link_id` BIGINT DEFAULT NULL,
    `last_collected_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_variant_slot` VARCHAR(160)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`purchase_order_id`, '|', `product_variant_id`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_purchase_order_item_active_variant` (`active_variant_slot`),
    KEY `idx_procurement_purchase_order_item_order` (`purchase_order_id`, `is_deleted`, `id`),
    KEY `idx_procurement_purchase_order_item_variant` (`product_variant_id`),
    KEY `idx_procurement_purchase_order_item_collection` (`collection_status`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_procurement_purchase_order_item_sourcing_spec := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND COLUMN_NAME = 'sourcing_spec_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD COLUMN `sourcing_spec_text` VARCHAR(200) DEFAULT NULL AFTER `manual_selection_source_collection_id`'
    )
);
PREPARE stmt FROM @add_procurement_purchase_order_item_sourcing_spec;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_procurement_purchase_order_item_sourcing_size := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND COLUMN_NAME = 'sourcing_size_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD COLUMN `sourcing_size_text` VARCHAR(200) DEFAULT NULL AFTER `sourcing_spec_text`'
    )
);
PREPARE stmt FROM @add_procurement_purchase_order_item_sourcing_size;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_procurement_purchase_order_item_sourcing_color := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND COLUMN_NAME = 'sourcing_color_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD COLUMN `sourcing_color_text` VARCHAR(200) DEFAULT NULL AFTER `sourcing_size_text`'
    )
);
PREPARE stmt FROM @add_procurement_purchase_order_item_sourcing_color;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `procurement_purchase_order_item_site` (
    `id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `purchase_order_item_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `site_id` BIGINT NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `product_site_offer_id` BIGINT NOT NULL,
    `psku_code` VARCHAR(100) DEFAULT NULL,
    `offer_code` VARCHAR(100) DEFAULT NULL,
    `transport_mode` VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
    `quantity` INT NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_site_slot` VARCHAR(180)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`purchase_order_item_id`, '|', `site_code`, '|', `transport_mode`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_purchase_order_item_site_active` (`active_site_slot`),
    KEY `idx_procurement_purchase_order_item_site_item` (`purchase_order_item_id`, `is_deleted`),
    KEY `idx_procurement_purchase_order_item_site_offer` (`product_site_offer_id`),
    KEY `idx_procurement_purchase_order_item_site_order` (`purchase_order_id`, `site_code`, `transport_mode`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_purchase_order_ali1688_collection` (
    `id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `purchase_order_item_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `source_collection_id` BIGINT NOT NULL,
    `ali1688_task_id` BIGINT DEFAULT NULL,
    `current_link_key` VARCHAR(120) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'QUEUED',
    `progress_percent` INT NOT NULL DEFAULT 0,
    `candidate_count` INT NOT NULL DEFAULT 0,
    `recommended_count` INT NOT NULL DEFAULT 0,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(500) DEFAULT NULL,
    `source_snapshot_json` LONGTEXT DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_purchase_order_ali1688_current` (`current_link_key`),
    KEY `idx_procurement_purchase_order_ali1688_order` (`purchase_order_id`, `is_deleted`),
    KEY `idx_procurement_purchase_order_ali1688_item` (`purchase_order_item_id`, `is_deleted`),
    KEY `idx_procurement_purchase_order_ali1688_source` (`source_collection_id`),
    KEY `idx_procurement_purchase_order_ali1688_task` (`ali1688_task_id`),
    KEY `idx_procurement_purchase_order_ali1688_status` (`status`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_purchase_order_operation_log` (
    `id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `purchase_order_item_id` BIGINT DEFAULT NULL,
    `operation_type` VARCHAR(60) NOT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `before_status` VARCHAR(40) DEFAULT NULL,
    `after_status` VARCHAR(40) DEFAULT NULL,
    `detail_json` LONGTEXT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_purchase_order_log_order` (`purchase_order_id`, `gmt_create`),
    KEY `idx_procurement_purchase_order_log_item` (`purchase_order_item_id`),
    KEY `idx_procurement_purchase_order_log_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_purchase_order', GREATEST(COALESCE(MAX(`id`) + 1, 200000), 200000), NOW(), NOW()
FROM `procurement_purchase_order`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_purchase_order_item', GREATEST(COALESCE(MAX(`id`) + 1, 210000), 210000), NOW(), NOW()
FROM `procurement_purchase_order_item`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_purchase_order_item_site', GREATEST(COALESCE(MAX(`id`) + 1, 220000), 220000), NOW(), NOW()
FROM `procurement_purchase_order_item_site`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_purchase_order_ali1688_collection', GREATEST(COALESCE(MAX(`id`) + 1, 230000), 230000), NOW(), NOW()
FROM `procurement_purchase_order_ali1688_collection`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_purchase_order_operation_log', GREATEST(COALESCE(MAX(`id`) + 1, 240000), 240000), NOW(), NOW()
FROM `procurement_purchase_order_operation_log`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
