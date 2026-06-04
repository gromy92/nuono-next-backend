-- Renamed from 060_noon_order_fact_writer_bridge.sql to keep migration prefixes unique.
CREATE TABLE IF NOT EXISTS `noon_order_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `noon_order_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('order_line_fact', 200000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `noon_order_line_fact` (
    `id` BIGINT NOT NULL,
    `source_system` VARCHAR(80) NOT NULL,
    `source_batch_id` VARCHAR(160) DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `id_partner` VARCHAR(80) NOT NULL,
    `src_country` VARCHAR(20) DEFAULT NULL,
    `country_code` VARCHAR(20) NOT NULL,
    `dest_country` VARCHAR(20) DEFAULT NULL,
    `bayan_nr` VARCHAR(120) DEFAULT NULL,
    `item_nr` VARCHAR(160) NOT NULL,
    `order_identity` VARCHAR(160) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) NOT NULL,
    `status` VARCHAR(80) NOT NULL,
    `offer_price` DECIMAL(18,6) DEFAULT NULL,
    `gmv_lcy` DECIMAL(18,6) DEFAULT NULL,
    `currency_code` VARCHAR(20) DEFAULT NULL,
    `brand_code` VARCHAR(160) DEFAULT NULL,
    `family` VARCHAR(255) DEFAULT NULL,
    `fulfillment_model` VARCHAR(160) DEFAULT NULL,
    `order_timestamp` DATETIME DEFAULT NULL,
    `shipment_timestamp` DATETIME DEFAULT NULL,
    `delivered_timestamp` DATETIME DEFAULT NULL,
    `report_date_from` DATE NOT NULL,
    `report_date_to` DATE NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_order_line_fact_natural` (
        `source_system`,
        `id_partner`,
        `country_code`,
        `item_nr`
    ),
    KEY `idx_noon_order_line_fact_scope_date` (
        `owner_user_id`,
        `store_code`,
        `site_code`,
        `report_date_from`,
        `report_date_to`
    ),
    KEY `idx_noon_order_line_fact_product` (`owner_user_id`, `partner_sku`, `sku`),
    KEY `idx_noon_order_line_fact_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
