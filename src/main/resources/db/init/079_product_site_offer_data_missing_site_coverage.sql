-- Reclassify legacy data_missing offer listing state only for explicitly confirmed
-- site coverage scopes. Insert reviewed owner/store/site rows into
-- product_site_offer_listing_coverage_scope before rerunning this migration.

CREATE TABLE IF NOT EXISTS `product_site_offer_listing_coverage_scope` (
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(64) NOT NULL,
    `site_code` VARCHAR(16) NOT NULL,
    `coverage_reason` VARCHAR(80) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`owner_user_id`, `store_code`, `site_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Confirmed site scopes for product listing-started migration';

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
 AND pm.logical_store_id = ls.id
JOIN product_site_offer_listing_coverage_scope scope
  ON scope.owner_user_id = ls.owner_user_id
 AND scope.store_code = lss.store_code
 AND scope.site_code = lss.site
SET pso.listing_started_at = CASE
        WHEN (
            SELECT MIN(dsf.fact_date)
            FROM daily_sales_fact dsf
            WHERE dsf.owner_user_id = ls.owner_user_id
              AND dsf.store_code = lss.store_code
              AND dsf.site_code = lss.site
              AND (
                  NULLIF(dsf.partner_sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
                  OR NULLIF(dsf.sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
              )
              AND (COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0)
        ) IS NOT NULL THEN CAST((
            SELECT MIN(dsf.fact_date)
            FROM daily_sales_fact dsf
            WHERE dsf.owner_user_id = ls.owner_user_id
              AND dsf.store_code = lss.store_code
              AND dsf.site_code = lss.site
              AND (
                  NULLIF(dsf.partner_sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
                  OR NULLIF(dsf.sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
              )
              AND (COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0)
        ) AS DATETIME)
        WHEN COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0) > 0
        THEN COALESCE(pso.last_synced_at, pso.gmt_updated, NOW())
        WHEN (
            SELECT MIN(dsf.fact_date)
            FROM daily_sales_fact dsf
            WHERE dsf.owner_user_id = ls.owner_user_id
              AND dsf.store_code = lss.store_code
              AND dsf.site_code = lss.site
              AND (
                  NULLIF(dsf.partner_sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
                  OR NULLIF(dsf.sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
              )
              AND COALESCE(dsf.net_units, 0) > 0
        ) IS NOT NULL THEN CAST((
            SELECT MIN(dsf.fact_date)
            FROM daily_sales_fact dsf
            WHERE dsf.owner_user_id = ls.owner_user_id
              AND dsf.store_code = lss.store_code
              AND dsf.site_code = lss.site
              AND (
                  NULLIF(dsf.partner_sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
                  OR NULLIF(dsf.sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
              )
              AND COALESCE(dsf.net_units, 0) > 0
        ) AS DATETIME)
        ELSE NULL
    END,
    pso.listing_started_source = CASE
        WHEN (
            SELECT MIN(dsf.fact_date)
            FROM daily_sales_fact dsf
            WHERE dsf.owner_user_id = ls.owner_user_id
              AND dsf.store_code = lss.store_code
              AND dsf.site_code = lss.site
              AND (
                  NULLIF(dsf.partner_sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
                  OR NULLIF(dsf.sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
              )
              AND (COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0)
        ) IS NOT NULL THEN 'pv'
        WHEN COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0) > 0 THEN 'inventory'
        WHEN (
            SELECT MIN(dsf.fact_date)
            FROM daily_sales_fact dsf
            WHERE dsf.owner_user_id = ls.owner_user_id
              AND dsf.store_code = lss.store_code
              AND dsf.site_code = lss.site
              AND (
                  NULLIF(dsf.partner_sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
                  OR NULLIF(dsf.sku, '') IN (
                      NULLIF(pv.partner_sku, ''),
                      NULLIF(pso.offer_code, ''),
                      NULLIF(pso.psku_code, ''),
                      NULLIF(pv.child_sku, ''),
                      NULLIF(pm.sku_parent, '')
                  )
              )
              AND COALESCE(dsf.net_units, 0) > 0
        ) IS NOT NULL THEN 'sales'
        ELSE 'not_listed'
    END,
    pso.gmt_updated = NOW()
WHERE pso.is_deleted = 0
  AND pso.listing_started_at IS NULL
  AND pso.listing_started_source = 'data_missing';
