-- Permit duplicate corrected dates only for soft-deleted competitor snapshots.
-- Accept exactly the legacy or target schema; every other state fails closed.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout = 5;
SET SESSION lock_wait_timeout = 5;

SET @cps_table_exact := (
    SELECT COUNT(*) = 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_product_snapshot'
      AND table_type = 'BASE TABLE'
      AND UPPER(engine) = 'INNODB'
      AND table_collation = 'utf8mb4_unicode_ci'
);
SET @cps_base_columns_exact := (
    SELECT COUNT(*) = 6
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_product_snapshot'
      AND extra = ''
      AND generation_expression = ''
      AND (
        (column_name = 'id' AND column_type = 'bigint'
          AND is_nullable = 'NO' AND column_default IS NULL)
        OR (column_name = 'watch_product_id' AND column_type = 'bigint'
          AND is_nullable = 'NO' AND column_default IS NULL)
        OR (column_name = 'subject_type' AND column_type = 'varchar(32)'
          AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'utf8mb4'
          AND collation_name = 'utf8mb4_unicode_ci')
        OR (column_name = 'noon_product_code' AND column_type = 'varchar(80)'
          AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'utf8mb4'
          AND collation_name = 'utf8mb4_unicode_ci')
        OR (column_name = 'fact_date' AND column_type = 'date'
          AND is_nullable = 'NO' AND column_default IS NULL)
        OR (column_name = 'is_deleted' AND column_type = 'bit(1)'
          AND is_nullable = 'NO' AND column_default IS NOT NULL)
      )
);

DROP TEMPORARY TABLE IF EXISTS `_migration_240_indexes`;
CREATE TEMPORARY TABLE `_migration_240_indexes` AS
SELECT index_name, MIN(non_unique) AS non_unique, COUNT(*) AS column_count,
       GROUP_CONCAT(COALESCE(column_name, '<expression>')
         ORDER BY seq_in_index SEPARATOR ',') AS column_signature,
       SUM(sub_part IS NOT NULL) AS prefix_count,
       SUM(UPPER(index_type) <> 'BTREE') AS non_btree_count,
       SUM(UPPER(is_visible) <> 'YES') AS invisible_count,
       SUM(expression IS NOT NULL) AS expression_count
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'operations_competitor_product_snapshot'
GROUP BY index_name;

