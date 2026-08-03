SET @add_noon_auth_source_checkpoint := IF(
  EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'noon_auth_identity_recovery_item'
      AND COLUMN_NAME = 'source_checkpoint'
  ),
  'SELECT ''noon_auth_source_checkpoint_exists'' AS stage',
  'ALTER TABLE noon_auth_identity_recovery_item ADD COLUMN source_checkpoint VARCHAR(64) DEFAULT NULL AFTER source_domain'
);
PREPARE add_noon_auth_source_checkpoint_stmt FROM @add_noon_auth_source_checkpoint;
EXECUTE add_noon_auth_source_checkpoint_stmt;
DEALLOCATE PREPARE add_noon_auth_source_checkpoint_stmt;

SET @add_noon_auth_resume_policy := IF(
  EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'noon_auth_identity_recovery_item'
      AND COLUMN_NAME = 'resume_policy'
  ),
  'SELECT ''noon_auth_resume_policy_exists'' AS stage',
  'ALTER TABLE noon_auth_identity_recovery_item ADD COLUMN resume_policy VARCHAR(32) NOT NULL DEFAULT ''AUTO_RESUME'' AFTER source_checkpoint'
);
PREPARE add_noon_auth_resume_policy_stmt FROM @add_noon_auth_resume_policy;
EXECUTE add_noon_auth_resume_policy_stmt;
DEALLOCATE PREPARE add_noon_auth_resume_policy_stmt;

SET @add_noon_auth_source_task_key := IF(
  EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'noon_auth_identity_recovery_item'
      AND COLUMN_NAME = 'source_task_key'
  ),
  'SELECT ''noon_auth_source_task_key_exists'' AS stage',
  'ALTER TABLE noon_auth_identity_recovery_item ADD COLUMN source_task_key VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (CASE WHEN source_task_id IS NULL THEN ''#binding'' ELSE CONCAT(COALESCE(NULLIF(UPPER(TRIM(source_domain)), ''''), ''#unknown''), '':'', source_task_id) END) STORED AFTER source_task_slot'
);
PREPARE add_noon_auth_source_task_key_stmt FROM @add_noon_auth_source_task_key;
EXECUTE add_noon_auth_source_task_key_stmt;
DEALLOCATE PREPARE add_noon_auth_source_task_key_stmt;

SET @drop_old_noon_auth_item_source_key := IF(
  EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'noon_auth_identity_recovery_item'
      AND INDEX_NAME = 'uk_noon_auth_recovery_item_source'
  ),
  'ALTER TABLE noon_auth_identity_recovery_item DROP INDEX uk_noon_auth_recovery_item_source',
  'SELECT ''old_noon_auth_item_source_key_absent'' AS stage'
);
PREPARE drop_old_noon_auth_item_source_key_stmt FROM @drop_old_noon_auth_item_source_key;
EXECUTE drop_old_noon_auth_item_source_key_stmt;
DEALLOCATE PREPARE drop_old_noon_auth_item_source_key_stmt;

SET @add_noon_auth_item_source_key := IF(
  EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'noon_auth_identity_recovery_item'
      AND INDEX_NAME = 'uk_noon_auth_recovery_item_business_source'
  ),
  'SELECT ''noon_auth_item_business_source_key_exists'' AS stage',
  'ALTER TABLE noon_auth_identity_recovery_item ADD UNIQUE INDEX uk_noon_auth_recovery_item_business_source (recovery_id, owner_user_id, project_code, source_task_key)'
);
PREPARE add_noon_auth_item_source_key_stmt FROM @add_noon_auth_item_source_key;
EXECUTE add_noon_auth_item_source_key_stmt;
DEALLOCATE PREPARE add_noon_auth_item_source_key_stmt;

SET @add_sales_sync_auth_recovery_id := IF(
  EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sales_sync_task'
      AND COLUMN_NAME = 'auth_recovery_id'
  ),
  'SELECT ''sales_sync_auth_recovery_id_exists'' AS stage',
  'ALTER TABLE sales_sync_task ADD COLUMN auth_recovery_id BIGINT DEFAULT NULL AFTER failure_reason'
);
PREPARE add_sales_sync_auth_recovery_id_stmt FROM @add_sales_sync_auth_recovery_id;
EXECUTE add_sales_sync_auth_recovery_id_stmt;
DEALLOCATE PREPARE add_sales_sync_auth_recovery_id_stmt;

SET @add_sales_sync_listing_coverage_mode := IF(
  EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sales_sync_task'
      AND COLUMN_NAME = 'listing_coverage_mode'
  ),
  'SELECT ''sales_sync_listing_coverage_mode_exists'' AS stage',
  'ALTER TABLE sales_sync_task ADD COLUMN listing_coverage_mode VARCHAR(32) NOT NULL DEFAULT ''NONE'' AFTER trigger_type'
);
PREPARE add_sales_sync_listing_coverage_mode_stmt FROM @add_sales_sync_listing_coverage_mode;
EXECUTE add_sales_sync_listing_coverage_mode_stmt;
DEALLOCATE PREPARE add_sales_sync_listing_coverage_mode_stmt;

DROP TABLE IF EXISTS product_listing_reauthentication_attempt;
