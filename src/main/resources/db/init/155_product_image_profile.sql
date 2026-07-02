-- PSKU-bound product image asset and element archive for AI image generation.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `product_image_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `owner_user_id` BIGINT NOT NULL,
  `store_code` VARCHAR(64) NOT NULL,
  `logical_store_id` BIGINT DEFAULT NULL,
  `psku_code` VARCHAR(100) NOT NULL,
  `product_identity_key` VARCHAR(120) NOT NULL,
  `product_master_id` BIGINT DEFAULT NULL,
  `product_variant_id` BIGINT DEFAULT NULL,
  `product_title` VARCHAR(500) DEFAULT NULL,
  `brand` VARCHAR(200) DEFAULT NULL,
  `title_ar` VARCHAR(500) DEFAULT NULL,
  `title_en` VARCHAR(500) DEFAULT NULL,
  `spec_summary` VARCHAR(500) DEFAULT NULL,
  `product_fact_text` LONGTEXT DEFAULT NULL,
  `hero_selling_points_json` LONGTEXT DEFAULT NULL,
  `profile_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_image_profile_identity` (`owner_user_id`, `store_code`, `psku_code`, `product_identity_key`, `active_unique_key`),
  KEY `idx_product_image_profile_scope` (`owner_user_id`, `store_code`, `deleted`, `updated_at`),
  KEY `idx_product_image_profile_logical_scope` (`owner_user_id`, `logical_store_id`, `deleted`, `updated_at`),
  KEY `idx_product_image_profile_logical_identity` (`owner_user_id`, `logical_store_id`, `psku_code`, `product_identity_key`, `deleted`),
  KEY `idx_product_image_profile_variant` (`product_variant_id`),
  KEY `idx_product_image_profile_master` (`product_master_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @product_image_profile_add_product_fact_text := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile'
        AND COLUMN_NAME = 'product_fact_text'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile` ADD COLUMN `product_fact_text` LONGTEXT DEFAULT NULL AFTER `spec_summary`'
  )
);
PREPARE stmt FROM @product_image_profile_add_product_fact_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `product_image_profile_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `profile_id` BIGINT NOT NULL,
  `image_url` VARCHAR(1024) NOT NULL,
  `content_type` VARCHAR(120) DEFAULT NULL,
  `size_bytes` BIGINT DEFAULT NULL,
  `width_px` INT DEFAULT NULL,
  `height_px` INT DEFAULT NULL,
  `source_store_code` VARCHAR(64) DEFAULT NULL,
  `source_site_code` VARCHAR(32) DEFAULT NULL,
  `source_snapshot_id` BIGINT DEFAULT NULL,
  `source_field` VARCHAR(120) DEFAULT NULL,
  `source_kind` VARCHAR(120) DEFAULT NULL,
  `image_role` VARCHAR(32) NOT NULL DEFAULT 'MAIN',
  `sort_order` INT NOT NULL DEFAULT 0,
  `asset_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_image_profile_asset_profile` (`profile_id`, `asset_status`, `sort_order`, `id`),
  KEY `idx_product_image_profile_asset_source` (`source_store_code`, `source_site_code`, `source_snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @product_image_profile_asset_add_content_type := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'content_type'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `content_type` VARCHAR(120) DEFAULT NULL AFTER `image_url`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_content_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_size_bytes := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'size_bytes'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `size_bytes` BIGINT DEFAULT NULL AFTER `content_type`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_size_bytes;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_width_px := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'width_px'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `width_px` INT DEFAULT NULL AFTER `size_bytes`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_width_px;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_image_profile_asset_add_height_px := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_image_profile_asset'
        AND COLUMN_NAME = 'height_px'
    ),
    'SELECT 1',
    'ALTER TABLE `product_image_profile_asset` ADD COLUMN `height_px` INT DEFAULT NULL AFTER `width_px`'
  )
);
PREPARE stmt FROM @product_image_profile_asset_add_height_px;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `product_image_section` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `profile_id` BIGINT NOT NULL,
  `section_type` VARCHAR(40) NOT NULL,
  `title_ar` VARCHAR(500) DEFAULT NULL,
  `title_en` VARCHAR(500) DEFAULT NULL,
  `description_ar` TEXT DEFAULT NULL,
  `description_en` TEXT DEFAULT NULL,
  `attributes_text` TEXT DEFAULT NULL,
  `focus_part` VARCHAR(200) DEFAULT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` BIT(1) NOT NULL DEFAULT b'1',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_product_image_section_profile` (`profile_id`, `deleted`, `sort_order`, `id`),
  KEY `idx_product_image_section_type` (`section_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `product_image_suite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `profile_id` BIGINT NOT NULL,
  `suite_name` VARCHAR(160) NOT NULL,
  `skin_id` BIGINT DEFAULT NULL,
  `skin_name` VARCHAR(160) DEFAULT NULL,
  `generation_task_id` VARCHAR(120) DEFAULT NULL,
  `draft_package_json` LONGTEXT DEFAULT NULL,
  `draft_prompt_text` LONGTEXT DEFAULT NULL,
  `suite_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `adopted_at` DATETIME DEFAULT NULL,
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_product_image_suite_profile` (`profile_id`, `deleted`, `suite_status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `product_image_suite_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `suite_id` BIGINT NOT NULL,
  `image_role` VARCHAR(40) NOT NULL,
  `image_url` VARCHAR(1024) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_image_suite_asset_suite` (`suite_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (3, '商品', 0, '/product', b'0', NOW(), NOW()),
  (9106, '商品图', 3, '/product/images', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

SET @product_images_menu_id = 9106;
SET @next_product_images_role_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `role_menu`);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`, `gmt_create`, `gmt_updated`)
SELECT
  @next_product_images_role_menu_id := @next_product_images_role_menu_id + 1,
  grants.`role_id`,
  @product_images_menu_id,
  b'0',
  NOW(),
  NOW()
FROM (
  SELECT 2 AS `role_id`
  UNION ALL SELECT 3
  UNION ALL SELECT 4
) grants
JOIN `role` r ON r.`id` = grants.`role_id` AND r.`is_deleted` = b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM `role_menu` existing
  WHERE existing.`role_id` = grants.`role_id`
    AND existing.`menu_id` = @product_images_menu_id
);

