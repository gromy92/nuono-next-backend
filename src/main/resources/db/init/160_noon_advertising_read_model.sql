SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `noon_ad_id_sequence` (
  `sequence_name` VARCHAR(80) NOT NULL,
  `next_id` BIGINT NOT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `noon_ad_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
  ('noon_ad_report_batch', 200000, NOW(), NOW()),
  ('noon_ad_campaign_fact', 210000, NOW(), NOW()),
  ('noon_ad_query_fact', 220000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `next_id` = `next_id`,
  `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `noon_ad_report_batch` (
  `id` BIGINT NOT NULL,
  `source_system` VARCHAR(80) NOT NULL DEFAULT 'noon_ads',
  `source_name` VARCHAR(255) DEFAULT NULL,
  `source_digest_sha256` VARCHAR(128) DEFAULT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `project_code` VARCHAR(100) NOT NULL,
  `store_code` VARCHAR(100) NOT NULL,
  `site_code` VARCHAR(32) NOT NULL,
  `report_date_from` DATE NOT NULL,
  `report_date_to` DATE NOT NULL,
  `status` VARCHAR(40) NOT NULL DEFAULT 'imported',
  `campaign_row_count` INT NOT NULL DEFAULT 0,
  `query_row_count` INT NOT NULL DEFAULT 0,
  `notes` VARCHAR(1000) DEFAULT NULL,
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_noon_ad_batch_scope` (`owner_user_id`, `store_code`, `site_code`, `report_date_from`, `report_date_to`),
  KEY `idx_noon_ad_batch_project` (`owner_user_id`, `project_code`, `report_date_from`, `report_date_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `noon_ad_campaign_fact` (
  `id` BIGINT NOT NULL,
  `batch_id` BIGINT NOT NULL,
  `source_system` VARCHAR(80) NOT NULL DEFAULT 'noon_ads',
  `owner_user_id` BIGINT NOT NULL,
  `project_code` VARCHAR(100) NOT NULL,
  `store_code` VARCHAR(100) NOT NULL,
  `site_code` VARCHAR(32) NOT NULL,
  `report_date_from` DATE NOT NULL,
  `report_date_to` DATE NOT NULL,
  `campaign_code` VARCHAR(120) NOT NULL,
  `campaign_name` VARCHAR(500) DEFAULT NULL,
  `campaign_status` VARCHAR(80) DEFAULT NULL,
  `qc_status` VARCHAR(80) DEFAULT NULL,
  `adgroup_code` VARCHAR(120) DEFAULT NULL,
  `campaign_start_date` DATE DEFAULT NULL,
  `campaign_end_date` DATE DEFAULT NULL,
  `views` INT NOT NULL DEFAULT 0,
  `clicks` INT NOT NULL DEFAULT 0,
  `orders_count` INT NOT NULL DEFAULT 0,
  `assisted_orders` INT NOT NULL DEFAULT 0,
  `atc_count` INT NOT NULL DEFAULT 0,
  `spend_amount` DECIMAL(18,6) DEFAULT NULL,
  `ad_revenue` DECIMAL(18,6) DEFAULT NULL,
  `ctr_percentage` DECIMAL(10,4) DEFAULT NULL,
  `roas` DECIMAL(18,6) DEFAULT NULL,
  `cpc` DECIMAL(18,6) DEFAULT NULL,
  `cps` DECIMAL(18,6) DEFAULT NULL,
  `cvr_percentage` DECIMAL(10,4) DEFAULT NULL,
  `zero_order_spend_amount` DECIMAL(18,6) DEFAULT NULL,
  `zero_order_spend_share` DECIMAL(10,6) DEFAULT NULL,
  `raw_payload_json` LONGTEXT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_noon_ad_campaign_scope` (
    `source_system`, `owner_user_id`, `store_code`, `site_code`,
    `report_date_from`, `report_date_to`, `campaign_code`
  ),
  KEY `idx_noon_ad_campaign_batch` (`batch_id`),
  KEY `idx_noon_ad_campaign_performance` (`owner_user_id`, `store_code`, `site_code`, `spend_amount`, `orders_count`, `roas`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `noon_ad_query_fact` (
  `id` BIGINT NOT NULL,
  `batch_id` BIGINT NOT NULL,
  `source_system` VARCHAR(80) NOT NULL DEFAULT 'noon_ads',
  `owner_user_id` BIGINT NOT NULL,
  `project_code` VARCHAR(100) NOT NULL,
  `store_code` VARCHAR(100) NOT NULL,
  `site_code` VARCHAR(32) NOT NULL,
  `report_date_from` DATE NOT NULL,
  `report_date_to` DATE NOT NULL,
  `campaign_code` VARCHAR(120) NOT NULL,
  `campaign_name` VARCHAR(500) DEFAULT NULL,
  `ad_sku_code` VARCHAR(160) NOT NULL DEFAULT '',
  `partner_sku` VARCHAR(160) NOT NULL DEFAULT '',
  `query_text` VARCHAR(1000) NOT NULL,
  `query_hash` VARCHAR(128) NOT NULL,
  `query_kind` VARCHAR(40) DEFAULT NULL,
  `views` INT NOT NULL DEFAULT 0,
  `clicks` INT NOT NULL DEFAULT 0,
  `orders_count` INT NOT NULL DEFAULT 0,
  `assisted_orders` INT NOT NULL DEFAULT 0,
  `atc_count` INT NOT NULL DEFAULT 0,
  `spend_amount` DECIMAL(18,6) DEFAULT NULL,
  `ad_revenue` DECIMAL(18,6) DEFAULT NULL,
  `ctr_percentage` DECIMAL(10,4) DEFAULT NULL,
  `roas` DECIMAL(18,6) DEFAULT NULL,
  `cpc` DECIMAL(18,6) DEFAULT NULL,
  `cps` DECIMAL(18,6) DEFAULT NULL,
  `cvr_percentage` DECIMAL(10,4) DEFAULT NULL,
  `raw_payload_json` LONGTEXT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_noon_ad_query_scope` (
    `source_system`, `owner_user_id`, `store_code`, `site_code`,
    `report_date_from`, `report_date_to`, `campaign_code`, `query_hash`
  ),
  KEY `idx_noon_ad_query_batch` (`batch_id`),
  KEY `idx_noon_ad_query_performance` (`owner_user_id`, `store_code`, `site_code`, `spend_amount`, `orders_count`, `roas`),
  KEY `idx_noon_ad_query_text` (`owner_user_id`, `store_code`, `site_code`, `query_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @noon_ad_query_has_legacy_sku = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'noon_ad_query_fact'
    AND COLUMN_NAME = 'sku'
);
SET @noon_ad_query_has_ad_sku_code = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'noon_ad_query_fact'
    AND COLUMN_NAME = 'ad_sku_code'
);
SET @noon_ad_query_ad_sku_code_sql = IF(
  @noon_ad_query_has_legacy_sku > 0 AND @noon_ad_query_has_ad_sku_code = 0,
  'ALTER TABLE `noon_ad_query_fact` CHANGE COLUMN `sku` `ad_sku_code` VARCHAR(160) NOT NULL DEFAULT '''' AFTER `campaign_name`',
  'SELECT 1'
);
PREPARE noon_ad_query_ad_sku_code_stmt FROM @noon_ad_query_ad_sku_code_sql;
EXECUTE noon_ad_query_ad_sku_code_stmt;
DEALLOCATE PREPARE noon_ad_query_ad_sku_code_stmt;

SET @noon_ad_query_has_ad_sku_code = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'noon_ad_query_fact'
    AND COLUMN_NAME = 'ad_sku_code'
);
SET @noon_ad_query_add_ad_sku_code_sql = IF(
  @noon_ad_query_has_ad_sku_code = 0,
  'ALTER TABLE `noon_ad_query_fact` ADD COLUMN `ad_sku_code` VARCHAR(160) NOT NULL DEFAULT '''' AFTER `campaign_name`',
  'SELECT 1'
);
PREPARE noon_ad_query_add_ad_sku_code_stmt FROM @noon_ad_query_add_ad_sku_code_sql;
EXECUTE noon_ad_query_add_ad_sku_code_stmt;
DEALLOCATE PREPARE noon_ad_query_add_ad_sku_code_stmt;

SET @noon_ad_query_has_partner_sku = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'noon_ad_query_fact'
    AND COLUMN_NAME = 'partner_sku'
);
SET @noon_ad_query_partner_sku_sql = IF(
  @noon_ad_query_has_partner_sku = 0,
  'ALTER TABLE `noon_ad_query_fact` ADD COLUMN `partner_sku` VARCHAR(160) NOT NULL DEFAULT '''' AFTER `ad_sku_code`',
  'SELECT 1'
);
PREPARE noon_ad_query_partner_sku_stmt FROM @noon_ad_query_partner_sku_sql;
EXECUTE noon_ad_query_partner_sku_stmt;
DEALLOCATE PREPARE noon_ad_query_partner_sku_stmt;

UPDATE `noon_ad_query_fact`
SET `ad_sku_code` = CASE
      WHEN COALESCE(`ad_sku_code`, '') = '' THEN `partner_sku`
      ELSE `ad_sku_code`
    END,
    `partner_sku` = ''
WHERE COALESCE(`partner_sku`, '') REGEXP '^Z[A-Za-z0-9]+(-[0-9]+)?$';

UPDATE `noon_ad_query_fact`
SET `query_hash` = SHA2(CONCAT(
  COALESCE(`campaign_code`, ''), CHAR(31),
  COALESCE(`partner_sku`, ''), CHAR(31),
  COALESCE(`ad_sku_code`, ''), CHAR(31),
  COALESCE(`query_text`, ''), CHAR(31),
  COALESCE(`query_kind`, '')
), 256);

SET @noon_ad_query_has_scope_index = (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'noon_ad_query_fact'
    AND INDEX_NAME = 'uk_noon_ad_query_scope'
);
SET @noon_ad_query_drop_scope_index_sql = IF(
  @noon_ad_query_has_scope_index > 0,
  'ALTER TABLE `noon_ad_query_fact` DROP INDEX `uk_noon_ad_query_scope`',
  'SELECT 1'
);
PREPARE noon_ad_query_drop_scope_index_stmt FROM @noon_ad_query_drop_scope_index_sql;
EXECUTE noon_ad_query_drop_scope_index_stmt;
DEALLOCATE PREPARE noon_ad_query_drop_scope_index_stmt;

ALTER TABLE `noon_ad_query_fact`
  ADD UNIQUE KEY `uk_noon_ad_query_scope` (
    `source_system`, `owner_user_id`, `store_code`, `site_code`,
    `report_date_from`, `report_date_to`, `campaign_code`, `query_hash`
  );

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (9800, '运营', 0, '/operations', b'0', NOW(), NOW()),
  (9803, '广告投放经营台', 9800, '/operations/noon-ads', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

SET @noon_ads_menu_id = 9803;
SET @next_role_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `role_menu`);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`, `gmt_create`, `gmt_updated`)
SELECT
  @next_role_menu_id := @next_role_menu_id + 1,
  grants.`role_id`,
  grants.`menu_id`,
  b'0',
  NOW(),
  NOW()
FROM (
  SELECT 2 AS `role_id`, @noon_ads_menu_id AS `menu_id`
  UNION ALL SELECT 3, @noon_ads_menu_id
  UNION ALL SELECT 4, @noon_ads_menu_id
) grants
JOIN `role` r ON r.`id` = grants.`role_id` AND r.`is_deleted` = b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM `role_menu` existing
  WHERE existing.`role_id` = grants.`role_id`
    AND existing.`menu_id` = grants.`menu_id`
    AND existing.`is_deleted` = b'0'
);

UPDATE `role_menu`
SET `is_deleted` = b'0',
    `gmt_updated` = NOW()
WHERE `role_id` IN (2, 3, 4)
  AND `menu_id` = @noon_ads_menu_id;

UPDATE `role_menu` duplicate
JOIN `role_menu` keep_row
  ON keep_row.`role_id` = duplicate.`role_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = b'0'
  AND duplicate.`is_deleted` = b'0'
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = b'1',
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` = @noon_ads_menu_id;

SET @next_user_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `user_menu`);

INSERT INTO `user_menu` (
  `id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  @next_user_menu_id := @next_user_menu_id + 1,
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
  AND rm.`menu_id` = @noon_ads_menu_id
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = b'0'
  AND u.`status` = 1
  AND u.`role_id` IN (2, 3, 4)
  AND COALESCE(u.`account_type`, '') = 'internal'
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = rm.`menu_id`
      AND existing.`is_deleted` = b'0'
  );

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
JOIN `role_menu` rm ON rm.`role_id` = u.`role_id`
  AND rm.`menu_id` = um.`menu_id`
  AND rm.`is_deleted` = b'0'
SET um.`status` = 1,
    um.`is_deleted` = b'0',
    um.`gmt_updated` = NOW()
WHERE um.`menu_id` = @noon_ads_menu_id
  AND u.`is_deleted` = b'0'
  AND u.`status` = 1
  AND u.`role_id` IN (2, 3, 4)
  AND COALESCE(u.`account_type`, '') = 'internal';

UPDATE `user_menu` duplicate
JOIN `user_menu` keep_row
  ON keep_row.`user_id` = duplicate.`user_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = b'0'
  AND duplicate.`is_deleted` = b'0'
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = b'1',
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` = @noon_ads_menu_id;
