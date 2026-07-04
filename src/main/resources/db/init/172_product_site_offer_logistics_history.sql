-- Store/product-level logistics history for logistics-new-product classification.
-- In-transit batches, successful ASN lines, Noon order history, and positive stock count as product history.

SET @pso_logistics_has_history_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'logistics_has_history'
);

SET @pso_logistics_has_history_ddl := IF(
    @pso_logistics_has_history_exists = 0,
    'ALTER TABLE `product_site_offer` ADD COLUMN `logistics_has_history` BIT(1) NOT NULL DEFAULT b''0'' AFTER `sales_currency`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_logistics_has_history_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_logistics_first_flow_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'logistics_first_flow_at'
);

SET @pso_logistics_first_flow_ddl := IF(
    @pso_logistics_first_flow_exists = 0,
    'ALTER TABLE `product_site_offer` ADD COLUMN `logistics_first_flow_at` DATETIME DEFAULT NULL AFTER `logistics_has_history`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_logistics_first_flow_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_logistics_last_flow_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'logistics_last_flow_at'
);

SET @pso_logistics_last_flow_ddl := IF(
    @pso_logistics_last_flow_exists = 0,
    'ALTER TABLE `product_site_offer` ADD COLUMN `logistics_last_flow_at` DATETIME DEFAULT NULL AFTER `logistics_first_flow_at`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_logistics_last_flow_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_logistics_history_source_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'logistics_history_source'
);

SET @pso_logistics_history_source_ddl := IF(
    @pso_logistics_history_source_exists = 0,
    'ALTER TABLE `product_site_offer` ADD COLUMN `logistics_history_source` VARCHAR(255) DEFAULT NULL AFTER `logistics_last_flow_at`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_logistics_history_source_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_logistics_history_source_length := (
    SELECT COALESCE(MAX(CHARACTER_MAXIMUM_LENGTH), 0)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'logistics_history_source'
);

SET @pso_logistics_history_source_resize_ddl := IF(
    @pso_logistics_history_source_exists > 0 AND @pso_logistics_history_source_length < 255,
    'ALTER TABLE `product_site_offer` MODIFY COLUMN `logistics_history_source` VARCHAR(255) DEFAULT NULL',
    'SELECT 1'
);

PREPARE stmt FROM @pso_logistics_history_source_resize_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_logistics_history_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND INDEX_NAME = 'idx_product_site_offer_logistics_history'
);

SET @pso_logistics_history_index_columns := (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND INDEX_NAME = 'idx_product_site_offer_logistics_history'
);

SET @pso_logistics_history_index_drop_ddl := IF(
    @pso_logistics_history_index_exists > 0
        AND @pso_logistics_history_index_columns <> 'logical_store_id,partner_sku,logistics_has_history',
    'ALTER TABLE `product_site_offer` DROP INDEX `idx_product_site_offer_logistics_history`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_logistics_history_index_drop_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_logistics_history_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND INDEX_NAME = 'idx_product_site_offer_logistics_history'
);

SET @pso_logistics_history_index_ddl := IF(
    @pso_logistics_history_index_exists = 0,
    'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_logistics_history` (`logical_store_id`, `partner_sku`, `logistics_has_history`)',
    'SELECT 1'
);

