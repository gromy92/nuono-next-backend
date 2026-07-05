-- Promote manual-selection analysis to first-class selection groups and persist profit snapshots.
-- Scope: additive/idempotent schema only; no external writes.

SET NAMES utf8mb4;

SET @add_source_site_code := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND COLUMN_NAME = 'site_code'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `site_code` VARCHAR(20) DEFAULT NULL AFTER `logical_store_id`'
  )
);
PREPARE stmt FROM @add_source_site_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_source_collection_source := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND COLUMN_NAME = 'collection_source'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `collection_source` VARCHAR(30) NOT NULL DEFAULT ''browser'' AFTER `source_type`'
  )
);
PREPARE stmt FROM @add_source_collection_source;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_selection_source_collection`
SET `collection_source` = 'browser'
WHERE `collection_source` IS NULL
   OR TRIM(`collection_source`) = '';

UPDATE `product_selection_source_collection`
SET `collection_source` = 'plugin'
WHERE LOWER(TRIM(`collection_source`)) IN ('plugin', 'extension', 'browser-extension')
   OR `collection_source` LIKE '%插件%';

UPDATE `product_selection_source_collection`
SET site_code = CASE
    WHEN LOWER(CONCAT(COALESCE(source_url, ''), ' ', COALESCE(page_url, ''))) REGEXP 'amazon\\.ae|noon\\.com/uae-|/uae-en/|/uae-ar/' THEN 'AE'
    WHEN LOWER(CONCAT(COALESCE(source_url, ''), ' ', COALESCE(page_url, ''))) REGEXP 'amazon\\.sa|noon\\.com/saudi-|/saudi-en/|/saudi-ar/' THEN 'SA'
    ELSE site_code
END
WHERE is_deleted = b'0'
  AND (site_code IS NULL OR TRIM(site_code) = '')
  AND LOWER(CONCAT(COALESCE(source_url, ''), ' ', COALESCE(page_url, ''))) REGEXP 'amazon\\.ae|amazon\\.sa|noon\\.com/uae-|noon\\.com/saudi-|/uae-en/|/uae-ar/|/saudi-en/|/saudi-ar/';

UPDATE `product_selection_source_collection` source
JOIN `logical_store_site` site
  ON site.logical_store_id = source.logical_store_id
 AND site.is_deleted = b'0'
 AND site.id = (
     SELECT site_pick.id
     FROM `logical_store_site` site_pick
     WHERE site_pick.logical_store_id = source.logical_store_id
       AND site_pick.is_deleted = b'0'
     ORDER BY site_pick.is_reference_site DESC, site_pick.id ASC
     LIMIT 1
 )
SET source.site_code = CASE
    WHEN UPPER(TRIM(site.site)) IN ('AE', 'ARE', 'UAE') OR site.site LIKE '%阿联酋%' THEN 'AE'
    WHEN UPPER(TRIM(site.site)) IN ('SA', 'SAU', 'KSA') OR site.site LIKE '%沙特%' THEN 'SA'
    ELSE UPPER(TRIM(site.site))
END
WHERE source.is_deleted = b'0'
  AND (source.site_code IS NULL OR TRIM(source.site_code) = '');

SET @add_source_site_idx := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_source_collection'
        AND INDEX_NAME = 'idx_product_selection_source_site'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_source_collection` ADD KEY `idx_product_selection_source_site` (`logical_store_id`, `site_code`, `collected_at`)'
  )
);
PREPARE stmt FROM @add_source_site_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `product_selection_analysis_item` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `logical_store_id` BIGINT NOT NULL,
  `site_code` VARCHAR(20) DEFAULT NULL,
  `project_id` BIGINT DEFAULT NULL,
  `project_name` VARCHAR(200) DEFAULT NULL,
  `source_collection_id` BIGINT NOT NULL,
  `ali1688_purchase_url` VARCHAR(1000) DEFAULT NULL,
  `purchase_price_rmb` DECIMAL(12,2) DEFAULT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'active',
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_selection_analysis_source` (`source_collection_id`),
  KEY `idx_product_selection_analysis_store` (`logical_store_id`, `site_code`, `gmt_create`),
  KEY `idx_product_selection_analysis_owner` (`owner_user_id`, `gmt_create`),
  KEY `idx_product_selection_analysis_project` (`logical_store_id`, `site_code`, `project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_analysis_site_code := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_analysis_item'
        AND COLUMN_NAME = 'site_code'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_analysis_item` ADD COLUMN `site_code` VARCHAR(20) DEFAULT NULL AFTER `logical_store_id`'
  )
);
PREPARE stmt FROM @add_analysis_site_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_analysis_project_id := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_analysis_item'
        AND COLUMN_NAME = 'project_id'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_analysis_item` ADD COLUMN `project_id` BIGINT DEFAULT NULL AFTER `site_code`'
  )
);
PREPARE stmt FROM @add_analysis_project_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_analysis_project_name := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_analysis_item'
        AND COLUMN_NAME = 'project_name'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_analysis_item` ADD COLUMN `project_name` VARCHAR(200) DEFAULT NULL AFTER `project_id`'
  )
);
PREPARE stmt FROM @add_analysis_project_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_analysis_project_idx := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_selection_analysis_item'
        AND INDEX_NAME = 'idx_product_selection_analysis_project'
    ),
    'SELECT 1',
    'ALTER TABLE `product_selection_analysis_item` ADD KEY `idx_product_selection_analysis_project` (`logical_store_id`, `site_code`, `project_id`)'
  )
);
PREPARE stmt FROM @add_analysis_project_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_selection_analysis_item` item
JOIN `product_selection_source_collection` source
  ON source.id = item.source_collection_id
 AND source.is_deleted = b'0'
