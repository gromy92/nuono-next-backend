CREATE TABLE IF NOT EXISTS `noon_brand_dictionary` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `brand_key` VARCHAR(200) NOT NULL,
    `brand_name` VARCHAR(200) NOT NULL,
    `label_en` VARCHAR(200) DEFAULT NULL,
    `label_ar` VARCHAR(200) DEFAULT NULL,
    `source` VARCHAR(40) NOT NULL DEFAULT 'manual',
    `status` VARCHAR(30) NOT NULL DEFAULT 'active',
    `usage_count` INT NOT NULL DEFAULT 0,
    `last_seen_at` DATETIME DEFAULT NULL,
    `fetched_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_brand_dictionary_scope_key` (`owner_user_id`, `project_code`, `store_code`, `brand_key`),
    KEY `idx_noon_brand_dictionary_lookup` (`owner_user_id`, `store_code`, `status`, `brand_name`),
    KEY `idx_noon_brand_dictionary_project` (`project_code`, `store_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `noon_product_fulltype_dictionary` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `product_fulltype` VARCHAR(255) NOT NULL,
    `family` VARCHAR(120) DEFAULT NULL,
    `product_type` VARCHAR(120) DEFAULT NULL,
    `product_subtype` VARCHAR(160) DEFAULT NULL,
    `label_en` VARCHAR(255) DEFAULT NULL,
    `label_ar` VARCHAR(255) DEFAULT NULL,
    `source` VARCHAR(40) NOT NULL DEFAULT 'manual',
    `status` VARCHAR(30) NOT NULL DEFAULT 'active',
    `usage_count` INT NOT NULL DEFAULT 0,
    `last_seen_at` DATETIME DEFAULT NULL,
    `fetched_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_fulltype_dictionary_scope_value` (`owner_user_id`, `project_code`, `store_code`, `product_fulltype`),
    KEY `idx_noon_fulltype_dictionary_lookup` (`owner_user_id`, `store_code`, `status`, `product_fulltype`),
    KEY `idx_noon_fulltype_dictionary_family` (`family`, `product_type`, `product_subtype`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('noon_brand_dictionary', 66000, NOW(), NOW()),
    ('noon_product_fulltype_dictionary', 68000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

SET @noon_brand_dictionary_id = (
    SELECT `next_id`
    FROM `product_management_id_sequence`
    WHERE `sequence_name` = 'noon_brand_dictionary'
);

INSERT INTO `noon_brand_dictionary` (
    `id`, `owner_user_id`, `project_code`, `store_code`, `brand_key`, `brand_name`, `label_en`,
    `source`, `status`, `usage_count`, `last_seen_at`, `fetched_at`, `is_deleted`, `created_by`, `updated_by`
)
SELECT
    @noon_brand_dictionary_id := @noon_brand_dictionary_id + 1,
    src.`owner_user_id`,
    src.`project_code`,
    src.`store_code`,
    src.`brand_key`,
    src.`brand_name`,
    src.`brand_name`,
    'product-projection-seed',
    'active',
    src.`usage_count`,
    src.`last_seen_at`,
    NOW(),
    b'0',
    0,
    0
FROM (
    SELECT
        ls.`owner_user_id`,
        ls.`project_code`,
        lss.`store_code`,
        LOWER(TRIM(pm.`brand_cache`)) AS `brand_key`,
        TRIM(pm.`brand_cache`) AS `brand_name`,
        COUNT(DISTINCT pm.`id`) AS `usage_count`,
        MAX(COALESCE(pm.`last_synced_at`, pm.`gmt_updated`, pm.`gmt_create`)) AS `last_seen_at`
    FROM `logical_store` ls
    JOIN `logical_store_site` lss
      ON lss.`logical_store_id` = ls.`id`
     AND lss.`is_deleted` = b'0'
    JOIN `product_master` pm
      ON pm.`logical_store_id` = ls.`id`
     AND pm.`is_deleted` = b'0'
    WHERE ls.`is_deleted` = b'0'
      AND pm.`brand_cache` IS NOT NULL
      AND TRIM(pm.`brand_cache`) != ''
    GROUP BY ls.`owner_user_id`, ls.`project_code`, lss.`store_code`, LOWER(TRIM(pm.`brand_cache`)), TRIM(pm.`brand_cache`)
) src
ORDER BY src.`owner_user_id`, src.`project_code`, src.`store_code`, src.`brand_name`
ON DUPLICATE KEY UPDATE
    `brand_name` = VALUES(`brand_name`),
    `label_en` = VALUES(`label_en`),
    `source` = VALUES(`source`),
    `status` = 'active',
    `usage_count` = GREATEST(`noon_brand_dictionary`.`usage_count`, VALUES(`usage_count`)),
    `last_seen_at` = GREATEST(COALESCE(`noon_brand_dictionary`.`last_seen_at`, '1970-01-01'), COALESCE(VALUES(`last_seen_at`), '1970-01-01')),
    `fetched_at` = VALUES(`fetched_at`),
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

UPDATE `product_management_id_sequence`
SET `next_id` = GREATEST(`next_id`, @noon_brand_dictionary_id),
    `gmt_updated` = NOW()
WHERE `sequence_name` = 'noon_brand_dictionary';

SET @noon_product_fulltype_dictionary_id = (
    SELECT `next_id`
    FROM `product_management_id_sequence`
    WHERE `sequence_name` = 'noon_product_fulltype_dictionary'
);

INSERT INTO `noon_product_fulltype_dictionary` (
    `id`, `owner_user_id`, `project_code`, `store_code`, `product_fulltype`,
    `family`, `product_type`, `product_subtype`, `label_en`,
    `source`, `status`, `usage_count`, `last_seen_at`, `fetched_at`, `is_deleted`, `created_by`, `updated_by`
)
SELECT
    @noon_product_fulltype_dictionary_id := @noon_product_fulltype_dictionary_id + 1,
    src.`owner_user_id`,
    src.`project_code`,
    src.`store_code`,
    src.`product_fulltype`,
    SUBSTRING_INDEX(src.`product_fulltype`, '-', 1),
    CASE
        WHEN src.`product_fulltype` LIKE '%-%-%' THEN SUBSTRING_INDEX(SUBSTRING_INDEX(src.`product_fulltype`, '-', 2), '-', -1)
        WHEN src.`product_fulltype` LIKE '%-%' THEN SUBSTRING_INDEX(src.`product_fulltype`, '-', -1)
        ELSE NULL
    END,
    CASE
        WHEN src.`product_fulltype` LIKE '%-%-%' THEN SUBSTRING_INDEX(src.`product_fulltype`, '-', -1)
        ELSE NULL
    END,
    src.`product_fulltype`,
    'product-projection-seed',
    'active',
    src.`usage_count`,
    src.`last_seen_at`,
    NOW(),
    b'0',
    0,
    0
FROM (
    SELECT
        ls.`owner_user_id`,
        ls.`project_code`,
        lss.`store_code`,
        TRIM(pm.`product_fulltype_cache`) AS `product_fulltype`,
        COUNT(DISTINCT pm.`id`) AS `usage_count`,
        MAX(COALESCE(pm.`last_synced_at`, pm.`gmt_updated`, pm.`gmt_create`)) AS `last_seen_at`
    FROM `logical_store` ls
    JOIN `logical_store_site` lss
      ON lss.`logical_store_id` = ls.`id`
     AND lss.`is_deleted` = b'0'
    JOIN `product_master` pm
      ON pm.`logical_store_id` = ls.`id`
     AND pm.`is_deleted` = b'0'
    WHERE ls.`is_deleted` = b'0'
      AND pm.`product_fulltype_cache` IS NOT NULL
      AND TRIM(pm.`product_fulltype_cache`) != ''
    GROUP BY ls.`owner_user_id`, ls.`project_code`, lss.`store_code`, TRIM(pm.`product_fulltype_cache`)
) src
ORDER BY src.`owner_user_id`, src.`project_code`, src.`store_code`, src.`product_fulltype`
ON DUPLICATE KEY UPDATE
    `family` = VALUES(`family`),
    `product_type` = VALUES(`product_type`),
    `product_subtype` = VALUES(`product_subtype`),
    `label_en` = VALUES(`label_en`),
    `source` = VALUES(`source`),
    `status` = 'active',
    `usage_count` = GREATEST(`noon_product_fulltype_dictionary`.`usage_count`, VALUES(`usage_count`)),
    `last_seen_at` = GREATEST(COALESCE(`noon_product_fulltype_dictionary`.`last_seen_at`, '1970-01-01'), COALESCE(VALUES(`last_seen_at`), '1970-01-01')),
    `fetched_at` = VALUES(`fetched_at`),
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

UPDATE `product_management_id_sequence`
SET `next_id` = GREATEST(`next_id`, @noon_product_fulltype_dictionary_id),
    `gmt_updated` = NOW()
WHERE `sequence_name` = 'noon_product_fulltype_dictionary';
