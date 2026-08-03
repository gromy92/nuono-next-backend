SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'operations_competitor_correction_writer_fence'
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
          AND table_collation = 'utf8mb4_unicode_ci'
          AND table_comment = 'competitor correction writer fence v1'
    ) = 1
    AND (
        SELECT COUNT(*) = 10
          AND SUM(CASE
            WHEN ordinal_position = 1 AND column_name = 'fence_name'
              AND column_type = 'varchar(64)' AND is_nullable = 'NO'
              AND column_default IS NULL AND character_set_name = 'utf8mb4'
              AND collation_name = 'utf8mb4_unicode_ci' THEN 1
            WHEN ordinal_position = 2 AND column_name = 'generation'
              AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
              AND column_default = '0' THEN 1
            WHEN ordinal_position = 3 AND column_name = 'fence_status'
              AND column_type = 'varchar(16)' AND is_nullable = 'NO'
              AND column_default = 'OPEN' AND character_set_name = 'utf8mb4'
              AND collation_name = 'utf8mb4_unicode_ci' THEN 1
            WHEN ordinal_position = 4 AND column_name = 'operation_run_id'
              AND column_type = 'varchar(128)' AND is_nullable = 'YES'
              AND column_default IS NULL AND character_set_name = 'utf8mb4'
              AND collation_name = 'utf8mb4_unicode_ci' THEN 1
            WHEN ordinal_position = 5 AND column_name = 'activated_by'
              AND column_type = 'varchar(128)' AND is_nullable = 'YES'
              AND column_default IS NULL AND character_set_name = 'utf8mb4'
              AND collation_name = 'utf8mb4_unicode_ci' THEN 1
            WHEN ordinal_position = 6 AND column_name = 'activated_at'
              AND column_type = 'datetime' AND is_nullable = 'YES'
              AND column_default IS NULL THEN 1
            WHEN ordinal_position = 7 AND column_name = 'reopened_by'
              AND column_type = 'varchar(128)' AND is_nullable = 'YES'
              AND column_default IS NULL AND character_set_name = 'utf8mb4'
              AND collation_name = 'utf8mb4_unicode_ci' THEN 1
            WHEN ordinal_position = 8 AND column_name = 'reopened_at'
              AND column_type = 'datetime' AND is_nullable = 'YES'
              AND column_default IS NULL THEN 1
            WHEN ordinal_position = 9 AND column_name = 'gmt_create'
              AND column_type = 'datetime' AND is_nullable = 'NO'
              AND UPPER(column_default) LIKE 'CURRENT_TIMESTAMP%'
              AND UPPER(extra) LIKE '%DEFAULT_GENERATED%' THEN 1
            WHEN ordinal_position = 10 AND column_name = 'gmt_updated'
              AND column_type = 'datetime' AND is_nullable = 'NO'
              AND UPPER(column_default) LIKE 'CURRENT_TIMESTAMP%'
              AND UPPER(extra) LIKE '%DEFAULT_GENERATED%'
              AND UPPER(extra) LIKE '%ON UPDATE CURRENT_TIMESTAMP%' THEN 1
            ELSE 0 END) = 10
          AND SUM(generation_expression <> '') = 0
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'operations_competitor_correction_writer_fence'
    ) = 1
    AND (
        SELECT COUNT(*) = 1
          AND SUM(index_name = 'PRIMARY' AND non_unique = 0
            AND seq_in_index = 1 AND column_name = 'fence_name'
            AND sub_part IS NULL AND UPPER(index_type) = 'BTREE'
            AND UPPER(is_visible) = 'YES' AND expression IS NULL) = 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'operations_competitor_correction_writer_fence'
    ) = 1
    AND (
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
    ) = 1
    AND (
        SELECT COUNT(*) = 3
          AND SUM(constraint_name = 'chk_ops_comp_cwf_name'
            AND REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(LOWER(check_clause), '`', ''),
                '_utf8mb4', ''), CONCAT(CHAR(92), CHAR(39)), CHAR(39)),
              '[[:space:]()]', ''
            ) = 'fence_name=''historical_business_date_correction''') = 1
          AND SUM(constraint_name = 'chk_ops_comp_cwf_status'
            AND REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(LOWER(check_clause), '`', ''),
                '_utf8mb4', ''), CONCAT(CHAR(92), CHAR(39)), CHAR(39)),
              '[[:space:]()]', ''
            ) = 'fence_statusin''open'',''active''') = 1
          AND SUM(constraint_name = 'chk_ops_comp_cwf_active_audit'
            AND REGEXP_REPLACE(
              REPLACE(REPLACE(REPLACE(LOWER(check_clause), '`', ''),
                '_utf8mb4', ''), CONCAT(CHAR(92), CHAR(39)), CHAR(39)),
              '[[:space:]()]', ''
            ) = 'fence_status=''open''andgeneration=0andoperation_run_idisnullandactivated_byisnullandactivated_atisnullandreopened_byisnullandreopened_atisnullorfence_status=''active''andgeneration>0andoperation_run_idisnotnullandactivated_byisnotnullandactivated_atisnotnullandreopened_byisnullandreopened_atisnullorfence_status=''open''andgeneration>0andoperation_run_idisnotnullandactivated_byisnotnullandactivated_atisnotnullandreopened_byisnotnullandreopened_atisnotnull') = 1
        FROM information_schema.check_constraints
        WHERE constraint_schema = DATABASE()
          AND constraint_name IN (
              'chk_ops_comp_cwf_name',
              'chk_ops_comp_cwf_status',
              'chk_ops_comp_cwf_active_audit'
          )
    ) = 1
    AND (
        SELECT COUNT(*) = 1
          AND COALESCE(SUM(
            fence_name = 'HISTORICAL_BUSINESS_DATE_CORRECTION'
            AND (
              (fence_status = 'OPEN' AND generation = 0
                AND operation_run_id IS NULL AND activated_by IS NULL
                AND activated_at IS NULL AND reopened_by IS NULL
                AND reopened_at IS NULL)
              OR (fence_status = 'ACTIVE' AND generation > 0
                AND operation_run_id IS NOT NULL AND activated_by IS NOT NULL
                AND activated_at IS NOT NULL AND reopened_by IS NULL
                AND reopened_at IS NULL)
              OR (fence_status = 'OPEN' AND generation > 0
                AND operation_run_id IS NOT NULL AND activated_by IS NOT NULL
                AND activated_at IS NOT NULL AND reopened_by IS NOT NULL
                AND reopened_at IS NOT NULL)
            )
          ), 0) = 1
        FROM operations_competitor_correction_writer_fence
    ) = 1,
    1,
    0
);
