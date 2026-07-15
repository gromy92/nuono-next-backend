-- Allow a failed real-run to be retried unless Noon already accepted the same PSKU.

UPDATE product_listing_task
SET failure_category = 'validation',
    failure_code = 'partner_sku_already_exists',
    failure_message = 'PSKU 已存在，不能重复创建。请更换新的 PSKU，或到商品详情中编辑已有商品。',
    gmt_updated = NOW()
WHERE mode = 'REAL_RUN'
  AND status = 'failed'
  AND (failure_code IS NULL OR failure_code <> 'partner_sku_already_exists')
  AND failure_message LIKE '%Partner skus already exists%';

UPDATE product_listing_task t
JOIN (
  SELECT owner_user_id, source_task_id, MAX(id) AS keep_task_id
  FROM product_listing_task
  WHERE mode = 'REAL_RUN'
    AND source_task_id IS NOT NULL
    AND (
      status IN ('running', 'submitted', 'succeeded', 'written_verify_failed')
      OR (status = 'failed' AND failure_code = 'partner_sku_already_exists')
    )
  GROUP BY owner_user_id, source_task_id
  HAVING COUNT(*) > 1
) duplicate_attempt
  ON duplicate_attempt.owner_user_id = t.owner_user_id
 AND duplicate_attempt.source_task_id = t.source_task_id
SET t.failure_code = 'partner_sku_already_exists_superseded',
    t.failure_message = '历史重复上架尝试已被最新任务替代。',
    t.gmt_updated = NOW()
WHERE t.mode = 'REAL_RUN'
  AND t.status = 'failed'
  AND t.failure_code = 'partner_sku_already_exists'
  AND t.id <> duplicate_attempt.keep_task_id;

SET @add_product_listing_real_write_attempt_source_task_id = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_listing_task'
      AND column_name = 'real_write_attempt_source_task_id'
  ),
  'SELECT ''product_listing_real_write_attempt_source_task_id_exists'' AS stage',
  'ALTER TABLE product_listing_task ADD COLUMN real_write_attempt_source_task_id BIGINT GENERATED ALWAYS AS (CASE WHEN `mode` = ''REAL_RUN'' AND (`status` IN (''running'', ''submitted'', ''succeeded'', ''written_verify_failed'') OR (`status` = ''failed'' AND `failure_code` = ''partner_sku_already_exists'')) THEN `source_task_id` ELSE NULL END) STORED AFTER completed_at'
);
PREPARE add_product_listing_real_write_attempt_source_task_id_stmt FROM @add_product_listing_real_write_attempt_source_task_id;
EXECUTE add_product_listing_real_write_attempt_source_task_id_stmt;
DEALLOCATE PREPARE add_product_listing_real_write_attempt_source_task_id_stmt;

SET @drop_product_listing_real_write_attempt_index = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product_listing_task'
      AND index_name = 'uk_product_listing_real_write_attempt'
  ),
  'ALTER TABLE product_listing_task DROP INDEX uk_product_listing_real_write_attempt',
  'SELECT ''product_listing_real_write_attempt_index_missing_before_refresh'' AS stage'
);
PREPARE drop_product_listing_real_write_attempt_index_stmt FROM @drop_product_listing_real_write_attempt_index;
EXECUTE drop_product_listing_real_write_attempt_index_stmt;
DEALLOCATE PREPARE drop_product_listing_real_write_attempt_index_stmt;

ALTER TABLE product_listing_task
  MODIFY COLUMN real_write_attempt_source_task_id BIGINT
  GENERATED ALWAYS AS (CASE WHEN `mode` = 'REAL_RUN' AND (`status` IN ('running', 'submitted', 'succeeded', 'written_verify_failed') OR (`status` = 'failed' AND `failure_code` = 'partner_sku_already_exists')) THEN `source_task_id` ELSE NULL END) STORED;

SET @add_product_listing_real_write_attempt_index = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product_listing_task'
      AND index_name = 'uk_product_listing_real_write_attempt'
  ),
  'SELECT ''product_listing_real_write_attempt_index_exists'' AS stage',
  'CREATE UNIQUE INDEX uk_product_listing_real_write_attempt ON product_listing_task (owner_user_id, real_write_attempt_source_task_id)'
);
PREPARE add_product_listing_real_write_attempt_index_stmt FROM @add_product_listing_real_write_attempt_index;
EXECUTE add_product_listing_real_write_attempt_index_stmt;
DEALLOCATE PREPARE add_product_listing_real_write_attempt_index_stmt;
