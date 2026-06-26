-- Warehouse shipping recommendation options.
-- Scope: selected ready goods -> shipping suggestions -> outbound orders -> packing lists.

CREATE TABLE IF NOT EXISTS `warehouse_shipping_batch` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `batch_no` VARCHAR(80) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    `selected_option_id` BIGINT DEFAULT NULL,
    `source_count` INT NOT NULL DEFAULT 0,
    `sku_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `store_summary_json` TEXT DEFAULT NULL,
    `site_summary_json` TEXT DEFAULT NULL,
    `transport_summary_json` TEXT DEFAULT NULL,
    `origin_summary_json` TEXT DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouse_shipping_batch_no` (`batch_no`),
    KEY `idx_warehouse_shipping_batch_owner` (`owner_user_id`, `status`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_shipping_batch_source` (
    `id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `fulfillment_balance_id` BIGINT NOT NULL,
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
    `planned_transport_mode` VARCHAR(20) NOT NULL,
    `fulfillment_type` VARCHAR(40) NOT NULL,
    `source_party_name` VARCHAR(200) DEFAULT NULL,
    `spec_status` VARCHAR(40) NOT NULL DEFAULT 'READY',
    `product_length_cm` DECIMAL(12, 3) DEFAULT NULL,
    `product_width_cm` DECIMAL(12, 3) DEFAULT NULL,
    `product_height_cm` DECIMAL(12, 3) DEFAULT NULL,
    `product_weight_g` DECIMAL(12, 3) DEFAULT NULL,
    `logistics_profile_status` VARCHAR(40) DEFAULT NULL,
    `sensitive_flag` BIT(1) NOT NULL DEFAULT b'0',
    `sensitive_reason_json` TEXT DEFAULT NULL,
    `reserved_quantity` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shipping_batch_source_balance` (`batch_id`, `fulfillment_balance_id`),
    KEY `idx_shipping_batch_source_batch` (`batch_id`, `is_deleted`),
    KEY `idx_shipping_batch_source_balance` (`fulfillment_balance_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_shipping_suggestion_option` (
    `id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `option_type` VARCHAR(40) NOT NULL,
    `option_name` VARCHAR(120) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'CANDIDATE',
    `selected_flag` BIT(1) NOT NULL DEFAULT b'0',
    `score` INT NOT NULL DEFAULT 0,
    `sku_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `air_quantity` INT NOT NULL DEFAULT 0,
    `sea_quantity` INT NOT NULL DEFAULT 0,
    `spec_missing_count` INT NOT NULL DEFAULT 0,
    `warning_count` INT NOT NULL DEFAULT 0,
    `forwarder_plan_type` VARCHAR(40) DEFAULT NULL,
    `auto_recommended` BIT(1) NOT NULL DEFAULT b'0',
    `target_forwarder_codes_json` TEXT DEFAULT NULL,
    `target_forwarder_names_json` TEXT DEFAULT NULL,
    `route_codes_json` TEXT DEFAULT NULL,
    `evaluation_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    `blocked_reasons_json` TEXT DEFAULT NULL,
    `actual_weight_kg` DECIMAL(12, 3) DEFAULT NULL,
    `volume_cbm` DECIMAL(12, 4) DEFAULT NULL,
    `chargeable_weight_kg` DECIMAL(12, 3) DEFAULT NULL,
    `estimated_total_amount` DECIMAL(14, 4) DEFAULT NULL,
    `avg_unit_amount` DECIMAL(14, 4) DEFAULT NULL,
    `currency` VARCHAR(20) DEFAULT NULL,
    `cost_snapshot_json` LONGTEXT DEFAULT NULL,
    `summary_json` TEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_shipping_option_batch` (`batch_id`, `selected_flag`, `is_deleted`),
    KEY `idx_shipping_option_owner` (`owner_user_id`, `status`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_shipping_suggestion_line` (
    `id` BIGINT NOT NULL,
    `option_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
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
    `source_party_name` VARCHAR(200) DEFAULT NULL,
    `spec_status` VARCHAR(40) NOT NULL DEFAULT 'READY',
    `target_forwarder_code` VARCHAR(50) DEFAULT NULL,
    `target_forwarder_name` VARCHAR(120) DEFAULT NULL,
    `route_code` VARCHAR(120) DEFAULT NULL,
    `route_name` VARCHAR(255) DEFAULT NULL,
    `actual_weight_kg` DECIMAL(12, 3) DEFAULT NULL,
    `volume_cbm` DECIMAL(12, 4) DEFAULT NULL,
    `chargeable_weight_kg` DECIMAL(12, 3) DEFAULT NULL,
    `estimated_amount` DECIMAL(14, 4) DEFAULT NULL,
    `currency` VARCHAR(20) DEFAULT NULL,
    `quantity` INT NOT NULL DEFAULT 0,
    `source_count` INT NOT NULL DEFAULT 0,
    `warning_json` TEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_shipping_suggestion_line_option` (`option_id`, `is_deleted`),
    KEY `idx_shipping_suggestion_line_group` (`owner_user_id`, `site_code`, `actual_transport_mode`, `fulfillment_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_shipping_suggestion_line_source` (
    `id` BIGINT NOT NULL,
    `option_id` BIGINT NOT NULL,
    `line_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `batch_source_id` BIGINT NOT NULL,
    `fulfillment_balance_id` BIGINT NOT NULL,
    `planned_transport_mode` VARCHAR(20) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_shipping_suggestion_source_option` (`option_id`, `is_deleted`),
    KEY `idx_shipping_suggestion_source_line` (`line_id`, `is_deleted`),
    KEY `idx_shipping_suggestion_source_batch_source` (`batch_source_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_outbound_order` (
    `id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `option_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `outbound_no` VARCHAR(80) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    `origin_type` VARCHAR(40) NOT NULL,
    `origin_name` VARCHAR(200) DEFAULT NULL,
    `sku_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `site_summary_json` TEXT DEFAULT NULL,
    `transport_summary_json` TEXT DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouse_outbound_no` (`outbound_no`),
    KEY `idx_warehouse_outbound_batch` (`batch_id`, `is_deleted`),
    KEY `idx_warehouse_outbound_owner` (`owner_user_id`, `status`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_outbound_order_line` (
    `id` BIGINT NOT NULL,
    `outbound_order_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `option_line_id` BIGINT NOT NULL,
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
    `source_party_name` VARCHAR(200) DEFAULT NULL,
    `spec_status` VARCHAR(40) NOT NULL DEFAULT 'READY',
    `quantity` INT NOT NULL DEFAULT 0,
    `packed_quantity` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_outbound_line_order` (`outbound_order_id`, `is_deleted`),
    KEY `idx_outbound_line_product` (`owner_user_id`, `partner_sku`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_outbound_order_line_source` (
    `id` BIGINT NOT NULL,
    `outbound_order_id` BIGINT NOT NULL,
    `outbound_order_line_id` BIGINT NOT NULL,
    `batch_source_id` BIGINT NOT NULL,
    `fulfillment_balance_id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT NOT NULL,
    `purchase_order_no` VARCHAR(60) NOT NULL,
    `purchase_order_title` VARCHAR(200) NOT NULL,
    `purchase_order_item_id` BIGINT NOT NULL,
    `purchase_order_item_site_id` BIGINT NOT NULL,
    `planned_transport_mode` VARCHAR(20) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_outbound_source_order` (`outbound_order_id`, `is_deleted`),
    KEY `idx_outbound_source_line` (`outbound_order_line_id`, `is_deleted`),
    KEY `idx_outbound_source_balance` (`fulfillment_balance_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_packing_list` (
    `id` BIGINT NOT NULL,
    `outbound_order_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `packing_no` VARCHAR(80) NOT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    `box_count` INT NOT NULL DEFAULT 0,
    `packed_quantity` INT NOT NULL DEFAULT 0,
    `gross_weight_kg` DECIMAL(12, 3) DEFAULT NULL,
    `volume_cbm` DECIMAL(12, 4) DEFAULT NULL,
    `remark` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouse_packing_no` (`packing_no`),
    KEY `idx_warehouse_packing_order` (`outbound_order_id`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_packing_box` (
    `id` BIGINT NOT NULL,
    `packing_list_id` BIGINT NOT NULL,
    `outbound_order_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `box_no` VARCHAR(80) NOT NULL,
    `length_cm` DECIMAL(12, 3) NOT NULL,
    `width_cm` DECIMAL(12, 3) NOT NULL,
    `height_cm` DECIMAL(12, 3) NOT NULL,
    `gross_weight_kg` DECIMAL(12, 3) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouse_packing_box_no` (`packing_list_id`, `box_no`),
    KEY `idx_warehouse_packing_box_list` (`packing_list_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `warehouse_packing_box_item` (
    `id` BIGINT NOT NULL,
    `packing_list_id` BIGINT NOT NULL,
    `packing_box_id` BIGINT NOT NULL,
    `outbound_order_id` BIGINT NOT NULL,
    `outbound_order_line_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `partner_sku` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `actual_transport_mode` VARCHAR(20) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 0,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_packing_box_item_box` (`packing_box_id`, `is_deleted`),
    KEY `idx_packing_box_item_line` (`outbound_order_line_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_shipping_batch', GREATEST(COALESCE(MAX(`id`) + 1, 700000), 700000), NOW(), NOW()
FROM `warehouse_shipping_batch`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_shipping_batch_source', GREATEST(COALESCE(MAX(`id`) + 1, 710000), 710000), NOW(), NOW()
FROM `warehouse_shipping_batch_source`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_shipping_suggestion_option', GREATEST(COALESCE(MAX(`id`) + 1, 720000), 720000), NOW(), NOW()
FROM `warehouse_shipping_suggestion_option`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_shipping_suggestion_line', GREATEST(COALESCE(MAX(`id`) + 1, 730000), 730000), NOW(), NOW()
FROM `warehouse_shipping_suggestion_line`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_shipping_suggestion_line_source', GREATEST(COALESCE(MAX(`id`) + 1, 740000), 740000), NOW(), NOW()
FROM `warehouse_shipping_suggestion_line_source`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_outbound_order', GREATEST(COALESCE(MAX(`id`) + 1, 800000), 800000), NOW(), NOW()
FROM `warehouse_outbound_order`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_outbound_order_line', GREATEST(COALESCE(MAX(`id`) + 1, 810000), 810000), NOW(), NOW()
FROM `warehouse_outbound_order_line`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_outbound_order_line_source', GREATEST(COALESCE(MAX(`id`) + 1, 820000), 820000), NOW(), NOW()
FROM `warehouse_outbound_order_line_source`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_packing_list', GREATEST(COALESCE(MAX(`id`) + 1, 830000), 830000), NOW(), NOW()
FROM `warehouse_packing_list`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_packing_box', GREATEST(COALESCE(MAX(`id`) + 1, 840000), 840000), NOW(), NOW()
FROM `warehouse_packing_box`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'warehouse_packing_box_item', GREATEST(COALESCE(MAX(`id`) + 1, 850000), 850000), NOW(), NOW()
FROM `warehouse_packing_box_item`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();
