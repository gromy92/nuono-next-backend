CREATE TABLE IF NOT EXISTS `product_variant_spec` (
    `id` BIGINT NOT NULL,
    `variant_id` BIGINT NOT NULL,
    `product_length_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_width_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_height_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_weight_g` DECIMAL(12,2) DEFAULT NULL,
    `carton_length_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_width_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_height_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_weight_kg` DECIMAL(12,3) DEFAULT NULL,
    `carton_quantity` INT DEFAULT NULL,
    `battery_magnetic_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `liquid_powder_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `source_type` VARCHAR(40) NOT NULL DEFAULT 'manual',
    `confirmed_at` DATETIME DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_variant_spec_variant` (`variant_id`),
    KEY `idx_product_variant_spec_logistics` (`battery_magnetic_type`, `liquid_powder_type`),
    KEY `idx_product_variant_spec_updated` (`gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @product_variant_spec_add_effective_source_id := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `product_variant_spec` ADD COLUMN `effective_source_id` BIGINT DEFAULT NULL AFTER `variant_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant_spec'
      AND COLUMN_NAME = 'effective_source_id'
);
PREPARE stmt FROM @product_variant_spec_add_effective_source_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_variant_spec_add_effective_source_type := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `product_variant_spec` ADD COLUMN `effective_source_type` VARCHAR(40) DEFAULT NULL AFTER `effective_source_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant_spec'
      AND COLUMN_NAME = 'effective_source_type'
);
PREPARE stmt FROM @product_variant_spec_add_effective_source_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_variant_spec_add_effective_source_index := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `product_variant_spec` ADD KEY `idx_product_variant_spec_effective_source` (`effective_source_id`, `effective_source_type`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_variant_spec'
      AND INDEX_NAME = 'idx_product_variant_spec_effective_source'
);
PREPARE stmt FROM @product_variant_spec_add_effective_source_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `product_variant_spec_source` (
    `id` BIGINT NOT NULL,
    `variant_id` BIGINT NOT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `product_length_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_width_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_height_cm` DECIMAL(10,2) DEFAULT NULL,
    `product_weight_g` DECIMAL(12,2) DEFAULT NULL,
    `carton_length_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_width_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_height_cm` DECIMAL(10,2) DEFAULT NULL,
    `carton_weight_kg` DECIMAL(12,3) DEFAULT NULL,
    `carton_quantity` INT DEFAULT NULL,
    `carton_source_type` VARCHAR(40) NOT NULL DEFAULT 'none',
    `battery_magnetic_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `liquid_powder_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `source_recorded_at` DATETIME DEFAULT NULL,
    `confirmed_at` DATETIME DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_variant_spec_source_variant_type` (`variant_id`, `source_type`),
    KEY `idx_product_variant_spec_source_variant` (`variant_id`),
    KEY `idx_product_variant_spec_source_type` (`source_type`, `carton_source_type`),
    KEY `idx_product_variant_spec_source_updated` (`gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_variant_spec', GREATEST(COALESCE(MAX(`id`), 99000), 99000), NOW(), NOW()
FROM `product_variant_spec`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO product_management_id_sequence (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_variant_spec_source', GREATEST(COALESCE(MAX(`id`), 120000), 120000), NOW(), NOW()
FROM `product_variant_spec_source`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO product_variant_spec_source (
    `id`, `variant_id`, `source_type`,
    `product_length_cm`, `product_width_cm`, `product_height_cm`, `product_weight_g`,
    `carton_length_cm`, `carton_width_cm`, `carton_height_cm`, `carton_weight_kg`, `carton_quantity`,
    `carton_source_type`, `battery_magnetic_type`, `liquid_powder_type`,
    `source_recorded_at`, `confirmed_at`, `confirmed_by`,
    `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
    120000 + pvs.id,
    pvs.variant_id,
    CASE
        WHEN pvs.source_type IN ('legacy_online', 'gm_forecast_excel') THEN 'ali1688'
        ELSE 'warehouse'
    END,
    pvs.product_length_cm, pvs.product_width_cm, pvs.product_height_cm, pvs.product_weight_g,
    pvs.carton_length_cm, pvs.carton_width_cm, pvs.carton_height_cm, pvs.carton_weight_kg, pvs.carton_quantity,
    CASE
        WHEN pvs.carton_length_cm IS NULL
         AND pvs.carton_width_cm IS NULL
         AND pvs.carton_height_cm IS NULL
         AND pvs.carton_weight_kg IS NULL
         AND pvs.carton_quantity IS NULL THEN 'none'
        WHEN pvs.source_type IN ('legacy_online', 'gm_forecast_excel') THEN 'factory_carton'
        ELSE 'warehouse_measured'
    END,
    COALESCE(pvs.battery_magnetic_type, 'unknown'),
    COALESCE(pvs.liquid_powder_type, 'unknown'),
    COALESCE(pvs.confirmed_at, pvs.gmt_updated, pvs.gmt_create, NOW()),
    pvs.confirmed_at,
    pvs.confirmed_by,
    b'0',
    pvs.created_by,
    pvs.updated_by,
    COALESCE(pvs.gmt_create, NOW()),
    COALESCE(pvs.gmt_updated, NOW())
FROM product_variant_spec pvs
WHERE pvs.is_deleted = 0
ON DUPLICATE KEY UPDATE
    `product_length_cm` = VALUES(`product_length_cm`),
    `product_width_cm` = VALUES(`product_width_cm`),
    `product_height_cm` = VALUES(`product_height_cm`),
    `product_weight_g` = VALUES(`product_weight_g`),
    `carton_length_cm` = VALUES(`carton_length_cm`),
    `carton_width_cm` = VALUES(`carton_width_cm`),
    `carton_height_cm` = VALUES(`carton_height_cm`),
    `carton_weight_kg` = VALUES(`carton_weight_kg`),
    `carton_quantity` = VALUES(`carton_quantity`),
    `carton_source_type` = VALUES(`carton_source_type`),
    `battery_magnetic_type` = VALUES(`battery_magnetic_type`),
    `liquid_powder_type` = VALUES(`liquid_powder_type`),
    `source_recorded_at` = VALUES(`source_recorded_at`),
    `confirmed_at` = VALUES(`confirmed_at`),
    `confirmed_by` = VALUES(`confirmed_by`),
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

UPDATE product_variant_spec pvs
JOIN product_variant_spec_source pvss
  ON pvss.variant_id = pvs.variant_id
 AND pvss.source_type = CASE
        WHEN pvs.source_type IN ('legacy_online', 'gm_forecast_excel') THEN 'ali1688'
        ELSE 'warehouse'
     END
 AND pvss.is_deleted = 0
SET pvs.effective_source_id = pvss.id,
    pvs.effective_source_type = pvss.source_type,
    pvs.gmt_updated = NOW()
WHERE pvs.is_deleted = 0
  AND pvs.effective_source_id IS NULL;

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES (9104, '商品规格', 3, '/product/specs', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `parent_id` = VALUES(`parent_id`),
    `url_path` = VALUES(`url_path`),
    `is_deleted` = b'0',
    `gmt_updated` = NOW();

SET @product_spec_menu_id = (
    SELECT id
    FROM `menu`
    WHERE `url_path` = '/product/specs'
      AND `is_deleted` = b'0'
    ORDER BY id ASC
    LIMIT 1
);

SET @next_product_spec_role_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `role_menu`);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`, `gmt_create`, `gmt_updated`)
SELECT
    @next_product_spec_role_menu_id := @next_product_spec_role_menu_id + 1,
    r.`id`,
    @product_spec_menu_id,
    b'0',
    NOW(),
    NOW()
FROM `role` r
WHERE r.`id` IN (2, 3, 4, 5, 6)
  AND r.`is_deleted` = b'0'
  AND @product_spec_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM `role_menu` existing
      WHERE existing.`role_id` = r.`id`
        AND existing.`menu_id` = @product_spec_menu_id
  );

UPDATE `role_menu` rm
JOIN (
    SELECT
        existing.`role_id`,
        COALESCE(MIN(CASE WHEN existing.`is_deleted` = b'0' THEN existing.`id` END), MIN(existing.`id`)) AS `id`
    FROM `role_menu` existing
    WHERE existing.`role_id` IN (2, 3, 4, 5, 6)
      AND existing.`menu_id` = @product_spec_menu_id
    GROUP BY existing.`role_id`
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
WHERE duplicate.`menu_id` = @product_spec_menu_id;

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` = 1
  AND `menu_id` = @product_spec_menu_id
  AND `is_deleted` = b'0';

SET @next_product_spec_user_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `user_menu`);

INSERT INTO `user_menu` (
    `id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`,
    `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
    @next_product_spec_user_menu_id := @next_product_spec_user_menu_id + 1,
    u.`id`,
    @product_spec_menu_id,
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
  AND rm.`menu_id` = @product_spec_menu_id
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = 0
  AND u.`status` = 1
  AND COALESCE(u.`account_type`, '') = 'internal'
  AND NOT EXISTS (
      SELECT 1
      FROM `user_menu` existing
      WHERE existing.`user_id` = u.`id`
        AND existing.`menu_id` = @product_spec_menu_id
  );

UPDATE `user_menu` um
JOIN (
    SELECT
        existing.`user_id`,
        COALESCE(MIN(CASE WHEN existing.`is_deleted` = 0 THEN existing.`id` END), MIN(existing.`id`)) AS `id`
    FROM `user_menu` existing
    JOIN `user` u ON u.`id` = existing.`user_id`
    WHERE existing.`menu_id` = @product_spec_menu_id
      AND u.`role_id` IN (2, 3, 4, 5, 6)
      AND u.`is_deleted` = 0
      AND u.`status` = 1
      AND COALESCE(u.`account_type`, '') = 'internal'
    GROUP BY existing.`user_id`
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
WHERE duplicate.`menu_id` = @product_spec_menu_id;

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE u.`role_id` = 1
  AND um.`menu_id` = @product_spec_menu_id
  AND um.`is_deleted` = b'0';
