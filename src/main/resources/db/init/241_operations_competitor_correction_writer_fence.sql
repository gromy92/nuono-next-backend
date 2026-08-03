-- Transaction-level writer fence for governed competitor history corrections.
-- Only an absent table (LEGACY) or the exact V1 contract (TARGET) is accepted.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout = 5;
SET SESSION lock_wait_timeout = 5;

SET @cwf_table_count := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
);
SET @cwf_table_exact := (
    SELECT COUNT(*) = 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
      AND table_type = 'BASE TABLE'
      AND UPPER(engine) = 'INNODB'
      AND table_collation = 'utf8mb4_unicode_ci'
      AND table_comment = 'competitor correction writer fence v1'
);
SET @cwf_columns_exact := (
    SELECT COUNT(*) = 10
      AND SUM(CASE
        WHEN ordinal_position = 1 AND column_name = 'fence_name'
          AND column_type = 'varchar(64)' AND is_nullable = 'NO'
          AND column_default IS NULL AND character_set_name = 'utf8mb4'
          AND collation_name = 'utf8mb4_unicode_ci'
        THEN 1
        WHEN ordinal_position = 2 AND column_name = 'generation'
          AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
          AND column_default = '0'
        THEN 1
        WHEN ordinal_position = 3 AND column_name = 'fence_status'
          AND column_type = 'varchar(16)' AND is_nullable = 'NO'
          AND column_default = 'OPEN' AND character_set_name = 'utf8mb4'
          AND collation_name = 'utf8mb4_unicode_ci'
        THEN 1
        WHEN ordinal_position = 4 AND column_name = 'operation_run_id'
          AND column_type = 'varchar(128)' AND is_nullable = 'YES'
          AND column_default IS NULL AND character_set_name = 'utf8mb4'
          AND collation_name = 'utf8mb4_unicode_ci'
        THEN 1
        WHEN ordinal_position = 5 AND column_name = 'activated_by'
          AND column_type = 'varchar(128)' AND is_nullable = 'YES'
          AND column_default IS NULL AND character_set_name = 'utf8mb4'
          AND collation_name = 'utf8mb4_unicode_ci'
        THEN 1
        WHEN ordinal_position = 6 AND column_name = 'activated_at'
          AND column_type = 'datetime' AND is_nullable = 'YES'
          AND column_default IS NULL
        THEN 1
        WHEN ordinal_position = 7 AND column_name = 'reopened_by'
          AND column_type = 'varchar(128)' AND is_nullable = 'YES'
          AND column_default IS NULL AND character_set_name = 'utf8mb4'
          AND collation_name = 'utf8mb4_unicode_ci'
        THEN 1
        WHEN ordinal_position = 8 AND column_name = 'reopened_at'
          AND column_type = 'datetime' AND is_nullable = 'YES'
          AND column_default IS NULL
        THEN 1
        WHEN ordinal_position = 9 AND column_name = 'gmt_create'
          AND column_type = 'datetime' AND is_nullable = 'NO'
          AND UPPER(column_default) LIKE 'CURRENT_TIMESTAMP%'
          AND UPPER(extra) LIKE '%DEFAULT_GENERATED%'
        THEN 1
        WHEN ordinal_position = 10 AND column_name = 'gmt_updated'
          AND column_type = 'datetime' AND is_nullable = 'NO'
          AND UPPER(column_default) LIKE 'CURRENT_TIMESTAMP%'
          AND UPPER(extra) LIKE '%DEFAULT_GENERATED%'
          AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%'
        THEN 1 ELSE 0 END) = 10
      AND SUM(generation_expression <> '') = 0
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
);
SET @cwf_index_exact := (
    SELECT COUNT(*) = 1
      AND SUM(index_name = 'PRIMARY' AND non_unique = 0
        AND seq_in_index = 1 AND column_name = 'fence_name'
        AND sub_part IS NULL AND UPPER(index_type) = 'BTREE'
        AND UPPER(is_visible) = 'YES' AND expression IS NULL) = 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
);
SET @cwf_constraints_exact := (
    SELECT COUNT(*) = 4
      AND SUM(constraint_name = 'PRIMARY'
        AND constraint_type = 'PRIMARY KEY') = 1
      AND SUM(constraint_name = 'chk_ops_comp_cwf_name'
        AND constraint_type = 'CHECK' AND enforced = 'YES') = 1
      AND SUM(constraint_name = 'chk_ops_comp_cwf_status'
        AND constraint_type = 'CHECK' AND enforced = 'YES') = 1
      AND SUM(constraint_name = 'chk_ops_comp_cwf_active_audit'
        AND constraint_type = 'CHECK' AND enforced = 'YES') = 1
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
);
SET @cwf_expected_audit_check :=
  'fence_status=''open''andgeneration=0andoperation_run_idisnullandactivated_byisnullandactivated_atisnullandreopened_byisnullandreopened_atisnullorfence_status=''active''andgeneration>0andoperation_run_idisnotnullandactivated_byisnotnullandactivated_atisnotnullandreopened_byisnullandreopened_atisnullorfence_status=''open''andgeneration>0andoperation_run_idisnotnullandactivated_byisnotnullandactivated_atisnotnullandreopened_byisnotnullandreopened_atisnotnull';
