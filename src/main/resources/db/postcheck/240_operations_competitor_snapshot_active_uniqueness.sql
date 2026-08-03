SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'operations_competitor_product_snapshot'
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
          AND table_collation = 'utf8mb4_unicode_ci'
    ) = 1
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'operations_competitor_product_snapshot'
          AND extra = '' AND generation_expression = ''
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
    ) = 6
    AND (
        SELECT COUNT(*)
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
                  '(_binary|b)?''\\?0''', '0'
              ),
              '[[:space:]]+', ''
          ) REGEXP
            '^[()]*casewhen[(]*is_deleted[)]*=[(]*(0|0b0|0x00)[)]*then[(]*fact_date[)]*elsenullend[()]*$'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 7
            AND SUM(
                prefix_count = 0 AND non_btree_count = 0
                AND invisible_count = 0 AND expression_count = 0
                AND CONCAT(index_name, '|', non_unique, '|', column_count, '|',
                           column_signature) IN (
                    'PRIMARY|0|1|id',
                    'idx_ops_comp_snapshot_watch_date|1|2|watch_product_id,fact_date',
                    'idx_ops_comp_snapshot_product_date|1|3|watch_product_id,competitor_product_id,fact_date',
                    'idx_ops_comp_snapshot_code_date|1|3|site_code,noon_product_code,fact_date',
                    'idx_ops_comp_snapshot_task|1|1|source_task_id',
                    'idx_ops_comp_snapshot_run|1|1|source_run_id',
                    'uk_ops_comp_snapshot_active_daily|0|4|watch_product_id,subject_type,noon_product_code,active_fact_date'
                )
            ) = 7,
            1,
            0
        )
        FROM (
            SELECT index_name, MIN(non_unique) AS non_unique,
                   COUNT(*) AS column_count,
                   GROUP_CONCAT(COALESCE(column_name, '<expression>')
                     ORDER BY seq_in_index SEPARATOR ',') AS column_signature,
                   SUM(sub_part IS NOT NULL) AS prefix_count,
                   SUM(UPPER(index_type) <> 'BTREE') AS non_btree_count,
                   SUM(UPPER(is_visible) <> 'YES') AS invisible_count,
                   SUM(expression IS NOT NULL) AS expression_count
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'operations_competitor_product_snapshot'
            GROUP BY index_name
        ) indexes_exact
    ) = 1
    AND NOT EXISTS (
        SELECT 1
        FROM operations_competitor_product_snapshot
        WHERE is_deleted = b'0'
        GROUP BY watch_product_id, subject_type, noon_product_code, fact_date
        HAVING COUNT(*) > 1
    ),
    1,
    0
);