SET item.site_code = source.site_code
WHERE item.is_deleted = b'0'
  AND (item.site_code IS NULL OR TRIM(item.site_code) = '')
  AND source.site_code IS NOT NULL
  AND TRIM(source.site_code) <> '';

UPDATE `product_selection_analysis_item` item
JOIN `product_selection_source_collection` source
  ON source.id = item.source_collection_id
 AND source.is_deleted = b'0'
SET item.project_id = item.id,
    item.project_name = COALESCE(
      NULLIF(TRIM(item.project_name), ''),
      NULLIF(TRIM(source.source_title_cn), ''),
      NULLIF(TRIM(source.source_title), ''),
      CONCAT('选品项目 ', item.id)
    )
WHERE item.is_deleted = b'0'
  AND (item.project_id IS NULL OR item.project_id <= 0);

CREATE TABLE IF NOT EXISTS `product_selection_group` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `logical_store_id` BIGINT NOT NULL,
  `site_code` VARCHAR(20) DEFAULT NULL,
  `group_no` VARCHAR(60) NOT NULL,
  `group_name` VARCHAR(200) NOT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'active',
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_selection_group_no` (`group_no`),
  KEY `idx_product_selection_group_store` (`logical_store_id`, `site_code`, `gmt_create`),
  KEY `idx_product_selection_group_owner` (`owner_user_id`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_group_material` (
  `id` BIGINT NOT NULL,
  `group_id` BIGINT NOT NULL,
  `source_collection_id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `logical_store_id` BIGINT NOT NULL,
  `site_code` VARCHAR(20) DEFAULT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'active',
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `active_source_collection_id` BIGINT
    GENERATED ALWAYS AS (
      CASE WHEN `is_deleted` = b'0' AND `status` = 'active' THEN `source_collection_id` ELSE NULL END
    ) STORED,
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_selection_group_material_source` (`active_source_collection_id`),
  KEY `idx_product_selection_group_material_group` (`group_id`, `is_deleted`, `id`),
  KEY `idx_product_selection_group_material_store` (`logical_store_id`, `site_code`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_group_procurement` (
  `group_id` BIGINT NOT NULL,
  `ali1688_purchase_url` VARCHAR(1000) DEFAULT NULL,
  `purchase_price_rmb` DECIMAL(12,2) DEFAULT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'active',
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_group_competitor` (
  `id` BIGINT NOT NULL,
  `group_id` BIGINT NOT NULL,
  `competitor_url` VARCHAR(1000) NOT NULL,
  `note` VARCHAR(500) DEFAULT NULL,
  `fetch_status` VARCHAR(30) NOT NULL DEFAULT 'pending',
  `fetched_payload_json` LONGTEXT DEFAULT NULL,
  `fetched_at` DATETIME DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_selection_group_competitor_group` (`group_id`, `is_deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_group_profit_snapshot` (
  `id` BIGINT NOT NULL,
  `group_id` BIGINT NOT NULL,
  `currency_code` VARCHAR(10) DEFAULT NULL,
  `profit_amount` DECIMAL(18,2) DEFAULT NULL,
  `profit_margin` DECIMAL(10,4) DEFAULT NULL,
  `snapshot_json` LONGTEXT DEFAULT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'draft',
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_selection_group_profit_group` (`group_id`, `is_deleted`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_selection_group` (
  `id`, `owner_user_id`, `logical_store_id`, `site_code`, `group_no`, `group_name`, `status`, `is_deleted`,
  `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  MIN(COALESCE(item.project_id, item.id)) AS `id`,
  MIN(item.owner_user_id) AS `owner_user_id`,
  MIN(item.logical_store_id) AS `logical_store_id`,
  MIN(item.site_code) AS `site_code`,
  CONCAT('PSG-', MIN(COALESCE(item.project_id, item.id))) AS `group_no`,
  COALESCE(
    NULLIF(MAX(item.project_name), ''),
    NULLIF(MAX(source.source_title_cn), ''),
    NULLIF(MAX(source.source_title), ''),
    CONCAT('选品组 ', MIN(COALESCE(item.project_id, item.id)))
  ) AS `group_name`,
  'active' AS `status`,
  b'0' AS `is_deleted`,
  MIN(item.created_by) AS `created_by`,
  MAX(item.updated_by) AS `updated_by`,
  MIN(item.gmt_create) AS `gmt_create`,
  MAX(item.gmt_updated) AS `gmt_updated`
FROM `product_selection_analysis_item` item
JOIN `product_selection_source_collection` source
  ON source.id = item.source_collection_id
 AND source.is_deleted = b'0'
WHERE item.is_deleted = b'0'
GROUP BY COALESCE(item.project_id, item.id)
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `status` = 'active',
  `is_deleted` = b'0',
  `updated_by` = VALUES(`updated_by`),
  `gmt_updated` = VALUES(`gmt_updated`);

INSERT INTO `product_selection_group_material` (
  `id`, `group_id`, `source_collection_id`, `owner_user_id`, `logical_store_id`, `site_code`, `status`, `is_deleted`,
  `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  item.id AS `id`,
  COALESCE(item.project_id, item.id) AS `group_id`,
  item.source_collection_id,
  item.owner_user_id,
  item.logical_store_id,
  item.site_code,
  'active' AS `status`,
  b'0' AS `is_deleted`,
  item.created_by,
  item.updated_by,
  item.gmt_create,
  item.gmt_updated
FROM `product_selection_analysis_item` item
WHERE item.is_deleted = b'0'
ON DUPLICATE KEY UPDATE
  `group_id` = VALUES(`group_id`),
  `status` = 'active',
  `is_deleted` = b'0',
  `updated_by` = VALUES(`updated_by`),
  `gmt_updated` = VALUES(`gmt_updated`);

INSERT INTO `product_selection_group_procurement` (
  `group_id`, `ali1688_purchase_url`, `purchase_price_rmb`, `status`, `is_deleted`,
  `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  MIN(COALESCE(item.project_id, item.id)) AS `group_id`,
  MAX(NULLIF(TRIM(item.ali1688_purchase_url), '')) AS `ali1688_purchase_url`,
  MAX(item.purchase_price_rmb) AS `purchase_price_rmb`,
  CASE
    WHEN COUNT(DISTINCT CONCAT(COALESCE(NULLIF(TRIM(item.ali1688_purchase_url), ''), ''), '|', COALESCE(CAST(item.purchase_price_rmb AS CHAR), ''))) <= 1
      THEN 'active'
    ELSE 'review_required'
  END AS `status`,
  b'0' AS `is_deleted`,
  MIN(item.created_by) AS `created_by`,
  MAX(item.updated_by) AS `updated_by`,
  MIN(item.gmt_create) AS `gmt_create`,
  MAX(item.gmt_updated) AS `gmt_updated`
FROM `product_selection_analysis_item` item
WHERE item.is_deleted = b'0'
  AND (NULLIF(TRIM(item.ali1688_purchase_url), '') IS NOT NULL OR item.purchase_price_rmb IS NOT NULL)
GROUP BY COALESCE(item.project_id, item.id)
ON DUPLICATE KEY UPDATE
  `ali1688_purchase_url` = VALUES(`ali1688_purchase_url`),
  `purchase_price_rmb` = VALUES(`purchase_price_rmb`),
  `status` = VALUES(`status`),
  `is_deleted` = b'0',
  `updated_by` = VALUES(`updated_by`),
  `gmt_updated` = VALUES(`gmt_updated`);

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_analysis_item', GREATEST(COALESCE(MAX(`id`) + 1, 89000), 89000), NOW(), NOW()
FROM `product_selection_analysis_item`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_group', GREATEST(COALESCE(MAX(`id`) + 1, 91000), 91000), NOW(), NOW()
FROM `product_selection_group`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_group_material', GREATEST(COALESCE(MAX(`id`) + 1, 92000), 92000), NOW(), NOW()
FROM `product_selection_group_material`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_group_competitor', GREATEST(COALESCE(MAX(`id`) + 1, 93000), 93000), NOW(), NOW()
FROM `product_selection_group_competitor`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_group_profit_snapshot', GREATEST(COALESCE(MAX(`id`) + 1, 94000), 94000), NOW(), NOW()
FROM `product_selection_group_profit_snapshot`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();
