-- PSKU product model realignment.
-- Product = PSKU master: logical_store_id + partner_sku.
-- current_z_code stores the current Noon Z code for the PSKU.
-- Product site offer state = logical_store_id + partner_sku + site_code.
-- product_variant remains a migration compatibility table and is not the business product identity.

CREATE TABLE IF NOT EXISTS `product_psku_model_realignment_violation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `violation_type` VARCHAR(80) NOT NULL,
    `product_master_id` BIGINT DEFAULT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `partner_skus` TEXT DEFAULT NULL,
    `row_count` INT NOT NULL DEFAULT 0,
    `detail` VARCHAR(500) DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_psku_model_violation_type` (`violation_type`, `product_master_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PSKU product model migration diagnostics';

SET @pm_add_partner_sku := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_master` ADD COLUMN `partner_sku` VARCHAR(100) DEFAULT NULL AFTER `logical_store_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_master'
      AND COLUMN_NAME = 'partner_sku'
);
PREPARE stmt FROM @pm_add_partner_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pm_add_current_z_code := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_master` ADD COLUMN `current_z_code` VARCHAR(100) DEFAULT NULL AFTER `partner_sku`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_master'
      AND COLUMN_NAME = 'current_z_code'
);
PREPARE stmt FROM @pm_add_current_z_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE FROM `product_psku_model_realignment_violation`
WHERE `violation_type` = 'MULTI_ACTIVE_PARTNER_SKU_PER_MASTER';

INSERT INTO `product_psku_model_realignment_violation` (
    `violation_type`,
    `product_master_id`,
    `logical_store_id`,
    `partner_skus`,
    `row_count`,
    `detail`
)
SELECT
    'MULTI_ACTIVE_PARTNER_SKU_PER_MASTER',
    pm.id,
    pm.logical_store_id,
    GROUP_CONCAT(DISTINCT TRIM(pv.partner_sku) ORDER BY TRIM(pv.partner_sku) SEPARATOR ','),
    COUNT(DISTINCT TRIM(pv.partner_sku)),
    'product_master has multiple active product_variant.partner_sku values; product_master.partner_sku left unfilled'
FROM `product_master` pm
JOIN `product_variant` pv
  ON pv.product_master_id = pm.id
WHERE pm.is_deleted = b'0'
  AND pv.is_deleted = b'0'
  AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL
GROUP BY pm.id, pm.logical_store_id
HAVING COUNT(DISTINCT TRIM(pv.partner_sku)) > 1;

-- Backfill invariants: pm.partner_sku = pv.partner_sku only when the master has exactly one active PSKU;
-- pm.current_z_code = pm.sku_parent when no explicit current Z code exists.
UPDATE `product_master` pm
JOIN (
    SELECT
        pv.product_master_id,
        MIN(TRIM(pv.partner_sku)) AS partner_sku
    FROM `product_variant` pv
    WHERE pv.is_deleted = b'0'
      AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL
    GROUP BY pv.product_master_id
    HAVING COUNT(DISTINCT TRIM(pv.partner_sku)) = 1
) chosen
  ON chosen.product_master_id = pm.id
SET pm.partner_sku = chosen.partner_sku,
    pm.current_z_code = COALESCE(NULLIF(TRIM(pm.current_z_code), ''), pm.sku_parent),
    pm.gmt_updated = NOW()
WHERE pm.is_deleted = b'0'
  AND (
      NULLIF(TRIM(pm.partner_sku), '') IS NULL
      OR TRIM(pm.partner_sku) <> chosen.partner_sku
      OR (
          NULLIF(TRIM(pm.current_z_code), '') IS NULL
          AND NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL
      )
  );

UPDATE `product_master` pm
SET pm.current_z_code = COALESCE(NULLIF(TRIM(pm.current_z_code), ''), pm.sku_parent),
    pm.gmt_updated = NOW()
WHERE pm.is_deleted = b'0'
  AND NULLIF(TRIM(pm.current_z_code), '') IS NULL
  AND NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL;

DELETE FROM `product_psku_model_realignment_violation`
WHERE `violation_type` = 'DUPLICATE_PRODUCT_MASTER_STORE_PARTNER_SKU';

INSERT INTO `product_psku_model_realignment_violation` (
    `violation_type`,
    `logical_store_id`,
    `partner_skus`,
    `row_count`,
    `detail`
)
SELECT
    'DUPLICATE_PRODUCT_MASTER_STORE_PARTNER_SKU',
    duplicate_pm.logical_store_id,
    duplicate_pm.partner_sku,
    duplicate_pm.row_count,
    'duplicate product_master logical_store_id + partner_sku target key; unique key creation skipped'
FROM (
    SELECT
        pm.logical_store_id,
        TRIM(pm.partner_sku) AS partner_sku,
        COUNT(*) AS row_count
    FROM `product_master` pm
    WHERE pm.is_deleted = b'0'
      AND NULLIF(TRIM(pm.partner_sku), '') IS NOT NULL
    GROUP BY pm.logical_store_id, TRIM(pm.partner_sku)
    HAVING COUNT(*) > 1
) duplicate_pm;

