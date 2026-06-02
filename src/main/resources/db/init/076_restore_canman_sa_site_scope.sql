-- Restore canman / PRJ108065 SA into the local owner store scope.
--
-- The local acceptance store scope for owner 307 lost STR108065-NSA while
-- daily_sales_fact still contains verified SA facts. Product projection then
-- treated the SA logical site as stale and soft-deleted it. Keep both layers
-- aligned so future local bootstraps and projection runs preserve the SA site.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET NAMES utf8mb4;

SET @canman_owner_user_id := 307;
SET @canman_operator_id := 10003;
SET @canman_project_code := 'PRJ108065';
SET @canman_project_name_default := 'canman';
SET @canman_sa_store_code := 'STR108065-NSA';
SET @canman_sa_site := 'SA';

SET @canman_org_code := COALESCE(
    (
        SELECT us.org_code
        FROM user_store us
        WHERE us.user_id = @canman_owner_user_id
          AND BINARY us.project_code = BINARY @canman_project_code
          AND NULLIF(us.org_code, '') IS NOT NULL
        ORDER BY us.is_deleted ASC, us.id ASC
        LIMIT 1
    ),
    'ORG1GYXDUP'
);

SET @canman_org_name := COALESCE(
    (
        SELECT us.org_name
        FROM user_store us
        WHERE us.user_id = @canman_owner_user_id
          AND BINARY us.project_code = BINARY @canman_project_code
          AND NULLIF(us.org_name, '') IS NOT NULL
        ORDER BY us.is_deleted ASC, us.id ASC
        LIMIT 1
    ),
    @canman_project_name_default
);

SET @canman_project_name := COALESCE(
    (
        SELECT up.project_name
        FROM user_project up
        WHERE up.user_id = @canman_owner_user_id
          AND BINARY up.project_code = BINARY @canman_project_code
          AND NULLIF(up.project_name, '') IS NOT NULL
        ORDER BY up.is_deleted ASC, up.id ASC
        LIMIT 1
    ),
    (
        SELECT us.project_name
        FROM user_store us
        WHERE us.user_id = @canman_owner_user_id
          AND BINARY us.project_code = BINARY @canman_project_code
          AND NULLIF(us.project_name, '') IS NOT NULL
        ORDER BY us.is_deleted ASC, us.id ASC
        LIMIT 1
    ),
    @canman_project_name_default
);

UPDATE user_project
SET project_name = COALESCE(NULLIF(project_name, ''), @canman_project_name),
    org_code = COALESCE(NULLIF(org_code, ''), @canman_org_code),
    org_name = COALESCE(NULLIF(org_name, ''), @canman_org_name),
    bind_status = 1,
    is_authorized = 1,
    is_deleted = 0,
    updated_by = @canman_operator_id,
    gmt_updated = NOW()
WHERE user_id = @canman_owner_user_id
  AND BINARY project_code = BINARY @canman_project_code;

UPDATE user_store
SET org_code = @canman_org_code,
    org_name = @canman_org_name,
    project_name = @canman_project_name,
    store_code = @canman_sa_store_code,
    site = @canman_sa_site,
    is_authorized = 1,
    is_deleted = 0,
    updated_by = @canman_operator_id,
    gmt_updated = NOW()
WHERE user_id = @canman_owner_user_id
  AND BINARY project_code = BINARY @canman_project_code
  AND (BINARY store_code = BINARY @canman_sa_store_code OR BINARY site = BINARY @canman_sa_site);

INSERT INTO user_store (
    user_id, org_code, org_name, project_code, project_name, store_code, site,
    is_authorized, is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
    @canman_owner_user_id, @canman_org_code, @canman_org_name,
    @canman_project_code, @canman_project_name, @canman_sa_store_code, @canman_sa_site,
    1, 0, @canman_operator_id, @canman_operator_id, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM user_store existing
    WHERE existing.user_id = @canman_owner_user_id
      AND BINARY existing.project_code = BINARY @canman_project_code
      AND BINARY existing.store_code = BINARY @canman_sa_store_code
);

INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
SELECT 'logical_store', GREATEST(COALESCE(MAX(id), 50000), 50000), NOW(), NOW()
FROM logical_store
ON DUPLICATE KEY UPDATE
    next_id = GREATEST(next_id, VALUES(next_id)),
    gmt_updated = NOW();

INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
SELECT 'logical_store_site', GREATEST(COALESCE(MAX(id), 51000), 51000), NOW(), NOW()
FROM logical_store_site
ON DUPLICATE KEY UPDATE
    next_id = GREATEST(next_id, VALUES(next_id)),
    gmt_updated = NOW();

UPDATE logical_store
SET project_name = @canman_project_name,
    status = 'ACTIVE',
    is_deleted = b'0',
    updated_by = @canman_operator_id,
    gmt_updated = NOW()
WHERE owner_user_id = @canman_owner_user_id
  AND BINARY project_code = BINARY @canman_project_code;

