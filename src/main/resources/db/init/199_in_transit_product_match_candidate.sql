SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `in_transit_product_match_candidate` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `package_id` BIGINT DEFAULT NULL,
    `box_no` VARCHAR(160) NOT NULL,
    `source_barcode` VARCHAR(160) NOT NULL,
    `source_psku` VARCHAR(160) DEFAULT NULL,
    `source_msku` VARCHAR(160) DEFAULT NULL,
    `product_name` VARCHAR(500) DEFAULT NULL,
    `store_code` VARCHAR(80) DEFAULT NULL,
    `site_code` VARCHAR(80) DEFAULT NULL,
    `shipped_quantity` INT NOT NULL DEFAULT 0,
    `received_quantity` INT NOT NULL DEFAULT 0,
    `carton_count` INT DEFAULT NULL,
    `units_per_carton` INT DEFAULT NULL,
    `carton_weight_kg` DECIMAL(18,4) DEFAULT NULL,
    `carton_volume_cbm` DECIMAL(18,6) DEFAULT NULL,
    `match_status` VARCHAR(30) NOT NULL DEFAULT 'UNMATCHED',
    `match_message` VARCHAR(500) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `active_unique_key` TINYINT GENERATED ALWAYS AS (
        CASE WHEN `is_deleted` = b'0' THEN 1 ELSE NULL END
    ) STORED,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_in_transit_match_candidate` (
        `owner_user_id`, `batch_id`, `box_no`, `source_barcode`, `active_unique_key`
    ),
    KEY `idx_in_transit_match_candidate_batch` (
        `owner_user_id`, `batch_id`, `match_status`, `is_deleted`
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