SET @pm_add_store_partner_sku_unique := (
    SELECT IF(
        COUNT(1) = 0
            AND NOT EXISTS (
                SELECT 1
                FROM `product_psku_model_realignment_violation`
                WHERE `violation_type` = 'DUPLICATE_PRODUCT_MASTER_STORE_PARTNER_SKU'
            ),
        'ALTER TABLE `product_master` ADD UNIQUE KEY `uk_product_master_store_partner_sku` (`logical_store_id`, `partner_sku`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_master'
      AND INDEX_NAME = 'uk_product_master_store_partner_sku'
);
PREPARE stmt FROM @pm_add_store_partner_sku_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_product_master_id := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_site_offer` ADD COLUMN `product_master_id` BIGINT DEFAULT NULL AFTER `id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'product_master_id'
);
PREPARE stmt FROM @pso_add_product_master_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_logical_store_id := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_site_offer` ADD COLUMN `logical_store_id` BIGINT DEFAULT NULL AFTER `product_master_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'logical_store_id'
);
PREPARE stmt FROM @pso_add_logical_store_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_partner_sku := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_site_offer` ADD COLUMN `partner_sku` VARCHAR(100) DEFAULT NULL AFTER `logical_store_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'partner_sku'
);
PREPARE stmt FROM @pso_add_partner_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_site_code := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_site_offer` ADD COLUMN `site_code` VARCHAR(30) DEFAULT NULL AFTER `site_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'site_code'
);
PREPARE stmt FROM @pso_add_site_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_site_offer` pso
JOIN `product_variant` pv
  ON pv.id = pso.variant_id
JOIN `product_master` pm
  ON pm.id = pv.product_master_id
JOIN `logical_store_site` lss
  ON lss.id = pso.site_id
SET pso.product_master_id = pm.id,
    pso.logical_store_id = pm.logical_store_id,
    pso.partner_sku = TRIM(pv.partner_sku),
    pso.site_code = TRIM(lss.site),
    pso.gmt_updated = NOW()
WHERE pso.is_deleted = b'0'
  AND pv.is_deleted = b'0'
  AND pm.is_deleted = b'0'
  AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL
  AND NULLIF(TRIM(lss.site), '') IS NOT NULL
  AND (
      pso.product_master_id IS NULL
      OR pso.product_master_id <> pm.id
      OR pso.logical_store_id IS NULL
      OR pso.logical_store_id <> pm.logical_store_id
      OR NULLIF(TRIM(pso.partner_sku), '') IS NULL
      OR TRIM(pso.partner_sku) <> TRIM(pv.partner_sku)
      OR NULLIF(TRIM(pso.site_code), '') IS NULL
      OR TRIM(pso.site_code) <> TRIM(lss.site)
  );

DELETE FROM `product_psku_model_realignment_violation`
WHERE `violation_type` = 'DUPLICATE_PRODUCT_SITE_OFFER_STORE_PSKU_SITE';

INSERT INTO `product_psku_model_realignment_violation` (
    `violation_type`,
    `logical_store_id`,
    `partner_skus`,
    `row_count`,
    `detail`
)
SELECT
    'DUPLICATE_PRODUCT_SITE_OFFER_STORE_PSKU_SITE',
    duplicate_pso.logical_store_id,
    duplicate_pso.partner_sku,
    duplicate_pso.row_count,
    CONCAT('duplicate product_site_offer logical_store_id + partner_sku + site_code target key; site_code=', duplicate_pso.site_code)
FROM (
    SELECT
        pso.logical_store_id,
        TRIM(pso.partner_sku) AS partner_sku,
        TRIM(pso.site_code) AS site_code,
        COUNT(*) AS row_count
    FROM `product_site_offer` pso
    WHERE pso.is_deleted = b'0'
      AND NULLIF(TRIM(pso.partner_sku), '') IS NOT NULL
      AND NULLIF(TRIM(pso.site_code), '') IS NOT NULL
    GROUP BY pso.logical_store_id, TRIM(pso.partner_sku), TRIM(pso.site_code)
    HAVING COUNT(*) > 1
) duplicate_pso;

SET @pso_add_store_psku_site_unique := (
    SELECT IF(
        COUNT(1) = 0
            AND NOT EXISTS (
                SELECT 1
                FROM `product_psku_model_realignment_violation`
                WHERE `violation_type` = 'DUPLICATE_PRODUCT_SITE_OFFER_STORE_PSKU_SITE'
            ),
        'ALTER TABLE `product_site_offer` ADD UNIQUE KEY `uk_product_site_offer_store_psku_site` (`logical_store_id`, `partner_sku`, `site_code`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND INDEX_NAME = 'uk_product_site_offer_store_psku_site'
);
PREPARE stmt FROM @pso_add_store_psku_site_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_add_product_master_site_index := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_master_site` (`product_master_id`, `site_id`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND INDEX_NAME = 'idx_product_site_offer_master_site'
);
PREPARE stmt FROM @pso_add_product_master_site_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pfda_add_logical_store_id := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_forwarder_declaration_attribute` ADD COLUMN `logical_store_id` BIGINT DEFAULT NULL AFTER `product_variant_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_forwarder_declaration_attribute'
      AND COLUMN_NAME = 'logical_store_id'
);
PREPARE stmt FROM @pfda_add_logical_store_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pfda_add_source_store_code := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_forwarder_declaration_attribute` ADD COLUMN `source_store_code` VARCHAR(100) DEFAULT NULL AFTER `logical_store_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_forwarder_declaration_attribute'
      AND COLUMN_NAME = 'source_store_code'
);
PREPARE stmt FROM @pfda_add_source_store_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pfda_add_partner_sku := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_forwarder_declaration_attribute` ADD COLUMN `partner_sku` VARCHAR(100) DEFAULT NULL AFTER `source_store_code`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_forwarder_declaration_attribute'
      AND COLUMN_NAME = 'partner_sku'
);
PREPARE stmt FROM @pfda_add_partner_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_forwarder_declaration_attribute` pfda
JOIN `procurement_shipping_order_line` sol
  ON sol.id = pfda.source_shipping_order_line_id
 AND sol.owner_user_id = pfda.owner_user_id
 AND sol.is_deleted = b'0'