SET @canman_logical_store_id := (
    SELECT ls.id
    FROM logical_store ls
    WHERE ls.owner_user_id = @canman_owner_user_id
      AND BINARY ls.project_code = BINARY @canman_project_code
    ORDER BY ls.is_deleted ASC, ls.id ASC
    LIMIT 1
);

SET @new_canman_logical_store_id := (
    SELECT GREATEST(
        COALESCE((SELECT seq.next_id FROM product_management_id_sequence seq WHERE seq.sequence_name = 'logical_store'), 50000),
        COALESCE((SELECT MAX(ls.id) FROM logical_store ls), 50000),
        50000
    ) + 1
);

INSERT INTO logical_store (
    id, owner_user_id, manager_user_id, project_code, project_name, status,
    is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
    @new_canman_logical_store_id, @canman_owner_user_id, NULL,
    @canman_project_code, @canman_project_name, 'ACTIVE',
    b'0', @canman_operator_id, @canman_operator_id, NOW(), NOW()
WHERE @canman_logical_store_id IS NULL;

SET @max_logical_store_id := (SELECT GREATEST(COALESCE(MAX(id), 50000), 50000) FROM logical_store);
UPDATE product_management_id_sequence
SET next_id = GREATEST(next_id, @max_logical_store_id),
    gmt_updated = NOW()
WHERE sequence_name = 'logical_store';

SET @canman_logical_store_id := (
    SELECT ls.id
    FROM logical_store ls
    WHERE ls.owner_user_id = @canman_owner_user_id
      AND BINARY ls.project_code = BINARY @canman_project_code
    ORDER BY ls.is_deleted ASC, ls.id ASC
    LIMIT 1
);

UPDATE logical_store_site
SET store_code = @canman_sa_store_code,
    site = @canman_sa_site,
    is_reference_site = b'0',
    is_mounted = b'1',
    site_status = 'ACTIVE',
    is_deleted = b'0',
    updated_by = @canman_operator_id,
    gmt_updated = NOW()
WHERE logical_store_id = @canman_logical_store_id
  AND (BINARY store_code = BINARY @canman_sa_store_code OR BINARY site = BINARY @canman_sa_site);

SET @canman_sa_site_id := (
    SELECT lss.id
    FROM logical_store_site lss
    WHERE lss.logical_store_id = @canman_logical_store_id
      AND (BINARY lss.store_code = BINARY @canman_sa_store_code OR BINARY lss.site = BINARY @canman_sa_site)
    ORDER BY lss.is_deleted ASC, lss.id ASC
    LIMIT 1
);

SET @new_canman_sa_site_id := (
    SELECT GREATEST(
        COALESCE((SELECT seq.next_id FROM product_management_id_sequence seq WHERE seq.sequence_name = 'logical_store_site'), 51000),
        COALESCE((SELECT MAX(lss.id) FROM logical_store_site lss), 51000),
        51000
    ) + 1
);

INSERT INTO logical_store_site (
    id, logical_store_id, store_code, site, is_reference_site, is_mounted, site_status,
    is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
    @new_canman_sa_site_id, @canman_logical_store_id,
    @canman_sa_store_code, @canman_sa_site, b'0', b'1', 'ACTIVE',
    b'0', @canman_operator_id, @canman_operator_id, NOW(), NOW()
WHERE @canman_logical_store_id IS NOT NULL
  AND @canman_sa_site_id IS NULL;

SET @max_logical_store_site_id := (SELECT GREATEST(COALESCE(MAX(id), 51000), 51000) FROM logical_store_site);
UPDATE product_management_id_sequence
SET next_id = GREATEST(next_id, @max_logical_store_site_id),
    gmt_updated = NOW()
WHERE sequence_name = 'logical_store_site';

INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
SELECT 'product_site_offer', GREATEST(COALESCE(MAX(id), 55000), 55000), NOW(), NOW()
FROM product_site_offer
ON DUPLICATE KEY UPDATE
    next_id = GREATEST(next_id, VALUES(next_id)),
    gmt_updated = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_canman_sa_sales_offer_backfill;
CREATE TEMPORARY TABLE tmp_canman_sa_sales_offer_backfill AS
SELECT
    pv.id AS variant_id,
    @canman_sa_site_id AS site_id,
    pv.partner_sku AS psku_code,
    COALESCE(NULLIF(pv.child_sku, ''), NULLIF(MAX(dsf.sku), ''), NULLIF(MAX(dsf.partner_sku), ''), pv.partner_sku) AS offer_code,
    COALESCE(NULLIF(MAX(dsf.currency_code), ''), 'SAR') AS currency,
    MIN(CASE
        WHEN COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0
        THEN dsf.fact_date
        ELSE NULL
    END) AS first_pv_date,
    MIN(CASE
        WHEN COALESCE(dsf.net_units, 0) > 0 THEN dsf.fact_date
        ELSE NULL
    END) AS first_sales_date,
    SUM(COALESCE(dsf.your_visitors, 0)) AS views_count,
    SUM(COALESCE(dsf.net_units, 0)) AS units_sold,
    SUM(COALESCE(dsf.revenue_shipped, 0)) AS sales_amount,
    MAX(dsf.fact_date) AS last_fact_date
FROM product_variant pv
JOIN product_master pm
  ON pm.id = pv.product_master_id
 AND pm.logical_store_id = @canman_logical_store_id
 AND pm.is_deleted = b'0'
JOIN daily_sales_fact dsf
  ON dsf.owner_user_id = @canman_owner_user_id
 AND dsf.store_code = @canman_sa_store_code
 AND dsf.site_code = @canman_sa_site
 AND (
    NULLIF(dsf.partner_sku, '') = pv.partner_sku
    OR NULLIF(dsf.sku, '') = pv.partner_sku
    OR NULLIF(dsf.partner_sku, '') = pv.child_sku
    OR NULLIF(dsf.sku, '') = pv.child_sku
 )
WHERE @canman_sa_site_id IS NOT NULL
  AND pv.is_deleted = b'0'
GROUP BY pv.id, pv.partner_sku, pv.child_sku;

SET @canman_sa_product_site_offer_next_id := (
    SELECT GREATEST(
        COALESCE((SELECT seq.next_id FROM product_management_id_sequence seq WHERE seq.sequence_name = 'product_site_offer'), 55000),
        COALESCE((SELECT MAX(pso.id) FROM product_site_offer pso), 55000),
        55000
    )
);

INSERT INTO product_site_offer (
    id, variant_id, site_id, psku_code, offer_code, currency,
    is_active, live_status, status_code,
    listing_started_at, listing_started_source,
    views_count, units_sold, sales_amount, sales_currency, last_synced_at,
    is_deleted, created_by, updated_by, gmt_create, gmt_updated
)
SELECT
    @canman_sa_product_site_offer_next_id := @canman_sa_product_site_offer_next_id + 1 AS id,
    backfill.variant_id,
    backfill.site_id,
    backfill.psku_code,
    backfill.offer_code,
    backfill.currency,
    NULL,
    'sales_fact_backfill',
    'sales_fact_backfill',
    CASE
        WHEN backfill.first_pv_date IS NOT NULL THEN CAST(backfill.first_pv_date AS DATETIME)
        WHEN backfill.first_sales_date IS NOT NULL THEN CAST(backfill.first_sales_date AS DATETIME)
        ELSE NULL
    END,
    CASE
        WHEN backfill.first_pv_date IS NOT NULL THEN 'pv'
        WHEN backfill.first_sales_date IS NOT NULL THEN 'sales'
        ELSE 'not_listed'
    END,
    backfill.views_count,
    backfill.units_sold,
    backfill.sales_amount,
    backfill.currency,
    CAST(backfill.last_fact_date AS DATETIME),
    b'0',
    @canman_operator_id,
    @canman_operator_id,
    NOW(),
    NOW()
FROM tmp_canman_sa_sales_offer_backfill backfill
LEFT JOIN product_site_offer active_offer
  ON active_offer.variant_id = backfill.variant_id
 AND active_offer.site_id = backfill.site_id
 AND active_offer.is_deleted = b'0'
WHERE active_offer.id IS NULL
ORDER BY backfill.variant_id
ON DUPLICATE KEY UPDATE
    psku_code = VALUES(psku_code),
    offer_code = VALUES(offer_code),
    currency = VALUES(currency),
    live_status = VALUES(live_status),
    status_code = VALUES(status_code),
    listing_started_at = CASE
        WHEN product_site_offer.listing_started_at IS NULL THEN VALUES(listing_started_at)
        ELSE product_site_offer.listing_started_at
    END,
    listing_started_source = CASE
        WHEN product_site_offer.listing_started_at IS NULL
          OR product_site_offer.listing_started_source IS NULL
          OR product_site_offer.listing_started_source IN ('data_missing', 'not_listed')
        THEN VALUES(listing_started_source)
        ELSE product_site_offer.listing_started_source
    END,
    views_count = VALUES(views_count),
    units_sold = VALUES(units_sold),
    sales_amount = VALUES(sales_amount),
    sales_currency = VALUES(sales_currency),
    last_synced_at = VALUES(last_synced_at),
    is_deleted = b'0',
    updated_by = VALUES(updated_by),
    gmt_updated = NOW();

SET @max_product_site_offer_id := (SELECT GREATEST(COALESCE(MAX(id), 55000), 55000) FROM product_site_offer);
UPDATE product_management_id_sequence
SET next_id = GREATEST(next_id, @max_product_site_offer_id, @canman_sa_product_site_offer_next_id),
    gmt_updated = NOW()
WHERE sequence_name = 'product_site_offer';

DROP TEMPORARY TABLE IF EXISTS tmp_canman_sa_sales_offer_backfill;
