CREATE TABLE IF NOT EXISTS `product_variant_spec` (
    `id` BIGINT NOT NULL,
    `variant_id` BIGINT NOT NULL,
    `product_length_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_width_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_height_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_weight_g` DECIMAL(12,2) DEFAULT NULL,
    `carton_length_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_width_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_height_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_weight_kg` DECIMAL(12,3) DEFAULT NULL,
    `carton_quantity` INT DEFAULT NULL,
    `battery_magnetic_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `liquid_powder_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `source_type` VARCHAR(40) NOT NULL DEFAULT 'manual',
    `confirmed_at` DATETIME DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_variant_spec_variant` (`variant_id`),
    KEY `idx_product_variant_spec_logistics` (`battery_magnetic_type`, `liquid_powder_type`),
    KEY `idx_product_variant_spec_updated` (`gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_variant_spec', GREATEST(COALESCE(MAX(`id`), 99000), 99000), NOW(), NOW()
FROM `product_variant_spec`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