SET @cps_preserved_indexes_exact := (
    SELECT COUNT(*) = 6
    FROM `_migration_240_indexes`
    WHERE prefix_count = 0 AND non_btree_count = 0
      AND invisible_count = 0 AND expression_count = 0
      AND CONCAT(index_name, '|', non_unique, '|', column_count, '|',
                 column_signature) IN (
        'PRIMARY|0|1|id',
        'idx_ops_comp_snapshot_watch_date|1|2|watch_product_id,fact_date',
        'idx_ops_comp_snapshot_product_date|1|3|watch_product_id,competitor_product_id,fact_date',
        'idx_ops_comp_snapshot_code_date|1|3|site_code,noon_product_code,fact_date',
        'idx_ops_comp_snapshot_task|1|1|source_task_id',
        'idx_ops_comp_snapshot_run|1|1|source_run_id'
      )
);
SET @cps_index_count := (
    SELECT COUNT(*) FROM `_migration_240_indexes`
);
SET @cps_active_column_count := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_product_snapshot'
      AND column_name = 'active_fact_date'
);
SET @cps_active_column_exact := (
    SELECT COUNT(*) = 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_product_snapshot'
      AND column_name = 'active_fact_date'
      AND column_type = 'date' AND is_nullable = 'YES'
      AND column_default IS NULL
      AND UPPER(extra) = 'VIRTUAL GENERATED'
      AND REGEXP_REPLACE(
        REGEXP_REPLACE(
          REPLACE(REPLACE(LOWER(generation_expression),
            '`is_deleted`', 'is_deleted'), '`fact_date`', 'fact_date'),
          '(_binary|b)?''\\\\?0''', '0'
        ),
        '[[:space:]]+', ''
      ) REGEXP
        '^[()]*casewhen[(]*is_deleted[)]*=[(]*(0|0b0|0x00)[)]*then[(]*fact_date[)]*elsenullend[()]*$'
);
SET @cps_old_exact := (
    SELECT COUNT(*) = 1 FROM `_migration_240_indexes`
    WHERE index_name = 'uk_ops_comp_snapshot_daily'
      AND non_unique = 0 AND column_count = 4
      AND column_signature =
        'watch_product_id,subject_type,noon_product_code,fact_date'
      AND prefix_count = 0 AND non_btree_count = 0
      AND invisible_count = 0 AND expression_count = 0
);
SET @cps_target_exact := (
    SELECT COUNT(*) = 1 FROM `_migration_240_indexes`
    WHERE index_name = 'uk_ops_comp_snapshot_active_daily'
      AND non_unique = 0 AND column_count = 4
      AND column_signature =
        'watch_product_id,subject_type,noon_product_code,active_fact_date'
      AND prefix_count = 0 AND non_btree_count = 0
      AND invisible_count = 0 AND expression_count = 0
);
SET @cps_old_equivalent_count := (
    SELECT COUNT(*) FROM `_migration_240_indexes`
    WHERE non_unique = 0 AND column_count = 4
      AND column_signature =
        'watch_product_id,subject_type,noon_product_code,fact_date'
      AND prefix_count = 0 AND non_btree_count = 0
      AND invisible_count = 0 AND expression_count = 0
);
SET @cps_target_equivalent_count := (
    SELECT COUNT(*) FROM `_migration_240_indexes`
    WHERE non_unique = 0 AND column_count = 4
      AND column_signature =
        'watch_product_id,subject_type,noon_product_code,active_fact_date'
      AND prefix_count = 0 AND non_btree_count = 0
      AND invisible_count = 0 AND expression_count = 0
);

SET @cps_state := CASE
  WHEN @cps_table_exact = 1 AND @cps_base_columns_exact = 1
    AND @cps_preserved_indexes_exact = 1 AND @cps_index_count = 7
    AND @cps_active_column_count = 0
    AND @cps_old_exact = 1 AND @cps_old_equivalent_count = 1
    AND @cps_target_exact = 0 AND @cps_target_equivalent_count = 0
    THEN 'LEGACY'
  WHEN @cps_table_exact = 1 AND @cps_base_columns_exact = 1
    AND @cps_preserved_indexes_exact = 1 AND @cps_index_count = 7
    AND @cps_active_column_count = 1 AND @cps_active_column_exact = 1
    AND @cps_old_exact = 0 AND @cps_old_equivalent_count = 0
    AND @cps_target_exact = 1 AND @cps_target_equivalent_count = 1
    THEN 'TARGET'
  ELSE 'UNSUPPORTED'
END;
SET @cps_ddl := CASE @cps_state
  WHEN 'LEGACY' THEN
    'ALTER TABLE `operations_competitor_product_snapshot` ADD COLUMN `active_fact_date` DATE GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b''0'' THEN `fact_date` ELSE NULL END) VIRTUAL AFTER `fact_date`, DROP INDEX `uk_ops_comp_snapshot_daily`, ADD UNIQUE INDEX `uk_ops_comp_snapshot_active_daily` (`watch_product_id`, `subject_type`, `noon_product_code`, `active_fact_date`), ALGORITHM=INPLACE, LOCK=NONE'
  WHEN 'TARGET' THEN
    'SELECT ''migration_240_already_target'' AS migration_240_state'
  ELSE
    'SELECT `migration_240_unsupported_schema_state` FROM information_schema.tables'
END;
PREPARE migration_240_ddl FROM @cps_ddl;
EXECUTE migration_240_ddl;
DEALLOCATE PREPARE migration_240_ddl;

