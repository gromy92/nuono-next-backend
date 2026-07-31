WITH
required_tables (`table_name`, `table_collation`) AS (
    VALUES
        ROW('noon_pull_id_sequence', 'utf8mb4_unicode_ci'),
        ROW('noon_pull_smoke_run', 'utf8mb4_unicode_ci'),
        ROW('noon_pull_smoke_evidence', 'utf8mb4_unicode_ci'),
        ROW('noon_production_scheduler_enablement', 'utf8mb4_unicode_ci')
),
expected_columns (
    `table_name`,
    `ordinal_position`,
    `column_name`,
    `column_type`,
    `is_nullable`,
    `default_value`,
    `extra_value`
) AS (
    VALUES
        ROW('noon_pull_id_sequence', 1, 'sequence_name', 'varchar(100)', 'NO', '<null>', ''),
        ROW('noon_pull_id_sequence', 2, 'next_id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_pull_id_sequence', 3, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('noon_pull_id_sequence', 4, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp'),

        ROW('noon_pull_smoke_run', 1, 'id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_pull_smoke_run', 2, 'target_environment', 'varchar(64)', 'NO', '<null>', ''),
        ROW('noon_pull_smoke_run', 3, 'owner_user_id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_pull_smoke_run', 4, 'project_code', 'varchar(100)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_run', 5, 'project_name', 'varchar(255)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_run', 6, 'store_code', 'varchar(100)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_run', 7, 'site_code', 'varchar(32)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_run', 8, 'rollback_global_pause_strategy', 'varchar(1000)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_run', 9, 'requested_domains', 'varchar(255)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_run', 10, 'missing_requirements', 'varchar(1000)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_run', 11, 'evidence_gate_satisfied', 'bit(1)', 'NO', '0', ''),
        ROW('noon_pull_smoke_run', 12, 'production_scheduling_allowed', 'bit(1)', 'NO', '0', ''),
        ROW('noon_pull_smoke_run', 13, 'is_deleted', 'bit(1)', 'NO', '0', ''),
        ROW('noon_pull_smoke_run', 14, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('noon_pull_smoke_run', 15, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp'),

        ROW('noon_pull_smoke_evidence', 1, 'id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 2, 'run_id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 3, 'sequence_no', 'int', 'NO', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 4, 'data_domain', 'varchar(32)', 'NO', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 5, 'target_identity', 'varchar(255)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 6, 'date_from', 'date', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 7, 'date_to', 'date', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 8, 'row_or_item_count', 'int', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 9, 'task_id', 'bigint', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 10, 'source_batch_id', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 11, 'file_digest_sha256', 'varchar(128)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 12, 'request_count', 'int', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 13, 'elapsed_millis', 'bigint', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 14, 'latest_fact_date', 'date', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 15, 'status', 'varchar(32)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 16, 'quality_state', 'varchar(64)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 17, 'failure_classification', 'varchar(80)', 'YES', '<null>', ''),
        ROW('noon_pull_smoke_evidence', 18, 'is_deleted', 'bit(1)', 'NO', '0', ''),
        ROW('noon_pull_smoke_evidence', 19, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('noon_pull_smoke_evidence', 20, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp'),

        ROW('noon_production_scheduler_enablement', 1, 'id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 2, 'target_environment', 'varchar(64)', 'NO', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 3, 'owner_user_id', 'bigint', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 4, 'project_code', 'varchar(100)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 5, 'project_name', 'varchar(255)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 6, 'store_code', 'varchar(100)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 7, 'site_code', 'varchar(32)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 8, 'enabled_domains', 'varchar(255)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 9, 'schedule_boundaries', 'varchar(1000)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 10, 'rollback_global_pause_strategy', 'varchar(1000)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 11, 'operator_user_id', 'bigint', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 12, 'smoke_run_id', 'bigint', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 13, 'decision', 'varchar(32)', 'NO', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 14, 'rejection_reasons', 'varchar(1000)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 15, 'plan_ids', 'varchar(500)', 'YES', '<null>', ''),
        ROW('noon_production_scheduler_enablement', 16, 'hitl_approved', 'bit(1)', 'NO', '0', ''),
        ROW('noon_production_scheduler_enablement', 17, 'is_deleted', 'bit(1)', 'NO', '0', ''),
        ROW('noon_production_scheduler_enablement', 18, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('noon_production_scheduler_enablement', 19, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp')
),
expected_indexes (
    `table_name`,
    `index_name`,
    `non_unique`,
    `seq_in_index`,
    `column_name`,
    `index_type`
) AS (
    VALUES
        ROW('noon_pull_id_sequence', 'PRIMARY', 0, 1, 'sequence_name', 'BTREE'),

        ROW('noon_pull_smoke_run', 'PRIMARY', 0, 1, 'id', 'BTREE'),
        ROW('noon_pull_smoke_run', 'idx_noon_pull_smoke_run_scope', 1, 1, 'target_environment', 'BTREE'),
        ROW('noon_pull_smoke_run', 'idx_noon_pull_smoke_run_scope', 1, 2, 'owner_user_id', 'BTREE'),
        ROW('noon_pull_smoke_run', 'idx_noon_pull_smoke_run_scope', 1, 3, 'store_code', 'BTREE'),
        ROW('noon_pull_smoke_run', 'idx_noon_pull_smoke_run_scope', 1, 4, 'site_code', 'BTREE'),
        ROW('noon_pull_smoke_run', 'idx_noon_pull_smoke_run_created', 1, 1, 'gmt_create', 'BTREE'),

        ROW('noon_pull_smoke_evidence', 'PRIMARY', 0, 1, 'id', 'BTREE'),
        ROW('noon_pull_smoke_evidence', 'idx_noon_pull_smoke_evidence_run', 1, 1, 'run_id', 'BTREE'),
        ROW('noon_pull_smoke_evidence', 'idx_noon_pull_smoke_evidence_run', 1, 2, 'sequence_no', 'BTREE'),
        ROW('noon_pull_smoke_evidence', 'idx_noon_pull_smoke_evidence_task', 1, 1, 'task_id', 'BTREE'),
        ROW('noon_pull_smoke_evidence', 'idx_noon_pull_smoke_evidence_batch', 1, 1, 'source_batch_id', 'BTREE'),

        ROW('noon_production_scheduler_enablement', 'PRIMARY', 0, 1, 'id', 'BTREE'),
        ROW('noon_production_scheduler_enablement', 'idx_noon_scheduler_enablement_scope', 1, 1, 'target_environment', 'BTREE'),
        ROW('noon_production_scheduler_enablement', 'idx_noon_scheduler_enablement_scope', 1, 2, 'owner_user_id', 'BTREE'),
        ROW('noon_production_scheduler_enablement', 'idx_noon_scheduler_enablement_scope', 1, 3, 'store_code', 'BTREE'),
        ROW('noon_production_scheduler_enablement', 'idx_noon_scheduler_enablement_scope', 1, 4, 'site_code', 'BTREE'),
        ROW('noon_production_scheduler_enablement', 'idx_noon_scheduler_enablement_smoke', 1, 1, 'smoke_run_id', 'BTREE'),
        ROW('noon_production_scheduler_enablement', 'idx_noon_scheduler_enablement_decision', 1, 1, 'decision', 'BTREE'),
        ROW('noon_production_scheduler_enablement', 'idx_noon_scheduler_enablement_decision', 1, 2, 'gmt_create', 'BTREE')
)
SELECT IF(
    (
        SELECT COUNT(*)
        FROM required_tables required
        JOIN information_schema.tables actual
          ON actual.table_schema = DATABASE()
         AND BINARY actual.table_name = BINARY required.table_name
        WHERE actual.table_type = 'BASE TABLE'
          AND actual.engine = 'InnoDB'
          AND actual.table_collation = required.table_collation
    ) = (SELECT COUNT(*) FROM required_tables)
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns actual
        JOIN required_tables required
          ON BINARY required.table_name = BINARY actual.table_name
        WHERE actual.table_schema = DATABASE()
    ) = (SELECT COUNT(*) FROM expected_columns)
    AND NOT EXISTS (
        SELECT 1
        FROM expected_columns expected
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema = DATABASE()
         AND BINARY actual.table_name = BINARY expected.table_name
         AND BINARY actual.column_name = BINARY expected.column_name
        WHERE actual.column_name IS NULL
           OR actual.ordinal_position <> expected.ordinal_position
           OR BINARY LOWER(actual.column_type) <> BINARY expected.column_type
           OR actual.is_nullable <> expected.is_nullable
           OR (
                CASE
                    WHEN actual.column_default IS NULL THEN '<null>'
                    WHEN actual.data_type = 'bit'
                     AND UPPER(HEX(actual.column_default)) IN (
                         '00', '30', '62273027', '42273027'
                     ) THEN '0'
                    WHEN actual.data_type IN (
                        'tinyint', 'smallint', 'mediumint', 'int', 'bigint',
                        'decimal', 'numeric', 'float', 'double'
                    )
                     AND CAST(actual.column_default AS DECIMAL(65, 30)) = 0
                        THEN '0'
                    WHEN LOWER(CAST(actual.column_default AS CHAR)) IN (
                        'current_timestamp', 'current_timestamp()'
                    ) THEN 'current_timestamp'
                    ELSE LOWER(CAST(actual.column_default AS CHAR))
                END
              ) <> expected.default_value
           OR TRIM(
                REPLACE(
                    LOWER(COALESCE(actual.extra, '')),
                    'default_generated',
                    ''
                )
              ) <> expected.extra_value
    )
    AND (
        SELECT COUNT(*)
        FROM information_schema.statistics actual
        JOIN (
            SELECT DISTINCT table_name, index_name
            FROM expected_indexes
        ) required
          ON BINARY required.table_name = BINARY actual.table_name
         AND BINARY required.index_name = BINARY actual.index_name
        WHERE actual.table_schema = DATABASE()
    ) = (SELECT COUNT(*) FROM expected_indexes)
    AND NOT EXISTS (
        SELECT 1
        FROM expected_indexes expected
        LEFT JOIN information_schema.statistics actual
          ON actual.table_schema = DATABASE()
         AND BINARY actual.table_name = BINARY expected.table_name
         AND BINARY actual.index_name = BINARY expected.index_name
         AND actual.seq_in_index = expected.seq_in_index
        WHERE actual.index_name IS NULL
           OR actual.column_name IS NULL
           OR actual.non_unique <> expected.non_unique
           OR BINARY actual.column_name <> BINARY expected.column_name
           OR actual.sub_part IS NOT NULL
           OR UPPER(actual.index_type) <> expected.index_type
           OR actual.collation <> 'A'
           OR actual.is_visible <> 'YES'
           OR actual.`expression` IS NOT NULL
    )
    AND EXISTS (
        SELECT 1
        FROM `noon_pull_id_sequence` sequence_row
        WHERE sequence_row.`sequence_name` = 'noon_pull_smoke_run'
          AND sequence_row.`next_id` >= GREATEST(
              139999,
              COALESCE((SELECT MAX(`id`) FROM `noon_pull_smoke_run`), 139999)
          )
    )
    AND EXISTS (
        SELECT 1
        FROM `noon_pull_id_sequence` sequence_row
        WHERE sequence_row.`sequence_name` = 'noon_pull_smoke_evidence'
          AND sequence_row.`next_id` >= GREATEST(
              140999,
              COALESCE(
                  (SELECT MAX(`id`) FROM `noon_pull_smoke_evidence`),
                  140999
              )
          )
    )
    AND EXISTS (
        SELECT 1
        FROM `noon_pull_id_sequence` sequence_row
        WHERE sequence_row.`sequence_name`
              = 'noon_production_scheduler_enablement'
          AND sequence_row.`next_id` >= GREATEST(
              141999,
              COALESCE(
                  (
                      SELECT MAX(`id`)
                      FROM `noon_production_scheduler_enablement`
                  ),
                  141999
              )
          )
    ),
    1,
    0
);
