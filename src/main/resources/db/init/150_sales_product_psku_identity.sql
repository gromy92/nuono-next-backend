-- Product-level sales/lifecycle identity:
--   business PSKU = partner_sku
--   sku remains the latest external Noon/report SKU for display and fact provenance only.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `product_lifecycle_current_state_psku_merge_map` (
    `duplicate_state_id` BIGINT NOT NULL,
    `canonical_state_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `merge_reason` VARCHAR(100) NOT NULL DEFAULT 'same_store_site_partner_sku',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`duplicate_state_id`),
    KEY `idx_lifecycle_state_psku_merge_canonical` (`canonical_state_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_lifecycle_current_state_psku_merge_map` (
    `duplicate_state_id`,
    `canonical_state_id`,
    `owner_user_id`,
    `store_code`,
    `site_code`,
    `partner_sku`,
    `merge_reason`,
    `gmt_create`,
    `gmt_updated`
)
SELECT
    duplicate_state.id AS duplicate_state_id,
    grouped.canonical_state_id,
    duplicate_state.owner_user_id,
    duplicate_state.store_code,
    duplicate_state.site_code,
    duplicate_state.partner_sku,
    'same_store_site_partner_sku',
    NOW(),
    NOW()
FROM `product_lifecycle_current_state` duplicate_state
JOIN (
    SELECT
        owner_user_id,
        store_code,
        site_code,
        partner_sku,
        MAX(id) AS canonical_state_id,
        COUNT(1) AS row_count
    FROM `product_lifecycle_current_state`
    GROUP BY owner_user_id, store_code, site_code, partner_sku
    HAVING COUNT(1) > 1
) grouped
  ON grouped.owner_user_id = duplicate_state.owner_user_id
 AND grouped.store_code = duplicate_state.store_code
 AND grouped.site_code = duplicate_state.site_code
 AND grouped.partner_sku = duplicate_state.partner_sku
WHERE duplicate_state.id <> grouped.canonical_state_id
ON DUPLICATE KEY UPDATE
    `canonical_state_id` = VALUES(`canonical_state_id`),
    `gmt_updated` = NOW();

UPDATE `product_lifecycle_history` history
JOIN `product_lifecycle_current_state_psku_merge_map` merge_map
  ON merge_map.duplicate_state_id = history.current_state_id
SET history.current_state_id = merge_map.canonical_state_id,
    history.gmt_updated = NOW();

DELETE duplicate_state
FROM `product_lifecycle_current_state` duplicate_state
JOIN `product_lifecycle_current_state_psku_merge_map` merge_map
  ON merge_map.duplicate_state_id = duplicate_state.id;

SET @drop_lifecycle_current_scope := (
  SELECT IF(
    COUNT(1) > 0,
    'ALTER TABLE `product_lifecycle_current_state` DROP INDEX `uk_product_lifecycle_current_scope`',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'product_lifecycle_current_state'
    AND INDEX_NAME = 'uk_product_lifecycle_current_scope'
);
PREPARE stmt FROM @drop_lifecycle_current_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_lifecycle_current_scope := (
  SELECT IF(
    COUNT(1) = 0,
    'ALTER TABLE `product_lifecycle_current_state` ADD UNIQUE KEY `uk_product_lifecycle_current_scope` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`)',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'product_lifecycle_current_state'
    AND INDEX_NAME = 'uk_product_lifecycle_current_scope'
);
PREPARE stmt FROM @add_lifecycle_current_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `sales_forecast_follow_up_psku_merge_map` (
    `duplicate_follow_up_id` BIGINT NOT NULL,
    `canonical_follow_up_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `merge_reason` VARCHAR(100) NOT NULL DEFAULT 'same_store_site_partner_sku',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`duplicate_follow_up_id`),
    KEY `idx_forecast_follow_up_psku_merge_canonical` (`canonical_follow_up_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sales_forecast_follow_up_invalid_psku_archive` (
    `follow_up_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) DEFAULT NULL,
    `archive_reason` VARCHAR(100) NOT NULL DEFAULT 'invalid_partner_sku',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`follow_up_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sales_forecast_follow_up_invalid_psku_archive` (
    `follow_up_id`,
    `owner_user_id`,
    `store_code`,
    `site_code`,
    `partner_sku`,
    `sku`,
    `archive_reason`,
    `gmt_create`,
    `gmt_updated`
)
SELECT
    id,
    owner_user_id,
    store_code,
    site_code,
    partner_sku,
    sku,
    'invalid_partner_sku',
    NOW(),
    NOW()
