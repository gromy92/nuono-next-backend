-- Backfill current 1688 collection tasks for existing manual source collections.
-- Source collection is the system-owned task origin; plugin assignments are only a browser-side assist.

SET NAMES utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES ('product_selection_ali1688_collection_task', 87000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, 87000),
  `gmt_updated` = NOW();

SET @ali1688_task_base := (
  SELECT `next_id`
  FROM `product_management_id_sequence`
  WHERE `sequence_name` = 'product_selection_ali1688_collection_task'
);
SET @ali1688_task_row := 0;

INSERT INTO `product_selection_ali1688_collection_task` (
  `id`,
  `source_collection_id`,
  `current_task_key`,
  `owner_user_id`,
  `logical_store_id`,
  `task_no`,
  `status`,
  `progress_percent`,
  `search_mode`,
  `source_image_url`,
  `selected_image_count`,
  `scanned_count`,
  `candidate_count`,
  `recommended_count`,
  `failure_code`,
  `failure_message`,
  `raw_search_snapshot_json`,
  `is_deleted`,
  `created_by`,
  `updated_by`,
  `gmt_create`,
  `gmt_updated`
)
SELECT
  @ali1688_task_base + backfill_source.row_no AS `id`,
  backfill_source.id AS `source_collection_id`,
  CAST(backfill_source.id AS CHAR) AS `current_task_key`,
  backfill_source.owner_user_id,
  backfill_source.logical_store_id,
  CONCAT('ALI1688-', @ali1688_task_base + backfill_source.row_no) AS `task_no`,
  CASE
    WHEN backfill_source.status = 'success' THEN 'queued'
    WHEN backfill_source.status = 'failed' THEN 'failed'
    ELSE 'waiting_source'
  END AS `status`,
  CASE
    WHEN backfill_source.status = 'success' THEN 5
    WHEN backfill_source.status = 'failed' THEN 100
    ELSE 0
  END AS `progress_percent`,
  '主图图搜' AS `search_mode`,
  backfill_source.source_image_url,
  CASE
    WHEN backfill_source.image_urls_json IS NOT NULL
      AND backfill_source.image_urls_json <> ''
      AND JSON_VALID(backfill_source.image_urls_json)
      THEN COALESCE(JSON_LENGTH(backfill_source.image_urls_json), 0)
    WHEN backfill_source.source_image_url IS NOT NULL
      AND backfill_source.source_image_url <> ''
      THEN 1
    ELSE 0
  END AS `selected_image_count`,
  0 AS `scanned_count`,
  0 AS `candidate_count`,
  0 AS `recommended_count`,
  CASE
    WHEN backfill_source.status = 'failed' THEN 'source_collection_failed'
    ELSE NULL
  END AS `failure_code`,
  CASE
    WHEN backfill_source.status = 'failed'
      THEN COALESCE(backfill_source.failure_message, '源头商品采集失败，1688 候选采集未启动。')
    ELSE NULL
  END AS `failure_message`,
  JSON_OBJECT(
    'backfilledFromSourceCollection', TRUE,
    'backfillVersion', 'ALI1688_TASK_BACKFILL_V1'
  ) AS `raw_search_snapshot_json`,
  b'0' AS `is_deleted`,
  COALESCE(backfill_source.updated_by, backfill_source.created_by, backfill_source.owner_user_id) AS `created_by`,
  COALESCE(backfill_source.updated_by, backfill_source.created_by, backfill_source.owner_user_id) AS `updated_by`,
  NOW() AS `gmt_create`,
  NOW() AS `gmt_updated`
FROM (
  SELECT
    source.*,
    (@ali1688_task_row := @ali1688_task_row + 1) AS row_no
  FROM `product_selection_source_collection` source
  WHERE source.is_deleted = b'0'
    AND NOT EXISTS (
      SELECT 1
      FROM `product_selection_ali1688_collection_task` task
      WHERE task.source_collection_id = source.id
        AND task.current_task_key IS NOT NULL
        AND task.is_deleted = b'0'
    )
  ORDER BY source.id ASC
) backfill_source;

UPDATE `product_management_id_sequence`
SET
  `next_id` = GREATEST(`next_id`, @ali1688_task_base + @ali1688_task_row),
  `gmt_updated` = NOW()
WHERE `sequence_name` = 'product_selection_ali1688_collection_task';