UPDATE `role_menu` rm
JOIN (
  SELECT
    existing.`role_id`,
    existing.`menu_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = b'0' THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `role_menu` existing
  WHERE existing.`role_id` IN (2, 3, 4)
    AND existing.`menu_id` = @product_images_menu_id
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
WHERE duplicate.`menu_id` = @product_images_menu_id;

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` = 1
  AND `menu_id` = @product_images_menu_id
  AND `is_deleted` = b'0';

SET @next_product_images_user_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `user_menu`);

INSERT INTO `user_menu` (
  `id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  @next_product_images_user_menu_id := @next_product_images_user_menu_id + 1,
  u.`id`,
  @product_images_menu_id,
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
  AND rm.`menu_id` = @product_images_menu_id
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = 0
  AND u.`status` = 1
  AND u.`role_id` IN (2, 3, 4)
  AND COALESCE(u.`account_type`, '') = 'internal'
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = @product_images_menu_id
  );

UPDATE `user_menu` um
JOIN (
  SELECT
    existing.`user_id`,
    existing.`menu_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = 0 THEN existing.`id` END), MIN(existing.`id`)) AS `id`,
    COALESCE(MAX(u.`expired_time`), '2099-12-31 23:59:59') AS `expired_time`
  FROM `user_menu` existing
  JOIN `user` u ON u.`id` = existing.`user_id`
  WHERE existing.`menu_id` = @product_images_menu_id
    AND u.`role_id` IN (2, 3, 4)
    AND u.`is_deleted` = 0
    AND u.`status` = 1
    AND COALESCE(u.`account_type`, '') = 'internal'
  GROUP BY existing.`user_id`, existing.`menu_id`
) keep_row ON keep_row.`id` = um.`id`
SET um.`status` = 1,
    um.`effective_time` = NOW(),
    um.`expired_time` = keep_row.`expired_time`,
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
WHERE duplicate.`menu_id` = @product_images_menu_id;

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`status` = 0,
    um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE um.`menu_id` = @product_images_menu_id
  AND u.`role_id` = 1
  AND um.`is_deleted` = b'0';
