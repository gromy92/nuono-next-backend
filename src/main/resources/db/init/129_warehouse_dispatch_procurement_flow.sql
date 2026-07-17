-- Warehouse dispatch flow for procurement purchase-order goods.
-- Scope: fulfillment confirmation, dispatchable balance, dispatch plan, and logistics handoff contract.
-- Dispatch plan statuses: DRAFT, READY_FOR_LOGISTICS, HANDOFF_FAILED, LOGISTICS_REQUESTED, CANCELLED.

SET @add_po_item_fulfillment_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND COLUMN_NAME = 'fulfillment_type'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD COLUMN `fulfillment_type` VARCHAR(40) NOT NULL DEFAULT ''WAREHOUSE_RECEIPT'' AFTER `source_type`'
    )
);
PREPARE stmt FROM @add_po_item_fulfillment_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_po_item_fulfillment_source_name := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND COLUMN_NAME = 'fulfillment_source_name'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD COLUMN `fulfillment_source_name` VARCHAR(200) DEFAULT NULL AFTER `fulfillment_type`'
    )
);
PREPARE stmt FROM @add_po_item_fulfillment_source_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_po_item_fulfillment_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_item'
              AND INDEX_NAME = 'idx_procurement_purchase_order_item_fulfillment'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_item` ADD KEY `idx_procurement_purchase_order_item_fulfillment` (`purchase_order_id`, `fulfillment_type`, `is_deleted`)'
    )
);
PREPARE stmt FROM @add_po_item_fulfillment_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `procurement_fulfillment_confirmation` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `client_request_id` VARCHAR(100) DEFAULT NULL,
    `request_fingerprint` CHAR(64) DEFAULT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `confirmation_no` VARCHAR(80) NOT NULL,
    `confirmation_type` VARCHAR(40) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'CONFIRMED',
    `source_party_name` VARCHAR(200) DEFAULT NULL,
    `related_confirmation_id` BIGINT DEFAULT NULL,
    `relation_type` VARCHAR(40) DEFAULT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `confirmed_at` DATETIME NOT NULL,
    `expected_quantity` INT NOT NULL DEFAULT 0,
    `confirmed_quantity_delta` INT NOT NULL DEFAULT 0,
    `abnormal_quantity_delta` INT NOT NULL DEFAULT 0,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fulfillment_confirmation_no` (`confirmation_no`),
    UNIQUE KEY `uk_fulfillment_confirmation_owner_client_request` (`owner_user_id`, `client_request_id`),
    KEY `idx_fulfillment_confirmation_order` (`purchase_order_id`, `confirmation_type`, `status`, `gmt_updated`),
    KEY `idx_fulfillment_confirmation_related` (`related_confirmation_id`, `relation_type`),
    KEY `idx_fulfillment_confirmation_owner` (`owner_user_id`, `confirmation_type`, `status`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_fulfillment_confirmation_line` (
    `id` BIGINT NOT NULL,
    `confirmation_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `purchase_order_item_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `partner_sku` VARCHAR(100) NOT NULL,
    `sku_parent` VARCHAR(100) DEFAULT NULL,
    `title_cache` VARCHAR(500) DEFAULT NULL,
    `image_url_cache` VARCHAR(1000) DEFAULT NULL,
    `fulfillment_type` VARCHAR(40) NOT NULL,
    `expected_quantity` INT NOT NULL DEFAULT 0,
    `confirmed_quantity_delta` INT NOT NULL DEFAULT 0,
    `abnormal_quantity_delta` INT NOT NULL DEFAULT 0,
    `related_confirmation_line_id` BIGINT DEFAULT NULL,
    `exception_reason` VARCHAR(500) DEFAULT NULL,
    `snapshot_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_fulfillment_confirmation_line_header` (`confirmation_id`, `is_deleted`),
    KEY `idx_fulfillment_confirmation_line_order` (`purchase_order_id`, `purchase_order_item_id`, `is_deleted`),
    KEY `idx_fulfillment_confirmation_line_related` (`related_confirmation_line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_fulfillment_balance` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `source_store_code` VARCHAR(100) NOT NULL,
    `source_store_name` VARCHAR(200) DEFAULT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `purchase_order_no` VARCHAR(60) NOT NULL,
    `purchase_order_title` VARCHAR(200) NOT NULL,
    `purchase_order_item_id` BIGINT NOT NULL,
    `purchase_order_item_site_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `partner_sku` VARCHAR(100) NOT NULL,
    `sku_parent` VARCHAR(100) DEFAULT NULL,
    `title_cache` VARCHAR(500) DEFAULT NULL,
    `image_url_cache` VARCHAR(1000) DEFAULT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `planned_transport_mode` VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
    `fulfillment_type` VARCHAR(40) NOT NULL DEFAULT 'WAREHOUSE_RECEIPT',
    `planned_quantity` INT NOT NULL DEFAULT 0,
    `confirmed_quantity` INT NOT NULL DEFAULT 0,
    `abnormal_quantity` INT NOT NULL DEFAULT 0,
    `reserved_quantity` INT NOT NULL DEFAULT 0,
    `logistics_handoff_quantity` INT NOT NULL DEFAULT 0,
    `available_quantity` INT NOT NULL DEFAULT 0,
    `is_new_product` BIT(1) NOT NULL DEFAULT b'0',
    `spec_status` VARCHAR(40) NOT NULL DEFAULT 'READY',
    `status` VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fulfillment_balance_item_site` (`purchase_order_item_site_id`),
    KEY `idx_fulfillment_balance_ready` (`owner_user_id`, `site_code`, `fulfillment_type`, `available_quantity`, `is_deleted`),
    KEY `idx_fulfillment_balance_order` (`purchase_order_id`, `purchase_order_item_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_dispatch_plan` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `client_request_id` VARCHAR(100) DEFAULT NULL,
    `request_fingerprint` VARCHAR(64) DEFAULT NULL,
    `plan_no` VARCHAR(80) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    `item_count` INT NOT NULL DEFAULT 0,
    `sku_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `site_summary_json` TEXT DEFAULT NULL,
    `transport_summary_json` TEXT DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `handoff_generation_no` INT NOT NULL DEFAULT 0,
    `handoff_request_no` VARCHAR(80) DEFAULT NULL,
    `ready_for_logistics_at` DATETIME DEFAULT NULL,
    `logistics_requested_at` DATETIME DEFAULT NULL,
    `handoff_confirmed_at` DATETIME DEFAULT NULL,
    `handoff_failed_at` DATETIME DEFAULT NULL,
    `handoff_error_message` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_dispatch_plan_no` (`plan_no`),
    UNIQUE KEY `uk_procurement_dispatch_handoff_request` (`handoff_request_no`),
    UNIQUE KEY `uk_dispatch_plan_owner_client_request` (`owner_user_id`, `client_request_id`),
    KEY `idx_dispatch_plan_owner` (`owner_user_id`, `status`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_dispatch_plan_line` (
    `id` BIGINT NOT NULL,
    `dispatch_plan_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `partner_sku` VARCHAR(100) NOT NULL,
    `sku_parent` VARCHAR(100) DEFAULT NULL,
    `title_cache` VARCHAR(500) DEFAULT NULL,
    `image_url_cache` VARCHAR(1000) DEFAULT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `actual_transport_mode` VARCHAR(20) NOT NULL,
    `fulfillment_type` VARCHAR(40) NOT NULL,
    `spec_status` VARCHAR(40) NOT NULL DEFAULT 'READY',
    `quantity` INT NOT NULL DEFAULT 0,
    `source_count` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_dispatch_plan_line_plan` (`dispatch_plan_id`, `is_deleted`),
    KEY `idx_dispatch_plan_line_group` (`owner_user_id`, `site_code`, `actual_transport_mode`, `fulfillment_type`, `spec_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_dispatch_plan_line_source` (
    `id` BIGINT NOT NULL,
    `dispatch_plan_id` BIGINT NOT NULL,
    `dispatch_plan_line_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `fulfillment_balance_id` BIGINT NOT NULL,
    `source_store_code` VARCHAR(100) NOT NULL,
    `source_store_name` VARCHAR(200) DEFAULT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `purchase_order_no` VARCHAR(60) NOT NULL,
    `purchase_order_item_id` BIGINT NOT NULL,
    `purchase_order_item_site_id` BIGINT NOT NULL,
    `planned_transport_mode` VARCHAR(20) NOT NULL,
    `fulfillment_type` VARCHAR(40) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_dispatch_source_plan` (`dispatch_plan_id`, `is_deleted`),
    KEY `idx_dispatch_source_line` (`dispatch_plan_line_id`, `is_deleted`),
    KEY `idx_dispatch_source_balance` (`fulfillment_balance_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_dispatch_plan_operation_log` (
    `id` BIGINT NOT NULL,
    `dispatch_plan_id` BIGINT DEFAULT NULL,
    `operation_type` VARCHAR(60) NOT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `before_status` VARCHAR(40) DEFAULT NULL,
    `after_status` VARCHAR(40) DEFAULT NULL,
    `detail_json` LONGTEXT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_dispatch_plan_log_plan` (`dispatch_plan_id`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `procurement_fulfillment_balance` (
    `id`, `owner_user_id`, `logical_store_id`, `source_store_code`, `source_store_name`,
    `purchase_order_id`, `purchase_order_no`, `purchase_order_title`, `purchase_order_item_id`,
    `purchase_order_item_site_id`, `product_master_id`, `product_variant_id`, `partner_sku`,
    `sku_parent`, `title_cache`, `image_url_cache`, `site_code`, `planned_transport_mode`,
    `fulfillment_type`, `planned_quantity`, `confirmed_quantity`, `abnormal_quantity`,
    `reserved_quantity`, `logistics_handoff_quantity`, `available_quantity`, `spec_status`,
    `status`, `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT site.id + 900000000,
       site.owner_user_id,
       site.logical_store_id,
       po.anchor_store_code_cache,
       po.project_name_cache,
       site.purchase_order_id,
       po.order_no,
       po.title,
       site.purchase_order_item_id,
       site.id,
       item.product_master_id,
       item.product_variant_id,
       item.partner_sku,
       item.sku_parent,
       item.title_cache,
       item.image_url_cache,
       site.site_code,
       site.transport_mode,
       COALESCE(item.fulfillment_type, 'WAREHOUSE_RECEIPT'),
       site.quantity,
       0,
       0,
       0,
       0,
       0,
       'READY',
       'OPEN',
       b'0',
       site.created_by,
       site.updated_by,
       NOW(),
       NOW()
FROM procurement_purchase_order_item_site site
JOIN procurement_purchase_order_item item
  ON item.id = site.purchase_order_item_id
 AND item.is_deleted = b'0'
JOIN procurement_purchase_order po
  ON po.id = site.purchase_order_id
 AND po.is_deleted = b'0'
WHERE site.is_deleted = b'0'
ON DUPLICATE KEY UPDATE
    `planned_quantity` = VALUES(`planned_quantity`),
    `source_store_code` = VALUES(`source_store_code`),
    `source_store_name` = VALUES(`source_store_name`),
    `purchase_order_no` = VALUES(`purchase_order_no`),
    `purchase_order_title` = VALUES(`purchase_order_title`),
    `partner_sku` = VALUES(`partner_sku`),
    `sku_parent` = VALUES(`sku_parent`),
    `title_cache` = VALUES(`title_cache`),
    `image_url_cache` = VALUES(`image_url_cache`),
    `planned_transport_mode` = VALUES(`planned_transport_mode`),
    `fulfillment_type` = VALUES(`fulfillment_type`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_fulfillment_balance', GREATEST(COALESCE(MAX(`id`) + 1, 310000), 310000), NOW(), NOW()
FROM `procurement_fulfillment_balance`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_fulfillment_confirmation', GREATEST(COALESCE(MAX(`id`) + 1, 320000), 320000), NOW(), NOW()
FROM `procurement_fulfillment_confirmation`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_fulfillment_confirmation_line', GREATEST(COALESCE(MAX(`id`) + 1, 330000), 330000), NOW(), NOW()
FROM `procurement_fulfillment_confirmation_line`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_dispatch_plan', GREATEST(COALESCE(MAX(`id`) + 1, 340000), 340000), NOW(), NOW()
FROM `procurement_dispatch_plan`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_dispatch_plan_line', GREATEST(COALESCE(MAX(`id`) + 1, 350000), 350000), NOW(), NOW()
FROM `procurement_dispatch_plan_line`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_dispatch_plan_line_source', GREATEST(COALESCE(MAX(`id`) + 1, 360000), 360000), NOW(), NOW()
FROM `procurement_dispatch_plan_line_source`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_dispatch_plan_operation_log', GREATEST(COALESCE(MAX(`id`) + 1, 390000), 390000), NOW(), NOW()
FROM `procurement_dispatch_plan_operation_log`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
