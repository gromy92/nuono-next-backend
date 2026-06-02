-- Restore canman / PRJ108065 SA historical sales facts from the legacy
-- cross_border_erp.product_sales_data export.
--
-- Load the local staging table first, then run this script:
--   LOAD DATA LOCAL INFILE '<export.tsv>'
--   INTO TABLE legacy_canman_sa_product_sales_stage
--   FIELDS TERMINATED BY '\t'
--   LINES TERMINATED BY '\n'
--   (...columns listed below...);
--
-- The export source is legacy user 338, STR108065-NSA, SA. The target owner is
-- local owner 307.

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

SET NAMES utf8mb4;

SET @canman_owner_user_id := 307;
SET @canman_legacy_data_user_id := 338;
SET @canman_project_code := 'PRJ108065';
SET @canman_sa_store_code := 'STR108065-NSA';
SET @canman_sa_site := 'SA';
SET @canman_operator_id := 10003;
SET @legacy_source_system := 'legacy_product_sales_data';
SET @legacy_source_filename := 'legacy_canman_sa_product_sales_data;legacy_user=338;owner=307;store=STR108065-NSA;site=SA';

CREATE TABLE IF NOT EXISTS legacy_canman_sa_product_sales_stage (
    legacy_row_id BIGINT NOT NULL,
    legacy_user_id BIGINT NOT NULL,
    store_code VARCHAR(80) NOT NULL,
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
    KEY idx_legacy_canman_sa_sales_scope (legacy_user_id, store_code, visit_date),
    KEY idx_legacy_canman_sa_sales_psku (psku_hex)
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

SET @canman_logical_store_id := (
    SELECT ls.id
    FROM logical_store ls
    WHERE ls.owner_user_id = @canman_owner_user_id
      AND BINARY ls.project_code = BINARY @canman_project_code
      AND ls.is_deleted = b'0'
    ORDER BY ls.id ASC
    LIMIT 1
);

SET @canman_sa_site_id := (
    SELECT lss.id
    FROM logical_store_site lss
    WHERE lss.logical_store_id = @canman_logical_store_id
      AND BINARY lss.store_code = BINARY @canman_sa_store_code
      AND BINARY lss.site = BINARY @canman_sa_site
      AND lss.is_deleted = b'0'
    ORDER BY lss.id ASC
    LIMIT 1
);

DROP TEMPORARY TABLE IF EXISTS tmp_canman_sa_legacy_sales_source;
CREATE TEMPORARY TABLE tmp_canman_sa_legacy_sales_source AS
SELECT
    stage.legacy_row_id,
    stage.legacy_user_id,
    @canman_owner_user_id AS owner_user_id,
    @canman_logical_store_id AS logical_store_id,
    @canman_sa_store_code AS store_code,
    @canman_sa_site AS site_code,
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
FROM legacy_canman_sa_product_sales_stage stage
WHERE stage.legacy_user_id = @canman_legacy_data_user_id
  AND BINARY stage.store_code = BINARY @canman_sa_store_code
  AND stage.visit_date IS NOT NULL
  AND NULLIF(stage.psku_hex, '') IS NOT NULL
  AND NULLIF(stage.sku_hex, '') IS NOT NULL;

SET @canman_sa_legacy_source_rows := (SELECT COUNT(*) FROM tmp_canman_sa_legacy_sales_source);
SET @canman_sa_legacy_report_date_from := (SELECT MIN(src.fact_date) FROM tmp_canman_sa_legacy_sales_source src);
SET @canman_sa_legacy_report_date_to := (SELECT MAX(src.fact_date) FROM tmp_canman_sa_legacy_sales_source src);

SET @canman_sa_legacy_batch_id := (
    SELECT sib.id
    FROM sales_import_batch sib
    WHERE BINARY sib.source_system = BINARY @legacy_source_system
      AND BINARY sib.source_filename = BINARY @legacy_source_filename
      AND sib.owner_user_id = @canman_owner_user_id
      AND BINARY sib.store_code = BINARY @canman_sa_store_code
      AND BINARY sib.site_code = BINARY @canman_sa_site
    ORDER BY sib.id ASC
    LIMIT 1
);

SET @new_canman_sa_legacy_batch_id := (
    SELECT GREATEST(
        COALESCE((SELECT seq.next_id FROM sales_data_id_sequence seq WHERE seq.sequence_name = 'sales_import_batch'), 10000),
        COALESCE((SELECT MAX(sib.id) FROM sales_import_batch sib), 10000),
        10000
    ) + 1
);

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
    @new_canman_sa_legacy_batch_id,
    @legacy_source_system,
    @legacy_source_filename,
    @canman_owner_user_id,
    @canman_logical_store_id,
    @canman_sa_store_code,
    @canman_sa_site,
    @canman_sa_legacy_report_date_from,
    @canman_sa_legacy_report_date_to,
    @canman_sa_legacy_source_rows,
    @canman_sa_legacy_source_rows,
    0,
    IF(@canman_sa_legacy_source_rows = 0, 'empty', 'imported'),
    NULL,
    NOW(),
    NOW(),
    NOW()
WHERE @canman_sa_legacy_batch_id IS NULL
  AND @canman_sa_legacy_source_rows > 0;

SET @canman_sa_legacy_batch_id := COALESCE(@canman_sa_legacy_batch_id, @new_canman_sa_legacy_batch_id);

UPDATE sales_import_batch
SET logical_store_id = @canman_logical_store_id,
    report_date_from = @canman_sa_legacy_report_date_from,
    report_date_to = @canman_sa_legacy_report_date_to,
    total_rows = @canman_sa_legacy_source_rows,
    success_rows = @canman_sa_legacy_source_rows,
    failure_rows = 0,
    status = IF(@canman_sa_legacy_source_rows = 0, 'empty', 'imported'),
    failure_summary_json = NULL,
    imported_at = NOW(),
    gmt_updated = NOW()
WHERE id = @canman_sa_legacy_batch_id
  AND @canman_sa_legacy_source_rows > 0;

SET @max_sales_import_batch_id := (SELECT GREATEST(COALESCE(MAX(id), 10000), 10000) FROM sales_import_batch);
UPDATE sales_data_id_sequence
SET next_id = GREATEST(next_id, @max_sales_import_batch_id),
    gmt_updated = NOW()
WHERE sequence_name = 'sales_import_batch';

SET @canman_sa_legacy_fact_next_id := (
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
    @canman_sa_legacy_fact_next_id := @canman_sa_legacy_fact_next_id + 1 AS id,
    @legacy_source_system,
    @canman_sa_legacy_batch_id,
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
        src.legacy_user_id,
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
FROM tmp_canman_sa_legacy_sales_source src
WHERE @canman_sa_legacy_batch_id IS NOT NULL
ORDER BY src.legacy_row_id
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
    COUNT(*) AS fact_row_count,
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

DROP TEMPORARY TABLE IF EXISTS tmp_canman_sa_listing_refresh;
CREATE TEMPORARY TABLE tmp_canman_sa_listing_refresh AS
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
JOIN tmp_canman_sa_sales_offer_backfill backfill
  ON backfill.variant_id = pso.variant_id
 AND backfill.site_id = pso.site_id
WHERE pso.is_deleted = b'0';

UPDATE product_site_offer pso
JOIN tmp_canman_sa_listing_refresh refresh
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
    pso.updated_by = @canman_operator_id,
    pso.gmt_updated = NOW()
WHERE pso.is_deleted = b'0';

SET @max_product_site_offer_id := (SELECT GREATEST(COALESCE(MAX(id), 55000), 55000) FROM product_site_offer);
UPDATE product_management_id_sequence
SET next_id = GREATEST(next_id, @max_product_site_offer_id),
    gmt_updated = NOW()
WHERE sequence_name = 'product_site_offer';

DROP TEMPORARY TABLE IF EXISTS tmp_canman_sa_listing_refresh;
DROP TEMPORARY TABLE IF EXISTS tmp_canman_sa_sales_offer_backfill;
DROP TEMPORARY TABLE IF EXISTS tmp_canman_sa_legacy_sales_source;
