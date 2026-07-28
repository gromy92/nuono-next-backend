-- Converge the sales/order fact schema formerly created during business writes.
CREATE TABLE IF NOT EXISTS `sales_data_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `daily_sales_fact` (
    `id` BIGINT NOT NULL,
    `source_system` VARCHAR(80) NOT NULL,
    `source_batch_id` BIGINT DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `fact_date` DATE NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) NOT NULL,
    `sku_config` VARCHAR(160) DEFAULT NULL,
    `country_code` VARCHAR(20) DEFAULT NULL,
    `currency_code` VARCHAR(20) DEFAULT NULL,
    `product_title` VARCHAR(1000) DEFAULT NULL,
    `your_visitors` INT DEFAULT NULL,
    `total_visitors` INT DEFAULT NULL,
    `gross_units` INT DEFAULT NULL,
    `shipped_units` INT DEFAULT NULL,
    `cancelled_units` INT DEFAULT NULL,
    `net_units` INT NOT NULL DEFAULT 0,
    `revenue_shipped` DECIMAL(18,6) DEFAULT NULL,
    `buy_box_visitor_percentage` DECIMAL(10,4) DEFAULT NULL,
    `conversion_visitors_percentage` DECIMAL(10,4) DEFAULT NULL,
    `asp_shipped_percentage` DECIMAL(18,6) DEFAULT NULL,
    `source_row_hash` VARCHAR(128) DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_daily_sales_fact_source_scope`
        (`source_system`, `owner_user_id`, `store_code`, `site_code`,
         `fact_date`, `partner_sku`, `sku`),
    KEY `idx_daily_sales_fact_scope_date`
        (`owner_user_id`, `store_code`, `site_code`, `fact_date`),
    KEY `idx_daily_sales_fact_product` (`owner_user_id`, `partner_sku`, `sku`),
    KEY `idx_daily_sales_fact_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `noon_order_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    UNIQUE KEY `uk_noon_order_line_fact_natural`
        (`source_system`, `id_partner`, `country_code`, `item_nr`),
    KEY `idx_noon_order_line_fact_scope_date`
        (`owner_user_id`, `store_code`, `site_code`,
         `report_date_from`, `report_date_to`),
    KEY `idx_noon_order_line_fact_product`
        (`owner_user_id`, `partner_sku`, `sku`),
    KEY `idx_noon_order_line_fact_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- next_id stores the last allocated value. Never move it backwards, and make
-- it at least the largest fact ID before the request-time DDL fallback is gone.
INSERT INTO `sales_data_id_sequence`
    (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES (
    'daily_sales_fact',
    GREATEST(
        100000,
        COALESCE((SELECT MAX(`id`) FROM `daily_sales_fact`), 100000)
    ),
    NOW(),
    NOW()
) AS `incoming`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(
        `sales_data_id_sequence`.`next_id`,
        `incoming`.`next_id`
    ),
    `gmt_updated` = NOW();

INSERT INTO `noon_order_id_sequence`
    (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES (
    'order_line_fact',
    GREATEST(
        200000,
        COALESCE((SELECT MAX(`id`) FROM `noon_order_line_fact`), 200000)
    ),
    NOW(),
    NOW()
) AS `incoming`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(
        `noon_order_id_sequence`.`next_id`,
        `incoming`.`next_id`
    ),
    `gmt_updated` = NOW();