FROM `sales_forecast_follow_up`
WHERE `partner_sku` IS NULL
   OR TRIM(`partner_sku`) = ''
   OR TRIM(`partner_sku`) = '-'
ON DUPLICATE KEY UPDATE
    `gmt_updated` = NOW();

DELETE invalid_follow_up
FROM `sales_forecast_follow_up` invalid_follow_up
JOIN `sales_forecast_follow_up_invalid_psku_archive` archive
  ON archive.follow_up_id = invalid_follow_up.id;

INSERT INTO `sales_forecast_follow_up_psku_merge_map` (
    `duplicate_follow_up_id`,
    `canonical_follow_up_id`,
    `owner_user_id`,
    `store_code`,
    `site_code`,
    `partner_sku`,
    `merge_reason`,
    `gmt_create`,
    `gmt_updated`
)
SELECT
    duplicate_follow_up.id AS duplicate_follow_up_id,
    grouped.canonical_follow_up_id,
    duplicate_follow_up.owner_user_id,
    duplicate_follow_up.store_code,
    duplicate_follow_up.site_code,
    duplicate_follow_up.partner_sku,
    'same_store_site_partner_sku',
    NOW(),
    NOW()
FROM `sales_forecast_follow_up` duplicate_follow_up
JOIN (
    SELECT
        owner_user_id,
        store_code,
        site_code,
        partner_sku,
        MAX(id) AS canonical_follow_up_id,
        COUNT(1) AS row_count
    FROM `sales_forecast_follow_up`
    GROUP BY owner_user_id, store_code, site_code, partner_sku
    HAVING COUNT(1) > 1
) grouped
  ON grouped.owner_user_id = duplicate_follow_up.owner_user_id
 AND grouped.store_code = duplicate_follow_up.store_code
 AND grouped.site_code = duplicate_follow_up.site_code
 AND grouped.partner_sku = duplicate_follow_up.partner_sku
WHERE duplicate_follow_up.id <> grouped.canonical_follow_up_id
ON DUPLICATE KEY UPDATE
    `canonical_follow_up_id` = VALUES(`canonical_follow_up_id`),
    `gmt_updated` = NOW();

DELETE duplicate_follow_up
FROM `sales_forecast_follow_up` duplicate_follow_up
JOIN `sales_forecast_follow_up_psku_merge_map` merge_map
  ON merge_map.duplicate_follow_up_id = duplicate_follow_up.id;

