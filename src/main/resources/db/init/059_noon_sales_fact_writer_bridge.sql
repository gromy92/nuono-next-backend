CREATE TABLE IF NOT EXISTS `sales_data_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sales_data_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('daily_sales_fact', 100000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

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
    UNIQUE KEY `uk_daily_sales_fact_source_scope` (
        `source_system`,
        `owner_user_id`,
        `store_code`,
        `site_code`,
        `fact_date`,
        `partner_sku`,
        `sku`
    ),
    KEY `idx_daily_sales_fact_scope_date` (`owner_user_id`, `store_code`, `site_code`, `fact_date`),
    KEY `idx_daily_sales_fact_product` (`owner_user_id`, `partner_sku`, `sku`),
    KEY `idx_daily_sales_fact_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
