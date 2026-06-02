-- Restore remaining owner 307 legacy product-sales facts into local daily_sales_fact.
--
-- This script reads a local staging table loaded from the legacy production
-- cross_border_erp.product_sales_data export. It is intentionally scoped to
-- owner 307 and the active local store-site rows represented in the staging
-- file. It does not require the legacy database to be present locally.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET NAMES utf8mb4;

SET @owner_user_id := 307;
SET @operator_id := 10003;
SET @legacy_source_system := 'legacy_product_sales_data';

CREATE TABLE IF NOT EXISTS legacy_owner307_product_sales_stage (
    owner_user_id BIGINT NOT NULL,
    project_code VARCHAR(80) NOT NULL,
    store_code VARCHAR(80) NOT NULL,
    site_code VARCHAR(20) NOT NULL,
    legacy_data_user_id BIGINT NOT NULL,
    legacy_row_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    psku_hex VARCHAR(400) NOT NULL,
    sku_hex VARCHAR(800) NOT NULL,
    sku_config_hex VARCHAR(400) DEFAULT NULL,
    country_code_hex VARCHAR(80) DEFAULT NULL,
    currency_code_hex VARCHAR(80) DEFAULT NULL,
    product_title_hex TEXT DEFAULT NULL,
    your_visitors_text VARCHAR(80) DEFAULT NULL,
    total_visitors_text VARCHAR(80) DEFAULT NULL,
    gross_units_text VARCHAR(80) DEFAULT NULL,
    shipped_units_text VARCHAR(80) DEFAULT NULL,
    cancelled_units_text VARCHAR(80) DEFAULT NULL,
    revenue_shipped_text VARCHAR(80) DEFAULT NULL,
    buy_box_visitor_percentage_text VARCHAR(80) DEFAULT NULL,
    conversion_visitors_percentage_text VARCHAR(80) DEFAULT NULL,
    asp_shipped_percentage_text VARCHAR(80) DEFAULT NULL,
    imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (legacy_row_id),
    KEY idx_owner307_legacy_sales_scope (owner_user_id, store_code, site_code, visit_date),
    KEY idx_owner307_legacy_sales_psku (psku_hex)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sales_data_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
SELECT 'sales_import_batch', GREATEST(COALESCE(MAX(id), 10000), 10000), NOW(), NOW()
FROM sales_import_batch
ON DUPLICATE KEY UPDATE
    next_id = GREATEST(next_id, VALUES(next_id)),
    gmt_updated = NOW();

INSERT INTO sales_data_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
SELECT 'daily_sales_fact', GREATEST(COALESCE(MAX(id), 100000), 100000), NOW(), NOW()
FROM daily_sales_fact
ON DUPLICATE KEY UPDATE
    next_id = GREATEST(next_id, VALUES(next_id)),
    gmt_updated = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_owner307_legacy_sales_source;
CREATE TEMPORARY TABLE tmp_owner307_legacy_sales_source AS
SELECT
    stage.legacy_row_id,
    stage.legacy_data_user_id,
    stage.owner_user_id,
    ls.id AS logical_store_id,
    stage.project_code,
    stage.store_code,
    stage.site_code,
    stage.visit_date AS fact_date,
    CAST(UNHEX(NULLIF(stage.psku_hex, '')) AS CHAR CHARACTER SET utf8mb4) AS partner_sku,
    CAST(UNHEX(NULLIF(stage.sku_hex, '')) AS CHAR CHARACTER SET utf8mb4) AS sku,
    CAST(UNHEX(NULLIF(stage.sku_config_hex, '')) AS CHAR CHARACTER SET utf8mb4) AS sku_config,
    CAST(UNHEX(NULLIF(stage.country_code_hex, '')) AS CHAR CHARACTER SET utf8mb4) AS country_code,
    CAST(UNHEX(NULLIF(stage.currency_code_hex, '')) AS CHAR CHARACTER SET utf8mb4) AS currency_code,
    CAST(UNHEX(NULLIF(stage.product_title_hex, '')) AS CHAR CHARACTER SET utf8mb4) AS product_title,
    CAST(NULLIF(stage.your_visitors_text, '') AS SIGNED) AS your_visitors,
    CAST(NULLIF(stage.total_visitors_text, '') AS SIGNED) AS total_visitors,
    CAST(NULLIF(stage.gross_units_text, '') AS SIGNED) AS gross_units,
    CAST(NULLIF(stage.shipped_units_text, '') AS SIGNED) AS shipped_units,
    CAST(NULLIF(stage.cancelled_units_text, '') AS SIGNED) AS cancelled_units,
    GREATEST(
        GREATEST(
            COALESCE(CAST(NULLIF(stage.gross_units_text, '') AS SIGNED), 0)
                - COALESCE(CAST(NULLIF(stage.cancelled_units_text, '') AS SIGNED), 0),
            COALESCE(CAST(NULLIF(stage.shipped_units_text, '') AS SIGNED), 0)
        ),
        0
    ) AS net_units,
    CAST(NULLIF(stage.revenue_shipped_text, '') AS DECIMAL(18,6)) AS revenue_shipped,
    CAST(NULLIF(stage.buy_box_visitor_percentage_text, '') AS DECIMAL(10,4)) AS buy_box_visitor_percentage,
    CAST(NULLIF(stage.conversion_visitors_percentage_text, '') AS DECIMAL(10,4)) AS conversion_visitors_percentage,
    CAST(NULLIF(stage.asp_shipped_percentage_text, '') AS DECIMAL(18,6)) AS asp_shipped_percentage
FROM legacy_owner307_product_sales_stage stage
JOIN user_store us
  ON us.user_id = stage.owner_user_id
 AND BINARY us.project_code = BINARY stage.project_code
 AND BINARY us.store_code = BINARY stage.store_code
 AND BINARY us.site = BINARY stage.site_code
 AND us.is_deleted = 0
 AND us.is_authorized = 1
JOIN logical_store ls
  ON ls.owner_user_id = stage.owner_user_id
 AND BINARY ls.project_code = BINARY stage.project_code
 AND ls.is_deleted = b'0'
JOIN logical_store_site lss
  ON lss.logical_store_id = ls.id
 AND BINARY lss.store_code = BINARY stage.store_code
 AND BINARY lss.site = BINARY stage.site_code
 AND lss.is_deleted = b'0'
WHERE stage.owner_user_id = @owner_user_id
  AND stage.visit_date IS NOT NULL
  AND NULLIF(stage.psku_hex, '') IS NOT NULL
  AND NULLIF(stage.sku_hex, '') IS NOT NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_owner307_legacy_sales_batch_candidate;
CREATE TEMPORARY TABLE tmp_owner307_legacy_sales_batch_candidate AS
SELECT
    src.owner_user_id,
    src.logical_store_id,
    src.project_code,
    src.store_code,
    src.site_code,
    src.legacy_data_user_id,
    CONCAT(
        'legacy_owner307_product_sales_data;legacy_user=', src.legacy_data_user_id,
        ';owner=', src.owner_user_id,
        ';store=', src.store_code,
        ';site=', src.site_code
    ) AS source_filename,
    MIN(src.fact_date) AS report_date_from,
    MAX(src.fact_date) AS report_date_to,
    COUNT(*) AS total_rows
FROM tmp_owner307_legacy_sales_source src
GROUP BY
    src.owner_user_id,
    src.logical_store_id,
    src.project_code,
    src.store_code,
    src.site_code,
    src.legacy_data_user_id;

SET @owner307_legacy_batch_next_id := (
    SELECT GREATEST(
        COALESCE((SELECT seq.next_id FROM sales_data_id_sequence seq WHERE seq.sequence_name = 'sales_import_batch'), 10000),
        COALESCE((SELECT MAX(sib.id) FROM sales_import_batch sib), 10000),
        10000
    )
);

DROP TEMPORARY TABLE IF EXISTS tmp_owner307_legacy_sales_batch;
CREATE TEMPORARY TABLE tmp_owner307_legacy_sales_batch AS
SELECT
    CASE
        WHEN existing.id IS NOT NULL THEN existing.id
        ELSE @owner307_legacy_batch_next_id := @owner307_legacy_batch_next_id + 1
    END AS batch_id,
    candidate.*
FROM (
    SELECT *
    FROM tmp_owner307_legacy_sales_batch_candidate
    ORDER BY owner_user_id, store_code, site_code
) candidate
LEFT JOIN sales_import_batch existing
  ON BINARY existing.source_system = BINARY @legacy_source_system
 AND BINARY existing.source_filename = BINARY candidate.source_filename
 AND existing.owner_user_id = candidate.owner_user_id
 AND BINARY existing.store_code = BINARY candidate.store_code
 AND BINARY existing.site_code = BINARY candidate.site_code;

INSERT INTO sales_import_batch (
    id,
    source_system,
    source_filename,
    owner_user_id,
    logical_store_id,
    store_code,
    site_code,
    report_date_from,
    report_date_to,
    total_rows,
    success_rows,
    failure_rows,
    status,
    failure_summary_json,
    imported_at,
    gmt_create,
    gmt_updated
)
SELECT
    batch.batch_id,
    @legacy_source_system,
    batch.source_filename,
    batch.owner_user_id,
    batch.logical_store_id,
    batch.store_code,
    batch.site_code,
    batch.report_date_from,
    batch.report_date_to,
    batch.total_rows,
    batch.total_rows,
    0,
    IF(batch.total_rows = 0, 'empty', 'imported'),
    NULL,
    NOW(),
    NOW(),
    NOW()
FROM tmp_owner307_legacy_sales_batch batch
LEFT JOIN sales_import_batch existing
  ON existing.id = batch.batch_id
WHERE existing.id IS NULL
  AND batch.total_rows > 0;

UPDATE sales_import_batch existing
JOIN tmp_owner307_legacy_sales_batch batch
  ON batch.batch_id = existing.id
SET existing.logical_store_id = batch.logical_store_id,
    existing.report_date_from = batch.report_date_from,
    existing.report_date_to = batch.report_date_to,
    existing.total_rows = batch.total_rows,
    existing.success_rows = batch.total_rows,
    existing.failure_rows = 0,
    existing.status = IF(batch.total_rows = 0, 'empty', 'imported'),
    existing.failure_summary_json = NULL,
    existing.imported_at = NOW(),
    existing.gmt_updated = NOW();

SET @max_sales_import_batch_id := (SELECT GREATEST(COALESCE(MAX(id), 10000), 10000) FROM sales_import_batch);
UPDATE sales_data_id_sequence
SET next_id = GREATEST(next_id, @max_sales_import_batch_id),
    gmt_updated = NOW()
WHERE sequence_name = 'sales_import_batch';

SET @owner307_legacy_fact_next_id := (
    SELECT GREATEST(
        COALESCE((SELECT seq.next_id FROM sales_data_id_sequence seq WHERE seq.sequence_name = 'daily_sales_fact'), 100000),
        COALESCE((SELECT MAX(dsf.id) FROM daily_sales_fact dsf), 100000),
        100000
    )
);

INSERT INTO daily_sales_fact (
    id,
    source_system,
    source_batch_id,
    owner_user_id,
    logical_store_id,
    store_code,
    site_code,
    fact_date,
    partner_sku,
    sku,
    sku_config,
    country_code,
    currency_code,
    product_title,
    your_visitors,
    total_visitors,
    gross_units,
    shipped_units,
    cancelled_units,
    net_units,
    revenue_shipped,
    buy_box_visitor_percentage,
    conversion_visitors_percentage,
    asp_shipped_percentage,
    source_row_hash,
    gmt_create,
    gmt_updated
)
SELECT
    @owner307_legacy_fact_next_id := @owner307_legacy_fact_next_id + 1 AS id,
    @legacy_source_system,
    batch.batch_id,
    src.owner_user_id,
    src.logical_store_id,
    src.store_code,
    src.site_code,
    src.fact_date,
    src.partner_sku,
    src.sku,
    src.sku_config,
    src.country_code,
    src.currency_code,
    src.product_title,
    src.your_visitors,
    src.total_visitors,
    src.gross_units,
    src.shipped_units,
    src.cancelled_units,
    src.net_units,
    src.revenue_shipped,
    src.buy_box_visitor_percentage,
    src.conversion_visitors_percentage,
    src.asp_shipped_percentage,
    SHA2(CONCAT_WS('|',
        src.legacy_row_id,
        src.legacy_data_user_id,
        src.store_code,
        src.site_code,
        src.fact_date,
        src.partner_sku,
        src.sku,
        COALESCE(src.gross_units, ''),
        COALESCE(src.shipped_units, ''),
        COALESCE(src.cancelled_units, ''),
        COALESCE(src.revenue_shipped, '')
    ), 256),
    NOW(),
    NOW()
FROM tmp_owner307_legacy_sales_source src
JOIN tmp_owner307_legacy_sales_batch batch
  ON batch.owner_user_id = src.owner_user_id
 AND BINARY batch.store_code = BINARY src.store_code
 AND BINARY batch.site_code = BINARY src.site_code
ORDER BY src.store_code, src.site_code, src.legacy_row_id
ON DUPLICATE KEY UPDATE
    source_batch_id = VALUES(source_batch_id),
    logical_store_id = VALUES(logical_store_id),
    sku_config = VALUES(sku_config),
    country_code = VALUES(country_code),
    currency_code = VALUES(currency_code),
    product_title = VALUES(product_title),
    your_visitors = VALUES(your_visitors),
    total_visitors = VALUES(total_visitors),
    gross_units = VALUES(gross_units),
    shipped_units = VALUES(shipped_units),
    cancelled_units = VALUES(cancelled_units),
    net_units = VALUES(net_units),
    revenue_shipped = VALUES(revenue_shipped),
    buy_box_visitor_percentage = VALUES(buy_box_visitor_percentage),
    conversion_visitors_percentage = VALUES(conversion_visitors_percentage),
    asp_shipped_percentage = VALUES(asp_shipped_percentage),
    source_row_hash = VALUES(source_row_hash),
    gmt_updated = NOW();

SET @max_daily_sales_fact_id := (SELECT GREATEST(COALESCE(MAX(id), 100000), 100000) FROM daily_sales_fact);
UPDATE sales_data_id_sequence
SET next_id = GREATEST(next_id, @max_daily_sales_fact_id),
    gmt_updated = NOW()
WHERE sequence_name = 'daily_sales_fact';

INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
SELECT 'product_site_offer', GREATEST(COALESCE(MAX(id), 55000), 55000), NOW(), NOW()
FROM product_site_offer
ON DUPLICATE KEY UPDATE
    next_id = GREATEST(next_id, VALUES(next_id)),
    gmt_updated = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_owner307_migrated_scope;
CREATE TEMPORARY TABLE tmp_owner307_migrated_scope AS
SELECT DISTINCT
    src.owner_user_id,
    src.logical_store_id,
    src.store_code,
    src.site_code
FROM tmp_owner307_legacy_sales_source src;

DROP TEMPORARY TABLE IF EXISTS tmp_owner307_sales_offer_backfill;
CREATE TEMPORARY TABLE tmp_owner307_sales_offer_backfill AS
SELECT
    pv.id AS variant_id,
    lss.id AS site_id,
    ls.owner_user_id,
    ls.id AS logical_store_id,
    lss.store_code,
    lss.site AS site_code,
    pv.partner_sku AS psku_code,
    COALESCE(NULLIF(pv.child_sku, ''), NULLIF(MAX(dsf.sku), ''), NULLIF(MAX(dsf.partner_sku), ''), pv.partner_sku) AS offer_code,
    COALESCE(NULLIF(MAX(dsf.currency_code), ''), CASE WHEN lss.site = 'AE' THEN 'AED' ELSE 'SAR' END) AS currency,
    MIN(CASE
        WHEN COALESCE(dsf.your_visitors, 0) > 0 OR COALESCE(dsf.total_visitors, 0) > 0
        THEN dsf.fact_date
        ELSE NULL
    END) AS first_pv_date,
    MIN(CASE
        WHEN COALESCE(dsf.net_units, 0) > 0 THEN dsf.fact_date
        ELSE NULL
    END) AS first_sales_date,
    COUNT(*) AS fact_row_count,
    SUM(COALESCE(dsf.your_visitors, 0)) AS views_count,
    SUM(COALESCE(dsf.net_units, 0)) AS units_sold,
    SUM(COALESCE(dsf.revenue_shipped, 0)) AS sales_amount,
    MAX(dsf.fact_date) AS last_fact_date
FROM tmp_owner307_migrated_scope scope_row
JOIN logical_store ls
  ON ls.id = scope_row.logical_store_id
 AND ls.owner_user_id = scope_row.owner_user_id
 AND ls.is_deleted = b'0'
JOIN logical_store_site lss
  ON lss.logical_store_id = ls.id
 AND BINARY lss.store_code = BINARY scope_row.store_code
 AND BINARY lss.site = BINARY scope_row.site_code
 AND lss.is_deleted = b'0'
JOIN product_master pm
  ON pm.logical_store_id = ls.id
 AND pm.is_deleted = b'0'
JOIN product_variant pv
  ON pv.product_master_id = pm.id
 AND pv.is_deleted = b'0'
JOIN daily_sales_fact dsf
  ON dsf.owner_user_id = ls.owner_user_id
 AND BINARY dsf.store_code = BINARY lss.store_code
 AND BINARY dsf.site_code = BINARY lss.site
 AND (
    NULLIF(dsf.partner_sku, '') = pv.partner_sku
    OR NULLIF(dsf.sku, '') = pv.partner_sku
    OR NULLIF(dsf.partner_sku, '') = pv.child_sku
    OR NULLIF(dsf.sku, '') = pv.child_sku
 )
GROUP BY
    pv.id,
    lss.id,
    ls.owner_user_id,
    ls.id,
    lss.store_code,
    lss.site,
    pv.partner_sku,
    pv.child_sku;

SET @owner307_product_site_offer_next_id := (
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
    @owner307_product_site_offer_next_id := @owner307_product_site_offer_next_id + 1 AS id,
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
    @operator_id,
    @operator_id,
    NOW(),
    NOW()
FROM tmp_owner307_sales_offer_backfill backfill
LEFT JOIN product_site_offer active_offer
  ON active_offer.variant_id = backfill.variant_id
 AND active_offer.site_id = backfill.site_id
 AND active_offer.is_deleted = b'0'
WHERE active_offer.id IS NULL
ORDER BY backfill.store_code, backfill.variant_id
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

DROP TEMPORARY TABLE IF EXISTS tmp_owner307_listing_refresh;
CREATE TEMPORARY TABLE tmp_owner307_listing_refresh AS
SELECT
    pso.id AS product_site_offer_id,
    backfill.fact_row_count,
    CASE
        WHEN backfill.first_pv_date IS NOT NULL THEN CAST(backfill.first_pv_date AS DATETIME)
        WHEN backfill.first_sales_date IS NOT NULL THEN CAST(backfill.first_sales_date AS DATETIME)
        ELSE NULL
    END AS resolved_listing_started_at,
    CASE
        WHEN backfill.first_pv_date IS NOT NULL THEN 'pv'
        WHEN backfill.first_sales_date IS NOT NULL THEN 'sales'
        ELSE 'not_listed'
    END AS resolved_listing_started_source,
    backfill.views_count,
    backfill.units_sold,
    backfill.sales_amount,
    backfill.currency,
    backfill.last_fact_date
FROM product_site_offer pso
JOIN tmp_owner307_sales_offer_backfill backfill
  ON backfill.variant_id = pso.variant_id
 AND backfill.site_id = pso.site_id
WHERE pso.is_deleted = b'0';

UPDATE product_site_offer pso
JOIN tmp_owner307_listing_refresh refresh
  ON refresh.product_site_offer_id = pso.id
SET pso.listing_started_at = CASE
        WHEN refresh.resolved_listing_started_at IS NOT NULL
          AND (
            pso.listing_started_at IS NULL
            OR refresh.resolved_listing_started_at < pso.listing_started_at
            OR pso.listing_started_source IS NULL
            OR pso.listing_started_source IN ('data_missing', 'not_listed')
          )
        THEN refresh.resolved_listing_started_at
        ELSE pso.listing_started_at
    END,
    pso.listing_started_source = CASE
        WHEN refresh.resolved_listing_started_at IS NOT NULL
          AND (
            pso.listing_started_at IS NULL
            OR refresh.resolved_listing_started_at <= pso.listing_started_at
            OR pso.listing_started_source IS NULL
            OR pso.listing_started_source IN ('data_missing', 'not_listed')
          )
        THEN refresh.resolved_listing_started_source
        WHEN refresh.resolved_listing_started_at IS NULL
          AND pso.listing_started_at IS NULL
          AND refresh.fact_row_count > 0
        THEN 'not_listed'
        ELSE pso.listing_started_source
    END,
    pso.views_count = refresh.views_count,
    pso.units_sold = refresh.units_sold,
    pso.sales_amount = refresh.sales_amount,
    pso.sales_currency = refresh.currency,
    pso.last_synced_at = CAST(refresh.last_fact_date AS DATETIME),
    pso.updated_by = @operator_id,
    pso.gmt_updated = NOW()
WHERE pso.is_deleted = b'0';

SET @max_product_site_offer_id := (SELECT GREATEST(COALESCE(MAX(id), 55000), 55000) FROM product_site_offer);
UPDATE product_management_id_sequence
SET next_id = GREATEST(next_id, @max_product_site_offer_id),
    gmt_updated = NOW()
WHERE sequence_name = 'product_site_offer';

DROP TEMPORARY TABLE IF EXISTS tmp_owner307_listing_refresh;
DROP TEMPORARY TABLE IF EXISTS tmp_owner307_sales_offer_backfill;
DROP TEMPORARY TABLE IF EXISTS tmp_owner307_migrated_scope;
DROP TEMPORARY TABLE IF EXISTS tmp_owner307_legacy_sales_batch;
DROP TEMPORARY TABLE IF EXISTS tmp_owner307_legacy_sales_batch_candidate;
DROP TEMPORARY TABLE IF EXISTS tmp_owner307_legacy_sales_source;