SET @drop_forecast_follow_up_scope := (
  SELECT IF(
    COUNT(1) > 0,
    'ALTER TABLE `sales_forecast_follow_up` DROP INDEX `uk_sales_forecast_follow_up_product`',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sales_forecast_follow_up'
    AND INDEX_NAME = 'uk_sales_forecast_follow_up_product'
);
PREPARE stmt FROM @drop_forecast_follow_up_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @modify_forecast_follow_up_sku := (
  SELECT IF(
    IS_NULLABLE = 'NO',
    'ALTER TABLE `sales_forecast_follow_up` MODIFY COLUMN `sku` VARCHAR(160) DEFAULT NULL',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sales_forecast_follow_up'
    AND COLUMN_NAME = 'sku'
);
PREPARE stmt FROM @modify_forecast_follow_up_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_forecast_follow_up_scope := (
  SELECT IF(
    COUNT(1) = 0,
    'ALTER TABLE `sales_forecast_follow_up` ADD UNIQUE KEY `uk_sales_forecast_follow_up_product` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`)',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sales_forecast_follow_up'
    AND INDEX_NAME = 'uk_sales_forecast_follow_up_product'
);
PREPARE stmt FROM @add_forecast_follow_up_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `sales_forecast_result_psku_merge_map` (
    `duplicate_result_id` BIGINT NOT NULL,
    `canonical_result_id` BIGINT NOT NULL,
    `run_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `merge_reason` VARCHAR(100) NOT NULL DEFAULT 'same_run_partner_sku',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`duplicate_result_id`),
    KEY `idx_forecast_result_psku_merge_canonical` (`canonical_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sales_forecast_result_invalid_psku_archive` (
    `result_id` BIGINT NOT NULL,
    `run_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) NOT NULL,
    `archive_reason` VARCHAR(100) NOT NULL DEFAULT 'invalid_partner_sku',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sales_forecast_result_invalid_psku_archive` (
    `result_id`,
    `run_id`,
    `owner_user_id`,
    `store_code`,
    `site_code`,
    `partner_sku`,
    `sku`,
    `archive_reason`,
    `gmt_create`,
    `gmt_updated`
)
SELECT
    id,
    run_id,
    owner_user_id,
    store_code,
    site_code,
    partner_sku,
    sku,
    'invalid_partner_sku',
    NOW(),
    NOW()
FROM `sales_forecast_result`
WHERE `partner_sku` IS NULL
   OR TRIM(`partner_sku`) = ''
   OR TRIM(`partner_sku`) = '-'
ON DUPLICATE KEY UPDATE
    `gmt_updated` = NOW();

DELETE invalid_result
FROM `sales_forecast_result` invalid_result
JOIN `sales_forecast_result_invalid_psku_archive` archive
  ON archive.result_id = invalid_result.id;

INSERT INTO `sales_forecast_result_psku_merge_map` (
    `duplicate_result_id`,
    `canonical_result_id`,
    `run_id`,
    `owner_user_id`,
    `store_code`,
    `site_code`,
    `partner_sku`,
    `merge_reason`,
    `gmt_create`,
    `gmt_updated`
)
SELECT
    duplicate_result.id AS duplicate_result_id,
    grouped.canonical_result_id,
    duplicate_result.run_id,
    duplicate_result.owner_user_id,
    duplicate_result.store_code,
    duplicate_result.site_code,
    duplicate_result.partner_sku,
    'same_run_partner_sku',
    NOW(),
    NOW()
FROM `sales_forecast_result` duplicate_result
JOIN (
    SELECT
        run_id,
        partner_sku,
        MAX(id) AS canonical_result_id,
        COUNT(1) AS row_count
    FROM `sales_forecast_result`
    GROUP BY run_id, partner_sku
    HAVING COUNT(1) > 1
) grouped
  ON grouped.run_id = duplicate_result.run_id
 AND grouped.partner_sku = duplicate_result.partner_sku
WHERE duplicate_result.id <> grouped.canonical_result_id
ON DUPLICATE KEY UPDATE
    `canonical_result_id` = VALUES(`canonical_result_id`),
    `gmt_updated` = NOW();

DELETE duplicate_result
FROM `sales_forecast_result` duplicate_result
JOIN `sales_forecast_result_psku_merge_map` merge_map
  ON merge_map.duplicate_result_id = duplicate_result.id;

SET @drop_forecast_result_scope := (
  SELECT IF(
    COUNT(1) > 0,
    'ALTER TABLE `sales_forecast_result` DROP INDEX `uk_sales_forecast_result_run_product`',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sales_forecast_result'
    AND INDEX_NAME = 'uk_sales_forecast_result_run_product'
);
PREPARE stmt FROM @drop_forecast_result_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_forecast_result_scope := (
  SELECT IF(
    COUNT(1) = 0,
    'ALTER TABLE `sales_forecast_result` ADD UNIQUE KEY `uk_sales_forecast_result_run_product` (`run_id`, `partner_sku`)',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sales_forecast_result'
    AND INDEX_NAME = 'uk_sales_forecast_result_run_product'
);
PREPARE stmt FROM @add_forecast_result_scope;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