TRUNCATE TABLE `_migration_240_indexes`;
INSERT INTO `_migration_240_indexes`
SELECT index_name, MIN(non_unique), COUNT(*),
       GROUP_CONCAT(COALESCE(column_name, '<expression>')
         ORDER BY seq_in_index SEPARATOR ','),
       SUM(sub_part IS NOT NULL), SUM(UPPER(index_type) <> 'BTREE'),
       SUM(UPPER(is_visible) <> 'YES'), SUM(expression IS NOT NULL)
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'operations_competitor_product_snapshot'
GROUP BY index_name;
SET @cps_preserved_indexes_exact := (
    SELECT COUNT(*) = 6
    FROM `_migration_240_indexes`
    WHERE prefix_count = 0 AND non_btree_count = 0
      AND invisible_count = 0 AND expression_count = 0
      AND CONCAT(index_name, '|', non_unique, '|', column_count, '|',
                 column_signature) IN (
        'PRIMARY|0|1|id',
        'idx_ops_comp_snapshot_watch_date|1|2|watch_product_id,fact_date',
        'idx_ops_comp_snapshot_product_date|1|3|watch_product_id,competitor_product_id,fact_date',
        'idx_ops_comp_snapshot_code_date|1|3|site_code,noon_product_code,fact_date',
        'idx_ops_comp_snapshot_task|1|1|source_task_id',
        'idx_ops_comp_snapshot_run|1|1|source_run_id'
      )
);
SET @cps_index_count := (
    SELECT COUNT(*) FROM `_migration_240_indexes`
);
SET @cps_active_column_exact := (
    SELECT COUNT(*) = 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_product_snapshot'
      AND column_name = 'active_fact_date'
      AND column_type = 'date' AND is_nullable = 'YES'
      AND column_default IS NULL AND UPPER(extra) = 'VIRTUAL GENERATED'
      AND REGEXP_REPLACE(
        REGEXP_REPLACE(
          REPLACE(REPLACE(LOWER(generation_expression),
            '`is_deleted`', 'is_deleted'), '`fact_date`', 'fact_date'),
          '(_binary|b)?''\\\\?0''', '0'
        ),
        '[[:space:]]+', ''
      ) REGEXP
        '^[()]*casewhen[(]*is_deleted[)]*=[(]*(0|0b0|0x00)[)]*then[(]*fact_date[)]*elsenullend[()]*$'
);
SET @cps_postcheck_ok := (
  @cps_table_exact = 1 AND @cps_base_columns_exact = 1
  AND @cps_preserved_indexes_exact = 1 AND @cps_index_count = 7
  AND @cps_active_column_exact = 1
  AND NOT EXISTS(
    SELECT 1 FROM `_migration_240_indexes`
    WHERE index_name = 'uk_ops_comp_snapshot_daily'
       OR (non_unique = 0 AND column_count = 4
         AND column_signature =
           'watch_product_id,subject_type,noon_product_code,fact_date')
  )
  AND EXISTS(
    SELECT 1 FROM `_migration_240_indexes`
    WHERE index_name = 'uk_ops_comp_snapshot_active_daily'
      AND non_unique = 0 AND column_count = 4
      AND column_signature =
        'watch_product_id,subject_type,noon_product_code,active_fact_date'
      AND prefix_count = 0 AND non_btree_count = 0
      AND invisible_count = 0 AND expression_count = 0
  )
);
SET @cps_postcheck := IF(
  @cps_postcheck_ok = 1,
  'SELECT ''migration_240_target_verified'' AS migration_240_state',
  'SELECT `migration_240_postcheck_failed` FROM information_schema.tables'
);
PREPARE migration_240_verify FROM @cps_postcheck;
EXECUTE migration_240_verify;
DEALLOCATE PREPARE migration_240_verify;
DROP TEMPORARY TABLE `_migration_240_indexes`;