PREPARE stmt FROM @pso_logistics_history_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_site_offer` pso
JOIN (
    SELECT matched_history.product_site_offer_id,
           MIN(matched_history.first_flow_at) AS first_flow_at,
           MAX(matched_history.last_flow_at) AS last_flow_at,
           LEFT(GROUP_CONCAT(DISTINCT matched_history.source_codes ORDER BY matched_history.source_codes SEPARATOR ','), 255) AS source_codes
    FROM (
        SELECT pso_match.id AS product_site_offer_id,
               history.first_flow_at,
               history.last_flow_at,
               history.source_codes
        FROM `product_site_offer` pso_match
        JOIN (
            SELECT source.logical_store_id,
                   source.partner_sku_key,
                   MIN(source.first_flow_at) AS first_flow_at,
                   MAX(source.last_flow_at) AS last_flow_at,
                   LEFT(GROUP_CONCAT(DISTINCT source.source_code ORDER BY source.source_code SEPARATOR ','), 255) AS source_codes
            FROM (
        SELECT lss.logical_store_id,
               CONVERT(UPPER(TRIM(COALESCE(pv.partner_sku, NULLIF(TRIM(line.psku), ''), NULLIF(TRIM(line.sku), '')))) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,
               COALESCE(batch.source_created_at, line.gmt_create, batch.gmt_create) AS first_flow_at,
               COALESCE(line.gmt_updated, batch.gmt_updated, NOW()) AS last_flow_at,
               'IN_TRANSIT_GOODS_LINE' AS source_code
        FROM `in_transit_goods_line` line
        JOIN `in_transit_batch` batch
          ON batch.id = line.batch_id
         AND batch.owner_user_id = line.owner_user_id
         AND batch.is_deleted = b'0'
         AND COALESCE(batch.batch_status, '') <> 'cancelled'
        JOIN `logical_store_site` lss
          ON lss.is_deleted = b'0'
         AND CONVERT(UPPER(TRIM(lss.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
             = CONVERT(UPPER(TRIM(COALESCE(
                 NULLIF(TRIM(line.store_code), ''),
                 NULLIF(TRIM(batch.target_store_code), '')
             ))) USING utf8mb4) COLLATE utf8mb4_unicode_ci
        JOIN `logical_store` ls
          ON ls.id = lss.logical_store_id
         AND ls.owner_user_id = line.owner_user_id
         AND ls.is_deleted = b'0'
        LEFT JOIN `product_barcode` pb
          ON CONVERT(UPPER(TRIM(pb.barcode)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
             IN (
                 CONVERT(UPPER(TRIM(line.psku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci,
                 CONVERT(UPPER(TRIM(line.sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
             )
         AND pb.is_deleted = b'0'
        LEFT JOIN `product_variant` pv
          ON pv.id = pb.variant_id
         AND pv.logical_store_id = lss.logical_store_id
         AND pv.is_deleted = b'0'
        WHERE line.is_deleted = b'0'
          AND (
              (line.psku IS NOT NULL AND TRIM(line.psku) <> '')
              OR (line.sku IS NOT NULL AND TRIM(line.sku) <> '')
          )
          AND COALESCE(
                  NULLIF(TRIM(line.store_code), ''),
                  NULLIF(TRIM(batch.target_store_code), '')
              ) IS NOT NULL
        UNION ALL
        SELECT owner_unique_product.logical_store_id,
               raw_owner_in_transit.partner_sku_key,
               raw_owner_in_transit.first_flow_at,
               raw_owner_in_transit.last_flow_at,
               'IN_TRANSIT_GOODS_LINE' AS source_code
        FROM (
            SELECT line.owner_user_id,
                   CONVERT(UPPER(TRIM(line.psku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,
                   COALESCE(batch.source_created_at, line.gmt_create, batch.gmt_create) AS first_flow_at,
                   COALESCE(line.gmt_updated, batch.gmt_updated, NOW()) AS last_flow_at
            FROM `in_transit_goods_line` line
            JOIN `in_transit_batch` batch
              ON batch.id = line.batch_id
             AND batch.owner_user_id = line.owner_user_id
             AND batch.is_deleted = b'0'
             AND COALESCE(batch.batch_status, '') <> 'cancelled'
            WHERE line.is_deleted = b'0'
              AND line.psku IS NOT NULL
              AND TRIM(line.psku) <> ''
            UNION ALL
            SELECT line.owner_user_id,
                   CONVERT(UPPER(TRIM(line.sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,
                   COALESCE(batch.source_created_at, line.gmt_create, batch.gmt_create) AS first_flow_at,
                   COALESCE(line.gmt_updated, batch.gmt_updated, NOW()) AS last_flow_at
            FROM `in_transit_goods_line` line
            JOIN `in_transit_batch` batch
              ON batch.id = line.batch_id
             AND batch.owner_user_id = line.owner_user_id
             AND batch.is_deleted = b'0'
             AND COALESCE(batch.batch_status, '') <> 'cancelled'
            WHERE line.is_deleted = b'0'
              AND line.sku IS NOT NULL
              AND TRIM(line.sku) <> ''
            UNION ALL
            SELECT line.owner_user_id,
                   CONVERT(UPPER(TRIM(pv.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,
                   COALESCE(batch.source_created_at, line.gmt_create, batch.gmt_create) AS first_flow_at,
                   COALESCE(line.gmt_updated, batch.gmt_updated, NOW()) AS last_flow_at
            FROM `in_transit_goods_line` line
            JOIN `in_transit_batch` batch
              ON batch.id = line.batch_id
             AND batch.owner_user_id = line.owner_user_id
             AND batch.is_deleted = b'0'
             AND COALESCE(batch.batch_status, '') <> 'cancelled'
            JOIN `product_barcode` pb
              ON pb.is_deleted = b'0'
             AND CONVERT(UPPER(TRIM(pb.barcode)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
                 IN (
                     CONVERT(UPPER(TRIM(line.psku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci,
                     CONVERT(UPPER(TRIM(line.sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
                 )
            JOIN `product_variant` pv
              ON pv.id = pb.variant_id
             AND pv.is_deleted = b'0'
             AND pv.partner_sku IS NOT NULL
             AND TRIM(pv.partner_sku) <> ''
            JOIN `logical_store` pv_ls
              ON pv_ls.id = pv.logical_store_id
             AND pv_ls.owner_user_id = line.owner_user_id
             AND pv_ls.is_deleted = b'0'
            WHERE line.is_deleted = b'0'
        ) raw_owner_in_transit
        JOIN (
            SELECT owner_product.owner_user_id,
                   owner_product.partner_sku_key,
                   MIN(owner_product.logical_store_id) AS logical_store_id
            FROM (
                SELECT ls.owner_user_id,
                       pso.logical_store_id,
                       CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key
                FROM `product_site_offer` pso
                JOIN `logical_store` ls
                  ON ls.id = pso.logical_store_id
                 AND ls.is_deleted = b'0'
                WHERE pso.is_deleted = b'0'
                  AND pso.partner_sku IS NOT NULL
                  AND TRIM(pso.partner_sku) <> ''
                UNION ALL
                SELECT ls.owner_user_id,
                       pso.logical_store_id,
                       CONVERT(REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '') USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key
                FROM `product_site_offer` pso
                JOIN `logical_store` ls
                  ON ls.id = pso.logical_store_id
                 AND ls.is_deleted = b'0'
                WHERE pso.is_deleted = b'0'
                  AND pso.partner_sku IS NOT NULL
                  AND TRIM(pso.partner_sku) <> ''
                  AND CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP '[0-9]-[0-9]+$'
            ) owner_product
            GROUP BY owner_product.owner_user_id, owner_product.partner_sku_key
            HAVING COUNT(DISTINCT owner_product.logical_store_id) = 1
        ) owner_unique_product
          ON owner_unique_product.owner_user_id = raw_owner_in_transit.owner_user_id
         AND owner_unique_product.partner_sku_key = raw_owner_in_transit.partner_sku_key
        UNION ALL
        SELECT lss.logical_store_id,
               CONVERT(UPPER(TRIM(asn_line.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,
               COALESCE(asn.submitted_at, asn_line.gmt_create, asn.gmt_create) AS first_flow_at,
               COALESCE(asn.finished_at, asn_line.gmt_updated, asn.gmt_updated, NOW()) AS last_flow_at,
               'OFFICIAL_WAREHOUSE_ASN' AS source_code
        FROM `official_warehouse_asn_line` asn_line
        JOIN `official_warehouse_asn` asn
          ON asn.id = asn_line.asn_id
         AND asn.owner_user_id = asn_line.owner_user_id
         AND asn.is_deleted = b'0'
         AND asn.status NOT IN ('DRAFT', 'FAILED')
        JOIN `logical_store_site` lss
          ON lss.is_deleted = b'0'
         AND CONVERT(UPPER(TRIM(lss.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
             = CONVERT(UPPER(TRIM(asn_line.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
        JOIN `logical_store` ls
          ON ls.id = lss.logical_store_id
         AND ls.owner_user_id = asn_line.owner_user_id
         AND ls.is_deleted = b'0'
        WHERE asn_line.is_deleted = b'0'
          AND asn_line.line_status NOT IN ('PENDING', 'FAILED')
          AND asn_line.partner_sku IS NOT NULL
          AND TRIM(asn_line.partner_sku) <> ''
        UNION ALL
        SELECT lss.logical_store_id,
               CONVERT(UPPER(TRIM(order_line.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,
               COALESCE(order_line.order_timestamp, CAST(order_line.report_date_from AS DATETIME), order_line.gmt_create) AS first_flow_at,
               COALESCE(order_line.delivered_timestamp, order_line.shipment_timestamp, order_line.order_timestamp, CAST(order_line.report_date_to AS DATETIME), order_line.gmt_updated, NOW()) AS last_flow_at,
               'NOON_ORDER_LINE_FACT' AS source_code
        FROM `noon_order_line_fact` order_line
        JOIN `logical_store_site` lss
          ON lss.is_deleted = b'0'
         AND CONVERT(UPPER(TRIM(lss.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
             = CONVERT(UPPER(TRIM(order_line.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
        JOIN `logical_store` ls
          ON ls.id = lss.logical_store_id
         AND ls.owner_user_id = order_line.owner_user_id
         AND ls.is_deleted = b'0'
        WHERE order_line.partner_sku IS NOT NULL
          AND TRIM(order_line.partner_sku) <> ''
        UNION ALL
        SELECT stock_offer.logical_store_id,
               CONVERT(UPPER(TRIM(stock_offer.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,
               COALESCE(stock_offer.last_synced_at, stock_offer.gmt_updated, stock_offer.gmt_create) AS first_flow_at,
               COALESCE(stock_offer.last_synced_at, stock_offer.gmt_updated, NOW()) AS last_flow_at,
               'PRODUCT_SITE_OFFER_STOCK' AS source_code
        FROM `product_site_offer` stock_offer
        WHERE stock_offer.is_deleted = b'0'
          AND stock_offer.partner_sku IS NOT NULL
          AND TRIM(stock_offer.partner_sku) <> ''
          AND COALESCE(stock_offer.fbn_stock, 0) + COALESCE(stock_offer.supermall_stock, 0) + COALESCE(stock_offer.fbp_stock, 0) > 0
        UNION ALL
        SELECT inventory.logical_store_id,
               CONVERT(UPPER(TRIM(inventory.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,
               COALESCE(inventory.inventory_snapshot_at, inventory.gmt_create) AS first_flow_at,
               COALESCE(inventory.inventory_snapshot_at, inventory.gmt_updated, NOW()) AS last_flow_at,
               'OFFICIAL_WAREHOUSE_INVENTORY' AS source_code
        FROM `official_warehouse_inventory_snapshot_line` inventory
        WHERE inventory.is_deleted = b'0'
          AND inventory.is_current = b'1'
          AND inventory.partner_sku IS NOT NULL
          AND TRIM(inventory.partner_sku) <> ''
          AND COALESCE(inventory.qty, 0) > 0
            ) source
            GROUP BY source.logical_store_id, source.partner_sku_key
        ) history
          ON history.logical_store_id = pso_match.logical_store_id
         AND (
                history.partner_sku_key = CONVERT(UPPER(TRIM(pso_match.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci
                OR (
                    CONVERT(UPPER(TRIM(pso_match.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP '[0-9]-[0-9]+$'
                    AND history.partner_sku_key = CONVERT(REGEXP_REPLACE(UPPER(TRIM(pso_match.partner_sku)), '-[0-9]+$', '') USING utf8mb4) COLLATE utf8mb4_unicode_ci
                )
             )
        WHERE pso_match.is_deleted = b'0'
          AND pso_match.partner_sku IS NOT NULL
          AND TRIM(pso_match.partner_sku) <> ''
    ) matched_history
    GROUP BY matched_history.product_site_offer_id
) history
  ON history.product_site_offer_id = pso.id
SET pso.logistics_has_history = b'1',
    pso.logistics_first_flow_at = CASE
        WHEN pso.logistics_first_flow_at IS NULL THEN COALESCE(history.first_flow_at, NOW())
        WHEN history.first_flow_at IS NULL THEN pso.logistics_first_flow_at
        WHEN pso.logistics_first_flow_at > history.first_flow_at THEN history.first_flow_at
        ELSE pso.logistics_first_flow_at
    END,
    pso.logistics_last_flow_at = CASE
        WHEN pso.logistics_last_flow_at IS NULL THEN COALESCE(history.last_flow_at, NOW())
        WHEN history.last_flow_at IS NULL THEN pso.logistics_last_flow_at
        WHEN pso.logistics_last_flow_at < history.last_flow_at THEN history.last_flow_at
        ELSE pso.logistics_last_flow_at
    END,
    pso.logistics_history_source = history.source_codes,
    pso.gmt_updated = NOW()
WHERE pso.is_deleted = b'0'
  AND pso.partner_sku IS NOT NULL
  AND TRIM(pso.partner_sku) <> '';