SET @cwf_checks_exact := (
    SELECT COUNT(*) = 3
      AND SUM(constraint_name = 'chk_ops_comp_cwf_name'
        AND REGEXP_REPLACE(
          REPLACE(REPLACE(LOWER(check_clause), '`', ''), '_utf8mb4', ''),
          '[[:space:]()]', ''
        ) = 'fence_name=''historical_business_date_correction''') = 1
      AND SUM(constraint_name = 'chk_ops_comp_cwf_status'
        AND REGEXP_REPLACE(
          REPLACE(REPLACE(LOWER(check_clause), '`', ''), '_utf8mb4', ''),
          '[[:space:]()]', ''
        ) = 'fence_statusin''open'',''active''') = 1
      AND SUM(constraint_name = 'chk_ops_comp_cwf_active_audit'
        AND REGEXP_REPLACE(
          REPLACE(REPLACE(LOWER(check_clause), '`', ''), '_utf8mb4', ''),
          '[[:space:]()]', ''
        ) = @cwf_expected_audit_check) = 1
    FROM information_schema.check_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_name IN (
        'chk_ops_comp_cwf_name',
        'chk_ops_comp_cwf_status',
        'chk_ops_comp_cwf_active_audit'
      )
);
SET @cwf_row_count := 0;
SET @cwf_row_exact := 0;
SET @cwf_row_probe := IF(
  @cwf_table_count = 1,
  'SELECT COUNT(*), COALESCE(SUM(`fence_name` = ''HISTORICAL_BUSINESS_DATE_CORRECTION'' AND ((`fence_status` = ''OPEN'' AND `generation` = 0 AND `operation_run_id` IS NULL AND `activated_by` IS NULL AND `activated_at` IS NULL AND `reopened_by` IS NULL AND `reopened_at` IS NULL) OR (`fence_status` = ''ACTIVE'' AND `generation` > 0 AND `operation_run_id` IS NOT NULL AND `activated_by` IS NOT NULL AND `activated_at` IS NOT NULL AND `reopened_by` IS NULL AND `reopened_at` IS NULL) OR (`fence_status` = ''OPEN'' AND `generation` > 0 AND `operation_run_id` IS NOT NULL AND `activated_by` IS NOT NULL AND `activated_at` IS NOT NULL AND `reopened_by` IS NOT NULL AND `reopened_at` IS NOT NULL))), 0) = 1 INTO @cwf_row_count, @cwf_row_exact FROM `operations_competitor_correction_writer_fence`',
  'SET @cwf_row_count := 0, @cwf_row_exact := 0'
);
PREPARE migration_241_row_probe FROM @cwf_row_probe;
EXECUTE migration_241_row_probe;
DEALLOCATE PREPARE migration_241_row_probe;

