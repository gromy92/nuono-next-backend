SET NAMES utf8mb4;

SET @ops_comp_keyword_add_product_keyword_id = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operations_competitor_keyword'
        AND COLUMN_NAME = 'product_keyword_id'
    ),
    'SELECT 1',
    'ALTER TABLE `operations_competitor_keyword` ADD COLUMN `product_keyword_id` BIGINT DEFAULT NULL AFTER `watch_product_id`'
  )
);
PREPARE stmt FROM @ops_comp_keyword_add_product_keyword_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_keyword_add_product_keyword_idx = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operations_competitor_keyword'
        AND INDEX_NAME = 'idx_ops_comp_keyword_product_keyword'
    ),
    'SELECT 1',
    'ALTER TABLE `operations_competitor_keyword` ADD KEY `idx_ops_comp_keyword_product_keyword` (`product_keyword_id`)'
  )
);
PREPARE stmt FROM @ops_comp_keyword_add_product_keyword_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `product_keyword_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
  ('product_keyword', GREATEST(COALESCE((SELECT MAX(`id`) + 1 FROM `product_keyword`), 300000), 300000), NOW(), NOW()),
  ('product_keyword_usage_event', GREATEST(COALESCE((SELECT MAX(`id`) + 1 FROM `product_keyword_usage_event`), 320000), 320000), NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

DROP TEMPORARY TABLE IF EXISTS `tmp_product_keyword_competitor_link_candidates`;
CREATE TEMPORARY TABLE `tmp_product_keyword_competitor_link_candidates` AS
SELECT
  wp.`owner_user_id`,
  UPPER(TRIM(wp.`store_code`)) AS `store_code`,
  UPPER(TRIM(wp.`site_code`)) AS `site_code`,
  TRIM(wp.`partner_sku`) AS `partner_sku`,
  MIN(TRIM(kw.`keyword`)) AS `keyword`,
  kw.`keyword_norm`,
  MIN(COALESCE(kw.`last_succeeded_at`, kw.`gmt_updated`, kw.`gmt_create`, NOW())) AS `occurred_at`,
  CAST(NULL AS SIGNED) AS `product_keyword_id`
FROM `operations_competitor_keyword` kw
JOIN `operations_competitor_watch_product` wp
  ON wp.`id` = kw.`watch_product_id`
 AND wp.`is_deleted` = b'0'
WHERE kw.`is_deleted` = b'0'
  AND kw.`status` = 'ACTIVE'
  AND COALESCE(TRIM(kw.`keyword`), '') != ''
  AND COALESCE(TRIM(kw.`keyword_norm`), '') != ''
  AND COALESCE(TRIM(wp.`partner_sku`), '') != ''
GROUP BY
  wp.`owner_user_id`,
  UPPER(TRIM(wp.`store_code`)),
  UPPER(TRIM(wp.`site_code`)),
  TRIM(wp.`partner_sku`),
  kw.`keyword_norm`;

UPDATE `tmp_product_keyword_competitor_link_candidates` candidate
JOIN `product_keyword` pk
  ON pk.`owner_user_id` = candidate.`owner_user_id`
 AND pk.`store_code` = candidate.`store_code`
 AND pk.`site_code` = candidate.`site_code`
 AND pk.`partner_sku` = candidate.`partner_sku`
 AND pk.`keyword_norm` = candidate.`keyword_norm`
 AND pk.`is_deleted` = b'0'
SET candidate.`product_keyword_id` = pk.`id`;

SET @product_keyword_backfill_id = (
  SELECT GREATEST(
    COALESCE((SELECT `next_id` FROM `product_keyword_id_sequence` WHERE `sequence_name` = 'product_keyword'), 300000),
    COALESCE((SELECT MAX(`id`) + 1 FROM `product_keyword`), 300000),
    300000
  ) - 1
);

UPDATE `tmp_product_keyword_competitor_link_candidates`
SET `product_keyword_id` = (@product_keyword_backfill_id := @product_keyword_backfill_id + 1)
WHERE `product_keyword_id` IS NULL
ORDER BY `owner_user_id`, `store_code`, `site_code`, `partner_sku`, `keyword_norm`;

INSERT INTO `product_keyword` (
  `id`, `owner_user_id`, `store_code`, `site_code`, `partner_sku`, `keyword`, `keyword_norm`,
  `locale`, `status`, `intent_tags_json`, `source_summary_json`, `first_seen_at`, `last_seen_at`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  candidate.`product_keyword_id`,
  candidate.`owner_user_id`,
  candidate.`store_code`,
  candidate.`site_code`,
  candidate.`partner_sku`,
  candidate.`keyword`,
  candidate.`keyword_norm`,
  NULL,
  'OBSERVED',
  '["COMPETITOR_TRACK"]',
  JSON_OBJECT('source', 'operations_competitor_keyword_backfill'),
  candidate.`occurred_at`,
  candidate.`occurred_at`,
  b'0',
  NULL,
  NULL,
  NOW(),
  NOW()
FROM `tmp_product_keyword_competitor_link_candidates` candidate
LEFT JOIN `product_keyword` existing
  ON existing.`id` = candidate.`product_keyword_id`
WHERE existing.`id` IS NULL
ON DUPLICATE KEY UPDATE
  `intent_tags_json` = CASE
    WHEN `product_keyword`.`intent_tags_json` LIKE '%"COMPETITOR_TRACK"%' THEN `product_keyword`.`intent_tags_json`
    WHEN `product_keyword`.`intent_tags_json` IS NULL OR `product_keyword`.`intent_tags_json` = '' THEN '["COMPETITOR_TRACK"]'
    ELSE REPLACE(`product_keyword`.`intent_tags_json`, ']', ',"COMPETITOR_TRACK"]')
  END,
  `last_seen_at` = GREATEST(COALESCE(`product_keyword`.`last_seen_at`, VALUES(`last_seen_at`)), VALUES(`last_seen_at`)),
  `gmt_updated` = NOW();

UPDATE `tmp_product_keyword_competitor_link_candidates` candidate
JOIN `product_keyword` pk
  ON pk.`owner_user_id` = candidate.`owner_user_id`
 AND pk.`store_code` = candidate.`store_code`
 AND pk.`site_code` = candidate.`site_code`
 AND pk.`partner_sku` = candidate.`partner_sku`
 AND pk.`keyword_norm` = candidate.`keyword_norm`
 AND pk.`is_deleted` = b'0'
SET candidate.`product_keyword_id` = pk.`id`;

UPDATE `operations_competitor_keyword` kw
JOIN `operations_competitor_watch_product` wp
  ON wp.`id` = kw.`watch_product_id`
 AND wp.`is_deleted` = b'0'
JOIN `tmp_product_keyword_competitor_link_candidates` candidate
  ON candidate.`owner_user_id` = wp.`owner_user_id`
 AND candidate.`store_code` = UPPER(TRIM(wp.`store_code`))
 AND candidate.`site_code` = UPPER(TRIM(wp.`site_code`))
 AND candidate.`partner_sku` = TRIM(wp.`partner_sku`)
 AND candidate.`keyword_norm` = kw.`keyword_norm`
SET kw.`product_keyword_id` = candidate.`product_keyword_id`,
    kw.`gmt_updated` = NOW()
WHERE kw.`is_deleted` = b'0'
  AND kw.`status` = 'ACTIVE';

DROP TEMPORARY TABLE IF EXISTS `tmp_product_keyword_competitor_usage_events`;
CREATE TEMPORARY TABLE `tmp_product_keyword_competitor_usage_events` AS
SELECT
  kw.`id` AS `competitor_keyword_id`,
  kw.`product_keyword_id`,
  wp.`owner_user_id`,
  UPPER(TRIM(wp.`store_code`)) AS `store_code`,
  UPPER(TRIM(wp.`site_code`)) AS `site_code`,
  TRIM(wp.`partner_sku`) AS `partner_sku`,
  TRIM(kw.`keyword`) AS `keyword`,
  kw.`keyword_norm`,
  COALESCE(kw.`last_succeeded_at`, kw.`gmt_updated`, kw.`gmt_create`, NOW()) AS `occurred_at`,
  CAST(NULL AS SIGNED) AS `usage_event_id`
FROM `operations_competitor_keyword` kw
JOIN `operations_competitor_watch_product` wp
  ON wp.`id` = kw.`watch_product_id`
 AND wp.`is_deleted` = b'0'
WHERE kw.`product_keyword_id` IS NOT NULL
  AND kw.`is_deleted` = b'0'
  AND kw.`status` = 'ACTIVE';

SET @product_keyword_usage_event_backfill_id = (
  SELECT GREATEST(
    COALESCE((SELECT `next_id` FROM `product_keyword_id_sequence` WHERE `sequence_name` = 'product_keyword_usage_event'), 320000),
    COALESCE((SELECT MAX(`id`) + 1 FROM `product_keyword_usage_event`), 320000),
    320000
  ) - 1
);

UPDATE `tmp_product_keyword_competitor_usage_events`
SET `usage_event_id` = (@product_keyword_usage_event_backfill_id := @product_keyword_usage_event_backfill_id + 1)
ORDER BY `competitor_keyword_id`;

INSERT INTO `product_keyword_usage_event` (
  `id`, `keyword_id`, `owner_user_id`, `store_code`, `site_code`, `partner_sku`, `keyword`, `keyword_norm`,
  `source_type`, `source_ref_type`, `source_ref_id`, `source_ref_key`, `event_natural_key`, `event_status`,
  `occurred_at`, `fact_date_from`, `fact_date_to`, `payload_json`, `metrics_json`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  event.`usage_event_id`,
  event.`product_keyword_id`,
  event.`owner_user_id`,
  event.`store_code`,
  event.`site_code`,
  event.`partner_sku`,
  event.`keyword`,
  event.`keyword_norm`,
  'COMPETITOR_KEYWORD',
  'operations_competitor_keyword',
  event.`competitor_keyword_id`,
  CONCAT(event.`competitor_keyword_id`),
  CONCAT('COMPETITOR_KEYWORD|operations_competitor_keyword|', event.`competitor_keyword_id`, '|',
         event.`site_code`, '|', event.`partner_sku`, '|', event.`keyword_norm`, '|OBSERVED'),
  'OBSERVED',
  event.`occurred_at`,
  NULL,
  NULL,
  JSON_OBJECT('competitorKeywordId', event.`competitor_keyword_id`, 'keyword', event.`keyword`, 'status', 'ACTIVE'),
  '{}',
  b'0',
  NULL,
  NULL,
  NOW(),
  NOW()
FROM `tmp_product_keyword_competitor_usage_events` event
ON DUPLICATE KEY UPDATE
  `keyword_id` = VALUES(`keyword_id`),
  `keyword` = VALUES(`keyword`),
  `keyword_norm` = VALUES(`keyword_norm`),
  `source_ref_type` = VALUES(`source_ref_type`),
  `source_ref_id` = VALUES(`source_ref_id`),
  `source_ref_key` = VALUES(`source_ref_key`),
  `event_status` = VALUES(`event_status`),
  `occurred_at` = VALUES(`occurred_at`),
  `payload_json` = VALUES(`payload_json`),
  `metrics_json` = VALUES(`metrics_json`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

INSERT INTO `product_keyword_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_keyword', GREATEST(COALESCE(MAX(`id`) + 1, 300000), 300000), NOW(), NOW()
FROM `product_keyword`
UNION ALL
SELECT 'product_keyword_usage_event', GREATEST(COALESCE(MAX(`id`) + 1, 320000), 320000), NOW(), NOW()
FROM `product_keyword_usage_event`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

DROP TEMPORARY TABLE IF EXISTS `tmp_product_keyword_competitor_usage_events`;
DROP TEMPORARY TABLE IF EXISTS `tmp_product_keyword_competitor_link_candidates`;
