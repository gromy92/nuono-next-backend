-- Product management official detail alignment.
-- This migration adds structured projections for Noon-style product detail tabs
-- while keeping the current last-write-wins draft/publish policy.

SET @product_master_add_sku_group := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND COLUMN_NAME = 'sku_group'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD COLUMN `sku_group` VARCHAR(200) DEFAULT NULL AFTER `cover_image_url`'
    )
);
PREPARE stmt FROM @product_master_add_sku_group;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_master_add_group_name_cache := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND COLUMN_NAME = 'group_name_cache'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD COLUMN `group_name_cache` VARCHAR(200) DEFAULT NULL AFTER `sku_group`'
    )
);
PREPARE stmt FROM @product_master_add_group_name_cache;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_master_add_issue_count := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND COLUMN_NAME = 'issue_count'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD COLUMN `issue_count` INT NOT NULL DEFAULT 0 AFTER `group_member_count`'
    )
);
PREPARE stmt FROM @product_master_add_issue_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_master_add_issue_summary_json := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND COLUMN_NAME = 'issue_summary_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD COLUMN `issue_summary_json` LONGTEXT DEFAULT NULL AFTER `issue_count`'
    )
);
PREPARE stmt FROM @product_master_add_issue_summary_json;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_master_add_sku_group_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND INDEX_NAME = 'idx_product_master_sku_group'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD KEY `idx_product_master_sku_group` (`logical_store_id`, `sku_group`)'
    )
);
PREPARE stmt FROM @product_master_add_sku_group_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_master_add_brand_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND INDEX_NAME = 'idx_product_master_brand'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD KEY `idx_product_master_brand` (`logical_store_id`, `brand_cache`)'
    )
);
PREPARE stmt FROM @product_master_add_brand_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_master_add_fulltype_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND INDEX_NAME = 'idx_product_master_fulltype'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD KEY `idx_product_master_fulltype` (`logical_store_id`, `product_fulltype_cache`)'
    )
);
PREPARE stmt FROM @product_master_add_fulltype_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `product_group` (
    `id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `sku_group` VARCHAR(200) NOT NULL,
    `group_ref` VARCHAR(200) DEFAULT NULL,
    `group_ref_canonical` VARCHAR(200) DEFAULT NULL,
    `group_name` VARCHAR(200) DEFAULT NULL,
    `brand` VARCHAR(200) DEFAULT NULL,
    `product_fulltype` VARCHAR(255) DEFAULT NULL,
    `axes_json` LONGTEXT DEFAULT NULL,
    `conditions_json` LONGTEXT DEFAULT NULL,
    `member_count` INT DEFAULT NULL,
    `source_snapshot_id` BIGINT DEFAULT NULL,
    `sync_status` VARCHAR(40) NOT NULL DEFAULT 'synced',
    `last_synced_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_group_store_sku_group` (`logical_store_id`, `sku_group`),
    KEY `idx_product_group_ref` (`logical_store_id`, `group_ref`),
    KEY `idx_product_group_brand_fulltype` (`logical_store_id`, `brand`, `product_fulltype`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_group_member` (
    `id` BIGINT NOT NULL,
    `product_group_id` BIGINT NOT NULL,
    `product_master_id` BIGINT DEFAULT NULL,
    `sku_parent` VARCHAR(100) NOT NULL,
    `member_sku` VARCHAR(120) DEFAULT NULL,
    `child_sku` VARCHAR(100) DEFAULT NULL,
    `partner_sku` VARCHAR(100) DEFAULT NULL,
    `axis_values_json` LONGTEXT DEFAULT NULL,
    `sort_ix` INT DEFAULT NULL,
    `member_status` VARCHAR(40) NOT NULL DEFAULT 'active',
    `source_snapshot_id` BIGINT DEFAULT NULL,
    `last_synced_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_group_member_sku` (`product_group_id`, `sku_parent`),
    KEY `idx_product_group_member_master` (`product_master_id`),
    KEY `idx_product_group_member_partner_sku` (`partner_sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_image_asset` (
    `id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `url` VARCHAR(1000) NOT NULL,
    `storage_key` VARCHAR(500) DEFAULT NULL,
    `original_filename` VARCHAR(255) DEFAULT NULL,
    `content_type` VARCHAR(120) DEFAULT NULL,
    `size_bytes` BIGINT DEFAULT NULL,
    `width_px` INT DEFAULT NULL,
    `height_px` INT DEFAULT NULL,
    `sha256` VARCHAR(128) DEFAULT NULL,
    `asset_status` VARCHAR(40) NOT NULL DEFAULT 'draft',
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_image_asset_master` (`product_master_id`, `asset_status`),
    KEY `idx_product_image_asset_sha256` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_issue` (
    `id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `site_id` BIGINT DEFAULT NULL,
    `variant_id` BIGINT DEFAULT NULL,
    `issue_scope_key` VARCHAR(200) NOT NULL DEFAULT 'product',
    `issue_source` VARCHAR(60) NOT NULL,
    `issue_code` VARCHAR(120) DEFAULT NULL,
    `issue_hash` VARCHAR(128) NOT NULL,
    `severity` VARCHAR(40) NOT NULL DEFAULT 'warning',
    `title` VARCHAR(300) DEFAULT NULL,
    `message` VARCHAR(1000) DEFAULT NULL,
    `raw_json` LONGTEXT DEFAULT NULL,
    `issue_status` VARCHAR(40) NOT NULL DEFAULT 'open',
    `first_seen_at` DATETIME NOT NULL,
    `last_seen_at` DATETIME NOT NULL,
    `resolved_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_issue_hash` (`product_master_id`, `issue_scope_key`, `issue_hash`),
    KEY `idx_product_issue_master_status` (`product_master_id`, `issue_status`),
    KEY `idx_product_issue_site_status` (`site_id`, `issue_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @product_site_offer_add_currency := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'currency'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `currency` VARCHAR(10) DEFAULT NULL AFTER `offer_code`'
    )
);
PREPARE stmt FROM @product_site_offer_add_currency;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_final_price := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'final_price'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `final_price` DECIMAL(12,2) DEFAULT NULL AFTER `price_max`'
    )
);
PREPARE stmt FROM @product_site_offer_add_final_price;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_final_price_source := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'final_price_source'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `final_price_source` VARCHAR(40) DEFAULT NULL AFTER `final_price`'
    )
);
PREPARE stmt FROM @product_site_offer_add_final_price_source;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_active_promotion_code := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'active_promotion_code'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `active_promotion_code` VARCHAR(120) DEFAULT NULL AFTER `final_price_source`'
    )
);
PREPARE stmt FROM @product_site_offer_add_active_promotion_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_active_promotion_name := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'active_promotion_name'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `active_promotion_name` VARCHAR(200) DEFAULT NULL AFTER `active_promotion_code`'
    )
);
PREPARE stmt FROM @product_site_offer_add_active_promotion_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_active_promotion_url := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'active_promotion_url'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `active_promotion_url` VARCHAR(1000) DEFAULT NULL AFTER `active_promotion_name`'
    )
);
PREPARE stmt FROM @product_site_offer_add_active_promotion_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_promotion_payload_json := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'promotion_payload_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `promotion_payload_json` LONGTEXT DEFAULT NULL AFTER `active_promotion_url`'
    )
);
PREPARE stmt FROM @product_site_offer_add_promotion_payload_json;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_price_synced_at := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'price_synced_at'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `price_synced_at` DATETIME DEFAULT NULL AFTER `promotion_payload_json`'
    )
);
PREPARE stmt FROM @product_site_offer_add_price_synced_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_final_price_source_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND INDEX_NAME = 'idx_product_site_offer_final_price_source'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_final_price_source` (`final_price_source`)'
    )
);
PREPARE stmt FROM @product_site_offer_add_final_price_source_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_promotion_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND INDEX_NAME = 'idx_product_site_offer_promotion'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_promotion` (`active_promotion_code`)'
    )
);
PREPARE stmt FROM @product_site_offer_add_promotion_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_overwrite_policy := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND COLUMN_NAME = 'overwrite_policy'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD COLUMN `overwrite_policy` VARCHAR(40) DEFAULT ''last_write_wins'' AFTER `action_type`'
    )
);
PREPARE stmt FROM @product_action_log_add_overwrite_policy;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_idempotency_key := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND COLUMN_NAME = 'idempotency_key'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD COLUMN `idempotency_key` VARCHAR(160) DEFAULT NULL AFTER `overwrite_policy`'
    )
);
PREPARE stmt FROM @product_action_log_add_idempotency_key;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_blocked_fields_json := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND COLUMN_NAME = 'blocked_fields_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD COLUMN `blocked_fields_json` LONGTEXT DEFAULT NULL AFTER `summary_json`'
    )
);
PREPARE stmt FROM @product_action_log_add_blocked_fields_json;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_request_json := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND COLUMN_NAME = 'request_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD COLUMN `request_json` LONGTEXT DEFAULT NULL AFTER `blocked_fields_json`'
    )
);
PREPARE stmt FROM @product_action_log_add_request_json;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_response_json := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND COLUMN_NAME = 'response_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD COLUMN `response_json` LONGTEXT DEFAULT NULL AFTER `request_json`'
    )
);
PREPARE stmt FROM @product_action_log_add_response_json;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_started_at := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND COLUMN_NAME = 'started_at'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD COLUMN `started_at` DATETIME DEFAULT NULL AFTER `response_json`'
    )
);
PREPARE stmt FROM @product_action_log_add_started_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_finished_at := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND COLUMN_NAME = 'finished_at'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD COLUMN `finished_at` DATETIME DEFAULT NULL AFTER `started_at`'
    )
);
PREPARE stmt FROM @product_action_log_add_finished_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_idempotency_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND INDEX_NAME = 'uk_product_action_log_idempotency'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD UNIQUE KEY `uk_product_action_log_idempotency` (`idempotency_key`)'
    )
);
PREPARE stmt FROM @product_action_log_add_idempotency_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_action_log_add_action_status_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_action_log'
              AND INDEX_NAME = 'idx_product_action_log_action_status'
        ),
        'SELECT 1',
        'ALTER TABLE `product_action_log` ADD KEY `idx_product_action_log_action_status` (`action_type`, `result_status`)'
    )
);
PREPARE stmt FROM @product_action_log_add_action_status_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_action_log`
SET `overwrite_policy` = 'last_write_wins'
WHERE `overwrite_policy` IS NULL
   OR `overwrite_policy` = '';

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('product_group', 60000, NOW(), NOW()),
    ('product_group_member', 61000, NOW(), NOW()),
    ('product_image_asset', 62000, NOW(), NOW()),
    ('product_issue', 63000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
