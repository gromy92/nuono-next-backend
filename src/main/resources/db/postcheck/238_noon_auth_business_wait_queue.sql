SELECT IF(
  (
    SELECT COUNT(*) AS required_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'noon_auth_identity_recovery_item'
      AND (
        (COLUMN_NAME = 'source_checkpoint' AND DATA_TYPE = 'varchar'
          AND CHARACTER_MAXIMUM_LENGTH = 64 AND IS_NULLABLE = 'YES')
        OR (COLUMN_NAME = 'resume_policy' AND DATA_TYPE = 'varchar'
          AND CHARACTER_MAXIMUM_LENGTH = 32 AND IS_NULLABLE = 'NO'
          AND COLUMN_DEFAULT = 'AUTO_RESUME')
        OR (COLUMN_NAME = 'source_task_key' AND DATA_TYPE = 'varchar'
          AND CHARACTER_MAXIMUM_LENGTH = 160 AND COLLATION_NAME = 'utf8mb4_bin'
          AND IS_NULLABLE = 'YES' AND UPPER(EXTRA) = 'STORED GENERATED'
          AND LOWER(GENERATION_EXPRESSION) LIKE '%source_domain%'
          AND LOWER(GENERATION_EXPRESSION) LIKE '%source_task_id%')
      )
  ) = 3
  AND (
    SELECT IF(
      COUNT(*) = 4 AND MIN(NON_UNIQUE) = 0 AND MAX(NON_UNIQUE) = 0
        AND GROUP_CONCAT(
          CONCAT(SEQ_IN_INDEX, ':', COLUMN_NAME)
          ORDER BY SEQ_IN_INDEX SEPARATOR ','
        ) = '1:recovery_id,2:owner_user_id,3:project_code,4:source_task_key',
      1,
      0
    ) AS business_source_index_valid
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'noon_auth_identity_recovery_item'
      AND INDEX_NAME = 'uk_noon_auth_recovery_item_business_source'
  ) = 1
  AND (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'noon_auth_identity_recovery_item'
      AND INDEX_NAME = 'uk_noon_auth_recovery_item_source'
  ) = 0
  AND (
    SELECT COUNT(*) AS sales_sync_wait_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sales_sync_task'
      AND (
        (COLUMN_NAME = 'auth_recovery_id' AND DATA_TYPE = 'bigint'
          AND IS_NULLABLE = 'YES')
        OR (COLUMN_NAME = 'listing_coverage_mode' AND DATA_TYPE = 'varchar'
          AND CHARACTER_MAXIMUM_LENGTH = 32 AND IS_NULLABLE = 'NO'
          AND COLUMN_DEFAULT = 'NONE')
      )
  ) = 2
  AND (
    SELECT COUNT(*) AS obsolete_listing_reauthentication_table_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_listing_reauthentication_attempt'
  ) = 0,
  1,
  0
) AS noon_auth_business_wait_queue_ready;