SET pfda.logical_store_id = sol.logical_store_id,
    pfda.source_store_code = sol.source_store_code,
    pfda.partner_sku = sol.partner_sku,
    pfda.gmt_updated = NOW()
WHERE pfda.is_deleted = b'0'
  AND NULLIF(TRIM(sol.partner_sku), '') IS NOT NULL
  AND (
      pfda.logical_store_id IS NULL
      OR pfda.logical_store_id <> sol.logical_store_id
      OR NULLIF(TRIM(pfda.source_store_code), '') IS NULL
      OR TRIM(pfda.source_store_code) <> TRIM(sol.source_store_code)
      OR NULLIF(TRIM(pfda.partner_sku), '') IS NULL
      OR TRIM(pfda.partner_sku) <> TRIM(sol.partner_sku)
  );

SET @pfda_add_business_lookup_index := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_forwarder_declaration_attribute` ADD KEY `idx_pfda_owner_store_psku_attr` (`owner_user_id`, `source_store_code`, `partner_sku`, `forwarder_code`, `attribute_code`, `is_deleted`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_forwarder_declaration_attribute'
      AND INDEX_NAME = 'idx_pfda_owner_store_psku_attr'
);
PREPARE stmt FROM @pfda_add_business_lookup_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pfcq_add_logical_store_id := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_forwarder_channel_quote` ADD COLUMN `logical_store_id` BIGINT DEFAULT NULL AFTER `product_variant_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_forwarder_channel_quote'
      AND COLUMN_NAME = 'logical_store_id'
);
PREPARE stmt FROM @pfcq_add_logical_store_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pfcq_add_source_store_code := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_forwarder_channel_quote` ADD COLUMN `source_store_code` VARCHAR(100) DEFAULT NULL AFTER `logical_store_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_forwarder_channel_quote'
      AND COLUMN_NAME = 'source_store_code'
);
PREPARE stmt FROM @pfcq_add_source_store_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pfcq_add_partner_sku := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_forwarder_channel_quote` ADD COLUMN `partner_sku` VARCHAR(100) DEFAULT NULL AFTER `source_store_code`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_forwarder_channel_quote'
      AND COLUMN_NAME = 'partner_sku'
);
PREPARE stmt FROM @pfcq_add_partner_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_forwarder_channel_quote` pfcq
JOIN `procurement_shipping_order_line` sol
  ON sol.id = pfcq.source_shipping_order_line_id
 AND sol.owner_user_id = pfcq.owner_user_id
 AND sol.is_deleted = b'0'
SET pfcq.logical_store_id = sol.logical_store_id,
    pfcq.source_store_code = sol.source_store_code,
    pfcq.partner_sku = sol.partner_sku,
    pfcq.gmt_updated = NOW()
WHERE pfcq.is_deleted = b'0'
  AND NULLIF(TRIM(sol.partner_sku), '') IS NOT NULL
  AND (
      pfcq.logical_store_id IS NULL
      OR pfcq.logical_store_id <> sol.logical_store_id
      OR NULLIF(TRIM(pfcq.source_store_code), '') IS NULL
      OR TRIM(pfcq.source_store_code) <> TRIM(sol.source_store_code)
      OR NULLIF(TRIM(pfcq.partner_sku), '') IS NULL
      OR TRIM(pfcq.partner_sku) <> TRIM(sol.partner_sku)
  );

SET @pfcq_add_business_lookup_index := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_forwarder_channel_quote` ADD KEY `idx_pfcq_owner_store_psku_channel` (`owner_user_id`, `source_store_code`, `partner_sku`, `site_code`, `forwarder_code`, `route_code`, `service_code`, `billing_unit`, `effective_status`, `is_deleted`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_forwarder_channel_quote'
      AND INDEX_NAME = 'idx_pfcq_owner_store_psku_channel'
);
PREPARE stmt FROM @pfcq_add_business_lookup_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