SET @cwf_state := CASE
  WHEN @cwf_table_count = 0 THEN 'LEGACY'
  WHEN @cwf_table_count = 1 AND @cwf_table_exact = 1
    AND @cwf_columns_exact = 1 AND @cwf_index_exact = 1
    AND @cwf_constraints_exact = 1 AND @cwf_checks_exact = 1
    AND @cwf_row_count = 0
    THEN 'TARGET_EMPTY'
  WHEN @cwf_table_count = 1 AND @cwf_table_exact = 1
    AND @cwf_columns_exact = 1 AND @cwf_index_exact = 1
    AND @cwf_constraints_exact = 1 AND @cwf_checks_exact = 1
    AND @cwf_row_count = 1 AND @cwf_row_exact = 1
    THEN 'TARGET'
  ELSE 'DRIFT'
END;
SET @cwf_ddl := CASE @cwf_state
  WHEN 'LEGACY' THEN
    'CREATE TABLE `operations_competitor_correction_writer_fence` (`fence_name` VARCHAR(64) NOT NULL, `generation` BIGINT UNSIGNED NOT NULL DEFAULT 0, `fence_status` VARCHAR(16) NOT NULL DEFAULT ''OPEN'', `operation_run_id` VARCHAR(128) DEFAULT NULL, `activated_by` VARCHAR(128) DEFAULT NULL, `activated_at` DATETIME DEFAULT NULL, `reopened_by` VARCHAR(128) DEFAULT NULL, `reopened_at` DATETIME DEFAULT NULL, `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`fence_name`), CONSTRAINT `chk_ops_comp_cwf_name` CHECK (`fence_name` = ''HISTORICAL_BUSINESS_DATE_CORRECTION''), CONSTRAINT `chk_ops_comp_cwf_status` CHECK (`fence_status` IN (''OPEN'', ''ACTIVE'')), CONSTRAINT `chk_ops_comp_cwf_active_audit` CHECK ((`fence_status` = ''OPEN'' AND `generation` = 0 AND `operation_run_id` IS NULL AND `activated_by` IS NULL AND `activated_at` IS NULL AND `reopened_by` IS NULL AND `reopened_at` IS NULL) OR (`fence_status` = ''ACTIVE'' AND `generation` > 0 AND `operation_run_id` IS NOT NULL AND `activated_by` IS NOT NULL AND `activated_at` IS NOT NULL AND `reopened_by` IS NULL AND `reopened_at` IS NULL) OR (`fence_status` = ''OPEN'' AND `generation` > 0 AND `operation_run_id` IS NOT NULL AND `activated_by` IS NOT NULL AND `activated_at` IS NOT NULL AND `reopened_by` IS NOT NULL AND `reopened_at` IS NOT NULL))) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''competitor correction writer fence v1'''
  WHEN 'TARGET_EMPTY' THEN
    'SELECT ''migration_241_exact_empty'' AS migration_241_state'
  WHEN 'TARGET' THEN
    'SELECT ''migration_241_already_target'' AS migration_241_state'
  ELSE
    'SELECT `migration_241_unsupported_schema_drift` FROM information_schema.tables'
END;
PREPARE migration_241_ddl FROM @cwf_ddl;
EXECUTE migration_241_ddl;
DEALLOCATE PREPARE migration_241_ddl;

SET @cwf_seed := IF(
  @cwf_state IN ('LEGACY', 'TARGET_EMPTY'),
  'INSERT INTO `operations_competitor_correction_writer_fence` (`fence_name`, `generation`, `fence_status`) VALUES (''HISTORICAL_BUSINESS_DATE_CORRECTION'', 0, ''OPEN'')',
  'SELECT ''migration_241_seed_not_required'' AS migration_241_seed_state'
);
PREPARE migration_241_seed FROM @cwf_seed;
EXECUTE migration_241_seed;
DEALLOCATE PREPARE migration_241_seed;

