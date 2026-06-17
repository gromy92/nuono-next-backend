-- Noon official warehouse ASN creation and generic Noon HTTP call log.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `noon_http_call_log` (
    `id` BIGINT NOT NULL,
    `occurred_at` DATETIME NOT NULL,
    `source_module` VARCHAR(80) NOT NULL,
    `operation` VARCHAR(80) NOT NULL,
    `owner_user_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(20) DEFAULT NULL,
    `project_code` VARCHAR(100) DEFAULT NULL,
    `partner_id` VARCHAR(80) DEFAULT NULL,
    `business_type` VARCHAR(80) DEFAULT NULL,
    `business_id` VARCHAR(80) DEFAULT NULL,
    `business_ref` VARCHAR(120) DEFAULT NULL,
    `http_method` VARCHAR(16) DEFAULT NULL,
    `host` VARCHAR(200) DEFAULT NULL,
    `path` VARCHAR(500) DEFAULT NULL,
    `query_hash` VARCHAR(64) DEFAULT NULL,
    `request_summary_json` LONGTEXT DEFAULT NULL,
    `request_hash` VARCHAR(64) DEFAULT NULL,
    `response_status_code` INT DEFAULT NULL,
    `response_summary_json` LONGTEXT DEFAULT NULL,
    `response_hash` VARCHAR(64) DEFAULT NULL,
    `elapsed_ms` BIGINT DEFAULT NULL,
    `status` VARCHAR(30) NOT NULL,
    `failure_type` VARCHAR(80) DEFAULT NULL,
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_noon_http_call_log_time` (`occurred_at`, `source_module`, `operation`),
    KEY `idx_noon_http_call_log_store` (`owner_user_id`, `store_code`, `site_code`),
    KEY `idx_noon_http_call_log_business` (`business_type`, `business_id`, `business_ref`),
    KEY `idx_noon_http_call_log_status` (`status`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `official_warehouse_asn` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `store_name` VARCHAR(200) DEFAULT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `project_code` VARCHAR(100) DEFAULT NULL,
    `partner_id` VARCHAR(80) DEFAULT NULL,
    `local_asn_no` VARCHAR(120) NOT NULL,
    `source_type` VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    `source_dispatch_plan_id` BIGINT DEFAULT NULL,
    `source_dispatch_plan_no` VARCHAR(120) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    `noon_asn_nr` VARCHAR(120) DEFAULT NULL,
    `noon_partner_asn_id` BIGINT DEFAULT NULL,
    `noon_total_qty` INT DEFAULT NULL,
    `noon_asn_status` VARCHAR(80) DEFAULT NULL,
    `noon_updated_at` DATETIME DEFAULT NULL,
    `routing_response_json` LONGTEXT DEFAULT NULL,
    `routing_is_transfer` BIT(1) DEFAULT NULL,
    `selected_warehouse_partner_code` VARCHAR(80) DEFAULT NULL,
    `selected_warehouse_code` VARCHAR(100) DEFAULT NULL,
    `selected_warehouse_name` VARCHAR(200) DEFAULT NULL,
    `product_count` INT NOT NULL DEFAULT 0,
    `total_quantity` INT NOT NULL DEFAULT 0,
    `error_stage` VARCHAR(80) DEFAULT NULL,
    `failure_type` VARCHAR(80) DEFAULT NULL,
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `submitted_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_official_warehouse_asn_local_no` (`local_asn_no`),
    KEY `idx_official_warehouse_asn_store_status` (`owner_user_id`, `store_code`, `site_code`, `status`),
    KEY `idx_official_warehouse_asn_noon_asn` (`noon_asn_nr`),
    KEY `idx_official_warehouse_asn_updated` (`gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `official_warehouse_asn_line` (
    `id` BIGINT NOT NULL,
    `asn_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `product_master_id` BIGINT NOT NULL,
    `product_variant_id` BIGINT NOT NULL,
    `product_site_offer_id` BIGINT DEFAULT NULL,
    `sku_parent` VARCHAR(100) DEFAULT NULL,
    `partner_sku` VARCHAR(100) DEFAULT NULL,
    `child_sku` VARCHAR(100) DEFAULT NULL,
    `psku_code` VARCHAR(100) NOT NULL,
    `noon_sku` VARCHAR(100) NOT NULL,
    `title_cache` VARCHAR(500) DEFAULT NULL,
    `image_url_cache` VARCHAR(1000) DEFAULT NULL,
    `qty` INT NOT NULL,
    `product_length_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_width_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_height_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_weight_g` DECIMAL(12,2) DEFAULT NULL,
    `cubic_feet` DECIMAL(12,5) DEFAULT NULL,
    `storage_type_code` VARCHAR(60) NOT NULL DEFAULT 'standard',
    `noon_partner_asn_line_id` BIGINT DEFAULT NULL,
    `noon_id_cluster` INT DEFAULT NULL,
    `noon_id_storage_type` INT DEFAULT NULL,
    `noon_cluster_code` VARCHAR(100) DEFAULT NULL,
    `noon_asn_status` VARCHAR(80) DEFAULT NULL,
    `noon_country_code` VARCHAR(20) DEFAULT NULL,
    `is_labeled` BIT(1) DEFAULT NULL,
    `is_repl_tool_asn` BIT(1) DEFAULT NULL,
    `line_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_official_warehouse_asn_line_asn` (`asn_id`),
    KEY `idx_official_warehouse_asn_line_product` (`product_variant_id`, `psku_code`),
    KEY `idx_official_warehouse_asn_line_noon` (`noon_partner_asn_line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
  ('official_warehouse_asn', 500000, NOW(), NOW()),
  ('official_warehouse_asn_line', 510000, NOW(), NOW()),
  ('noon_http_call_log', 520000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (9250, '仓库', 0, '/warehouse', b'0', NOW(), NOW()),
  (9252, 'Noon官方仓', 9250, '/warehouse/official-warehouse', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

SET @official_warehouse_root_menu_id = 9250;
SET @official_warehouse_menu_id = 9252;
SET @next_official_warehouse_role_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `role_menu`);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`, `gmt_create`, `gmt_updated`)
SELECT
  @next_official_warehouse_role_menu_id := @next_official_warehouse_role_menu_id + 1,
  grants.`role_id`,
  grants.`menu_id`,
  b'0',
  NOW(),
  NOW()
FROM (
  SELECT 2 AS `role_id`, @official_warehouse_root_menu_id AS `menu_id`
  UNION ALL SELECT 2, @official_warehouse_menu_id
  UNION ALL SELECT 3, @official_warehouse_root_menu_id
  UNION ALL SELECT 3, @official_warehouse_menu_id
  UNION ALL SELECT 5, @official_warehouse_root_menu_id
  UNION ALL SELECT 5, @official_warehouse_menu_id
  UNION ALL SELECT 6, @official_warehouse_root_menu_id
  UNION ALL SELECT 6, @official_warehouse_menu_id
) grants
JOIN `role` r ON r.`id` = grants.`role_id` AND r.`is_deleted` = b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM `role_menu` existing
  WHERE existing.`role_id` = grants.`role_id`
    AND existing.`menu_id` = grants.`menu_id`
);

UPDATE `role_menu` rm
JOIN (
  SELECT
    existing.`role_id`,
    existing.`menu_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = b'0' THEN existing.`id` END), MIN(existing.`id`)) AS `keep_id`
  FROM `role_menu` existing
  WHERE existing.`menu_id` IN (@official_warehouse_root_menu_id, @official_warehouse_menu_id)
  GROUP BY existing.`role_id`, existing.`menu_id`
) keep_row
  ON keep_row.`role_id` = rm.`role_id`
  AND keep_row.`menu_id` = rm.`menu_id`
  AND keep_row.`keep_id` = rm.`id`
SET rm.`is_deleted` = b'0',
    rm.`gmt_updated` = NOW();

SET @next_official_warehouse_user_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `user_menu`);

INSERT INTO `user_menu` (
  `id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  @next_official_warehouse_user_menu_id := @next_official_warehouse_user_menu_id + 1,
  u.`id`,
  rm.`menu_id`,
  1,
  NOW(),
  COALESCE(u.`expired_time`, '2099-12-31 23:59:59'),
  b'0',
  COALESCE(u.`updated_by`, u.`created_by`, 1),
  COALESCE(u.`updated_by`, u.`created_by`, 1),
  NOW(),
  NOW()
FROM `user` u
JOIN `role_menu` rm ON rm.`role_id` = u.`role_id`
  AND rm.`menu_id` IN (@official_warehouse_root_menu_id, @official_warehouse_menu_id)
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = b'0'
  AND u.`status` = 1
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = rm.`menu_id`
  );

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
JOIN `role_menu` rm ON rm.`role_id` = u.`role_id`
  AND rm.`menu_id` = um.`menu_id`
  AND rm.`is_deleted` = b'0'
SET um.`status` = 1,
    um.`is_deleted` = b'0',
    um.`gmt_updated` = NOW()
WHERE um.`menu_id` IN (@official_warehouse_root_menu_id, @official_warehouse_menu_id)
  AND u.`is_deleted` = b'0'
  AND u.`status` = 1;
