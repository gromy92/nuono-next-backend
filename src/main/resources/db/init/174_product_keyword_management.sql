SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `product_keyword_id_sequence` (
  `sequence_name` VARCHAR(80) NOT NULL,
  `next_id` BIGINT NOT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_keyword` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `store_code` VARCHAR(100) NOT NULL,
  `site_code` VARCHAR(32) NOT NULL,
  `partner_sku` VARCHAR(160) NOT NULL,
  `keyword` VARCHAR(255) NOT NULL,
  `keyword_norm` VARCHAR(255) NOT NULL,
  `locale` VARCHAR(32) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `intent_tags_json` LONGTEXT DEFAULT NULL,
  `source_summary_json` LONGTEXT DEFAULT NULL,
  `first_seen_at` DATETIME DEFAULT NULL,
  `last_seen_at` DATETIME DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `active_unique_key` TINYINT
    GENERATED ALWAYS AS (
      CASE WHEN `is_deleted` = b'0' THEN 1 ELSE NULL END
    ) STORED,
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_keyword_scope` (
    `owner_user_id`, `store_code`, `site_code`, `partner_sku`, `keyword_norm`, `active_unique_key`
  ),
  KEY `idx_product_keyword_product` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `status`),
  KEY `idx_product_keyword_store_status` (`owner_user_id`, `store_code`, `site_code`, `status`, `last_seen_at`),
  KEY `idx_product_keyword_norm` (`keyword_norm`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `product_keyword_usage_event` (
  `id` BIGINT NOT NULL,
  `keyword_id` BIGINT DEFAULT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `store_code` VARCHAR(100) NOT NULL,
  `site_code` VARCHAR(32) NOT NULL,
  `partner_sku` VARCHAR(160) NOT NULL,
  `keyword` VARCHAR(255) NOT NULL,
  `keyword_norm` VARCHAR(255) NOT NULL,
  `source_type` VARCHAR(40) NOT NULL,
  `source_ref_type` VARCHAR(120) DEFAULT NULL,
  `source_ref_id` BIGINT DEFAULT NULL,
  `source_ref_key` VARCHAR(512) DEFAULT NULL,
  `event_natural_key` VARCHAR(512) NOT NULL,
  `event_status` VARCHAR(40) NOT NULL,
  `occurred_at` DATETIME NOT NULL,
  `fact_date_from` DATE DEFAULT NULL,
  `fact_date_to` DATE DEFAULT NULL,
  `payload_json` LONGTEXT DEFAULT NULL,
  `metrics_json` LONGTEXT DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_keyword_event_natural` (`owner_user_id`, `event_natural_key`),
  KEY `idx_product_keyword_event_product` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `occurred_at`),
  KEY `idx_product_keyword_event_keyword` (`keyword_id`, `source_type`, `occurred_at`),
  KEY `idx_product_keyword_event_source` (`source_type`, `source_ref_type`, `source_ref_id`),
  KEY `idx_product_keyword_event_source_key` (`source_type`, `source_ref_type`, `source_ref_key`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `product_keyword_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_keyword', GREATEST(COALESCE(MAX(`id`), 0) + 1, 300000), NOW(), NOW()
FROM `product_keyword`
UNION ALL
SELECT 'product_keyword_usage_event', GREATEST(COALESCE(MAX(`id`), 0) + 1, 320000), NOW(), NOW()
FROM `product_keyword_usage_event`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = VALUES(`gmt_updated`);

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (9800, '运营', 0, '/operations', b'0', NOW(), NOW()),
  (9804, '关键词数据', 9800, '/operations/product-keywords', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

SET @operations_menu_id = 9800;
SET @product_keyword_menu_id = 9804;
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
  SELECT 2 AS `role_id`, 9800 AS `menu_id`
  UNION ALL SELECT 2, 9804
  UNION ALL SELECT 3, 9800
  UNION ALL SELECT 3, 9804
  UNION ALL SELECT 4, 9800
  UNION ALL SELECT 4, 9804
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
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = b'0' THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `role_menu` existing
  WHERE existing.`role_id` IN (2, 3, 4)
    AND existing.`menu_id` IN (@operations_menu_id, @product_keyword_menu_id)
  GROUP BY existing.`role_id`, existing.`menu_id`
) keep_row ON keep_row.`id` = rm.`id`
SET rm.`is_deleted` = b'0',
    rm.`gmt_updated` = NOW();

UPDATE `role_menu` duplicate
JOIN `role_menu` keep_row
  ON keep_row.`role_id` = duplicate.`role_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = b'0'
  AND duplicate.`is_deleted` = b'0'
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = b'1',
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` IN (@operations_menu_id, @product_keyword_menu_id);

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` = 1
  AND `menu_id` IN (@operations_menu_id, @product_keyword_menu_id)
  AND `is_deleted` = b'0';

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
  AND rm.`menu_id` IN (@operations_menu_id, @product_keyword_menu_id)
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = 0
  AND u.`status` = 1
  AND u.`role_id` IN (2, 3, 4)
  AND COALESCE(u.`account_type`, '') = 'internal'
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = rm.`menu_id`
  );

UPDATE `user_menu` um
JOIN (
  SELECT
    existing.`user_id`,
    existing.`menu_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = 0 THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `user_menu` existing
  JOIN `user` u ON u.`id` = existing.`user_id`
  WHERE existing.`menu_id` IN (@operations_menu_id, @product_keyword_menu_id)
    AND u.`role_id` IN (2, 3, 4)
    AND u.`is_deleted` = 0
    AND u.`status` = 1
    AND COALESCE(u.`account_type`, '') = 'internal'
  GROUP BY existing.`user_id`, existing.`menu_id`
) keep_row ON keep_row.`id` = um.`id`
SET um.`status` = 1,
    um.`is_deleted` = b'0',
    um.`gmt_updated` = NOW();

UPDATE `user_menu` duplicate
JOIN `user_menu` keep_row
  ON keep_row.`user_id` = duplicate.`user_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = 0
  AND duplicate.`is_deleted` = 0
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = 1,
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` IN (@operations_menu_id, @product_keyword_menu_id);

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`status` = 0,
    um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE um.`menu_id` IN (@operations_menu_id, @product_keyword_menu_id)
  AND u.`role_id` = 1
  AND um.`is_deleted` = b'0';
