-- Archived procurement history for PSKU generations that are no longer active.
-- Scope: preserve old purchase facts without attaching them to the reused/current product.

CREATE TABLE IF NOT EXISTS `procurement_archived_purchase_history` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `psku_lifecycle_archive_id` BIGINT NOT NULL,
    `psku` VARCHAR(100) NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `purchase_order_id` BIGINT DEFAULT NULL,
    `purchase_order_item_id` BIGINT DEFAULT NULL,
    `purchase_order_item_site_id` BIGINT DEFAULT NULL,
    `order_no_snapshot` VARCHAR(60) DEFAULT NULL,
    `order_title_snapshot` VARCHAR(200) NOT NULL,
    `business_date` DATE NOT NULL,
    `site_code` VARCHAR(20) DEFAULT NULL,
    `quantity` INT NOT NULL DEFAULT 0,
    `amount_rmb` DECIMAL(18, 2) DEFAULT NULL,
    `air_quantity` INT DEFAULT NULL,
    `sea_quantity` INT DEFAULT NULL,
    `product_name_snapshot` VARCHAR(300) DEFAULT NULL,
    `spec_snapshot` VARCHAR(500) DEFAULT NULL,
    `source_file` VARCHAR(300) DEFAULT NULL,
    `source_sheet` VARCHAR(100) DEFAULT NULL,
    `source_row_refs` VARCHAR(500) DEFAULT NULL,
    `source_row_key` VARCHAR(200) NOT NULL,
    `resolution_type` VARCHAR(40) NOT NULL DEFAULT 'ARCHIVED_PRODUCT',
    `record_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    `source_detail_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_source_slot` VARCHAR(420)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' THEN CONCAT(`logical_store_id`, '|', `psku_lifecycle_archive_id`, '|', `source_row_key`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_archived_purchase_history_source` (`active_source_slot`),
    KEY `idx_procurement_archived_purchase_history_archive` (`psku_lifecycle_archive_id`, `is_deleted`, `business_date`),
    KEY `idx_procurement_archived_purchase_history_psku` (`owner_user_id`, `logical_store_id`, `psku`, `is_deleted`, `business_date`),
    KEY `idx_procurement_archived_purchase_history_variant` (`product_variant_id`, `is_deleted`),
    KEY `idx_procurement_archived_purchase_history_order` (`purchase_order_id`, `purchase_order_item_id`, `purchase_order_item_site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
SELECT 'procurement_archived_purchase_history', GREATEST(COALESCE(MAX(`id`), 250000), 250000), NOW(), NOW()
FROM `procurement_archived_purchase_history`
ON DUPLICATE KEY UPDATE
    next_id = GREATEST(next_id, VALUES(next_id)),
    gmt_updated = NOW();
