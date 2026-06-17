CREATE TABLE IF NOT EXISTS `product_psku_lifecycle_archive` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `psku` VARCHAR(100) NOT NULL,
    `generation_no` INT NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `sku_parent_snapshot` VARCHAR(100) DEFAULT NULL,
    `title_snapshot` VARCHAR(500) DEFAULT NULL,
    `image_url_snapshot` VARCHAR(1000) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL,
    `valid_from` DATETIME DEFAULT NULL,
    `valid_to` DATETIME DEFAULT NULL,
    `archive_reason` VARCHAR(100) DEFAULT NULL,
    `replaced_by_archive_id` BIGINT DEFAULT NULL,
    `evidence_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_psku_slot` VARCHAR(160)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' AND `status` = 'ACTIVE' THEN CONCAT(`logical_store_id`, '|', `psku`)
                ELSE NULL
            END
        ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_psku_lifecycle_active` (`active_psku_slot`),
    UNIQUE KEY `uk_product_psku_lifecycle_generation` (`logical_store_id`, `psku`, `generation_no`),
    KEY `idx_product_psku_lifecycle_lookup` (`owner_user_id`, `logical_store_id`, `psku`, `status`, `is_deleted`),
    KEY `idx_product_psku_lifecycle_variant` (`product_variant_id`, `is_deleted`),
    KEY `idx_product_psku_lifecycle_validity` (`logical_store_id`, `psku`, `valid_from`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
SELECT 'product_psku_lifecycle_archive', GREATEST(COALESCE(MAX(`id`), 130000), 130000), NOW(), NOW()
FROM `product_psku_lifecycle_archive`
ON DUPLICATE KEY UPDATE
    next_id = GREATEST(next_id, VALUES(next_id)),
    gmt_updated = NOW();