SET @cwf_post_table := (
    SELECT COUNT(*) = 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
      AND table_type = 'BASE TABLE' AND UPPER(engine) = 'INNODB'
      AND table_collation = 'utf8mb4_unicode_ci'
      AND table_comment = 'competitor correction writer fence v1'
);
SET @cwf_post_columns := (
    SELECT COUNT(*) = 10
      AND SUM(column_name IN (
        'fence_name', 'generation', 'fence_status', 'operation_run_id',
        'activated_by', 'activated_at', 'reopened_by', 'reopened_at',
        'gmt_create', 'gmt_updated'
      )) = 10
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
);
SET @cwf_post_index := (
    SELECT COUNT(*) = 1
      AND SUM(index_name = 'PRIMARY' AND non_unique = 0
        AND seq_in_index = 1 AND column_name = 'fence_name'
        AND sub_part IS NULL AND UPPER(index_type) = 'BTREE'
        AND UPPER(is_visible) = 'YES' AND expression IS NULL) = 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
);
SET @cwf_post_constraints := (
    SELECT COUNT(*) = 4
      AND SUM(constraint_name = 'PRIMARY'
        AND constraint_type = 'PRIMARY KEY') = 1
      AND SUM(constraint_name IN (
        'chk_ops_comp_cwf_name',
        'chk_ops_comp_cwf_status',
        'chk_ops_comp_cwf_active_audit'
      ) AND constraint_type = 'CHECK' AND enforced = 'YES') = 3
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'operations_competitor_correction_writer_fence'
);
SET @cwf_post_checks := (
    SELECT COUNT(*) = 3
      AND SUM(constraint_name = 'chk_ops_comp_cwf_name'
        AND check_clause LIKE '%HISTORICAL_BUSINESS_DATE_CORRECTION%') = 1
      AND SUM(constraint_name = 'chk_ops_comp_cwf_status'
        AND check_clause LIKE '%OPEN%' AND check_clause LIKE '%ACTIVE%') = 1
      AND SUM(constraint_name = 'chk_ops_comp_cwf_active_audit'
        AND REGEXP_REPLACE(
          REPLACE(REPLACE(LOWER(check_clause), '`', ''), '_utf8mb4', ''),
          '[[:space:]()]', ''
        ) = @cwf_expected_audit_check) = 1
    FROM information_schema.check_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_name IN (
        'chk_ops_comp_cwf_name',
        'chk_ops_comp_cwf_status',
        'chk_ops_comp_cwf_active_audit'
      )
);
SET @cwf_post_row_sql :=
  'SELECT COUNT(*) = 1 AND COALESCE(SUM(`fence_name` = ''HISTORICAL_BUSINESS_DATE_CORRECTION'' AND ((`fence_status` = ''OPEN'' AND `generation` = 0 AND `operation_run_id` IS NULL AND `activated_by` IS NULL AND `activated_at` IS NULL AND `reopened_by` IS NULL AND `reopened_at` IS NULL) OR (`fence_status` = ''ACTIVE'' AND `generation` > 0 AND `operation_run_id` IS NOT NULL AND `activated_by` IS NOT NULL AND `activated_at` IS NOT NULL AND `reopened_by` IS NULL AND `reopened_at` IS NULL) OR (`fence_status` = ''OPEN'' AND `generation` > 0 AND `operation_run_id` IS NOT NULL AND `activated_by` IS NOT NULL AND `activated_at` IS NOT NULL AND `reopened_by` IS NOT NULL AND `reopened_at` IS NOT NULL))), 0) = 1 INTO @cwf_post_row FROM `operations_competitor_correction_writer_fence`';
PREPARE migration_241_post_row FROM @cwf_post_row_sql;
EXECUTE migration_241_post_row;
DEALLOCATE PREPARE migration_241_post_row;
SET @cwf_postcheck := CASE
  WHEN @cwf_post_table <> 1 THEN
    'SELECT `migration_241_post_table_failed` FROM information_schema.tables'
  WHEN @cwf_post_columns <> 1 THEN
    'SELECT `migration_241_post_columns_failed` FROM information_schema.tables'
  WHEN @cwf_post_index <> 1 THEN
    'SELECT `migration_241_post_index_failed` FROM information_schema.tables'
  WHEN @cwf_post_constraints <> 1 THEN
    'SELECT `migration_241_post_constraints_failed` FROM information_schema.tables'
  WHEN @cwf_post_checks <> 1 THEN
    'SELECT `migration_241_post_checks_failed` FROM information_schema.tables'
  WHEN @cwf_post_row <> 1 THEN
    'SELECT `migration_241_post_row_failed` FROM information_schema.tables'
  ELSE 'SELECT ''migration_241_target_verified'' AS migration_241_state'
END;
PREPARE migration_241_verify FROM @cwf_postcheck;
EXECUTE migration_241_verify;
DEALLOCATE PREPARE migration_241_verify;
