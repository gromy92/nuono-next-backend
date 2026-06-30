-- Product information listing-started timestamp stored at site offer grain.
-- Priority: missing PV/sales facts, earliest PV, earliest observed inventory, earliest sales, otherwise not listed.

SET @product_site_offer_add_listing_started_at := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'listing_started_at'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `listing_started_at` DATETIME DEFAULT NULL AFTER `status_code`'
    )
);
PREPARE stmt FROM @product_site_offer_add_listing_started_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_listing_started_source := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND COLUMN_NAME = 'listing_started_source'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD COLUMN `listing_started_source` VARCHAR(40) DEFAULT NULL AFTER `listing_started_at`'
    )
);
PREPARE stmt FROM @product_site_offer_add_listing_started_source;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @product_site_offer_add_listing_started_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_site_offer'
              AND INDEX_NAME = 'idx_product_site_offer_listing_started'
        ),
        'SELECT 1',
        'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_listing_started` (`site_id`, `listing_started_at`)'
    )
);
PREPARE stmt FROM @product_site_offer_add_listing_started_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE product_site_offer pso
JOIN product_variant pv
  ON pv.id = pso.variant_id
 AND pv.is_deleted = 0
JOIN product_master pm
  ON pm.id = pv.product_master_id
 AND pm.is_deleted = 0
JOIN logical_store_site lss
  ON lss.id = pso.site_id
 AND lss.is_deleted = 0
JOIN logical_store ls
  ON ls.id = lss.logical_store_id
 AND ls.is_deleted = 0
LEFT JOIN (
    SELECT owner_user_id, store_code, site_code, COUNT(1) AS site_fact_row_count
    FROM daily_sales_fact
    GROUP BY owner_user_id, store_code, site_code
) sitef
  ON sitef.owner_user_id = ls.owner_user_id
 AND sitef.store_code = lss.store_code
 AND sitef.site_code = lss.site
LEFT JOIN (
    SELECT owner_user_id, store_code, site_code, product_key, MIN(fact_date) AS first_pv_date
    FROM (
        SELECT owner_user_id, store_code, site_code, NULLIF(partner_sku, '') AS product_key, fact_date
        FROM daily_sales_fact
        WHERE COALESCE(your_visitors, 0) > 0 OR COALESCE(total_visitors, 0) > 0
        UNION ALL
        SELECT owner_user_id, store_code, site_code, NULLIF(sku, '') AS product_key, fact_date
        FROM daily_sales_fact
        WHERE COALESCE(your_visitors, 0) > 0 OR COALESCE(total_visitors, 0) > 0
    ) pv_source
    WHERE product_key IS NOT NULL
    GROUP BY owner_user_id, store_code, site_code, product_key
) pvf
  ON pvf.owner_user_id = ls.owner_user_id
 AND pvf.store_code = lss.store_code
 AND pvf.site_code = lss.site
 AND pvf.product_key = NULLIF(pv.partner_sku, '')
LEFT JOIN (
    SELECT owner_user_id, store_code, site_code, product_key, MIN(fact_date) AS first_sales_date
    FROM (
        SELECT owner_user_id, store_code, site_code, NULLIF(partner_sku, '') AS product_key, fact_date
        FROM daily_sales_fact
        WHERE COALESCE(net_units, 0) > 0
        UNION ALL
        SELECT owner_user_id, store_code, site_code, NULLIF(sku, '') AS product_key, fact_date
        FROM daily_sales_fact
        WHERE COALESCE(net_units, 0) > 0
    ) sales_source
    WHERE product_key IS NOT NULL
    GROUP BY owner_user_id, store_code, site_code, product_key
) sf
  ON sf.owner_user_id = ls.owner_user_id
 AND sf.store_code = lss.store_code
 AND sf.site_code = lss.site
 AND sf.product_key = NULLIF(pv.partner_sku, '')
SET pso.listing_started_at = CASE
      WHEN COALESCE(sitef.site_fact_row_count, 0) = 0 THEN NULL
      WHEN pvf.first_pv_date IS NOT NULL THEN CAST(pvf.first_pv_date AS DATETIME)
      WHEN COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0) > 0 THEN NOW()
      WHEN sf.first_sales_date IS NOT NULL THEN CAST(sf.first_sales_date AS DATETIME)
      ELSE NULL
    END,
    pso.listing_started_source = CASE
      WHEN COALESCE(sitef.site_fact_row_count, 0) = 0 THEN 'data_missing'
      WHEN pvf.first_pv_date IS NOT NULL THEN 'pv'
      WHEN COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0) > 0 THEN 'inventory'
      WHEN sf.first_sales_date IS NOT NULL THEN 'sales'
      ELSE 'not_listed'
    END,
    pso.gmt_updated = NOW()
WHERE pso.is_deleted = 0
  AND pso.listing_started_at IS NULL
  AND pso.listing_started_source IS NULL;
