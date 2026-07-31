SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'nuono_schema_migration',
              'nuono_schema_migration_attempt'
          )
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
          AND table_collation = 'utf8mb4_unicode_ci'
    ) = 2
    AND (
        SELECT GROUP_CONCAT(
            CONCAT(
                ordinal_position,
                ':',
                column_name,
                ':',
                LOWER(column_type),
                ':',
                is_nullable
            )
            ORDER BY ordinal_position
            SEPARATOR '|'
        )
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration'
    ) = CONCAT(
        '1:migration_key:varchar(191):NO|',
        '2:script_path:varchar(255):NO|',
        '3:checksum_sha256:char(64):NO|',
        '4:postcheck_sha256:char(64):NO|',
        '5:state:varchar(16):NO|',
        '6:release_commit:char(40):NO|',
        '7:attempt_no:int unsigned:NO|',
        '8:started_at:datetime(6):NO|',
        '9:finished_at:datetime(6):YES|',
        '10:installed_by:varchar(128):NO|',
        '11:error_code:varchar(64):YES|',
        '12:error_digest:char(64):YES|',
        '13:error_summary:varchar(1000):YES|',
        '14:gmt_create:datetime(6):NO|',
        '15:gmt_updated:datetime(6):NO'
    )
    AND (
        SELECT GROUP_CONCAT(
            CONCAT(
                ordinal_position,
                ':',
                column_name,
                ':',
                LOWER(column_type),
                ':',
                is_nullable
            )
            ORDER BY ordinal_position
            SEPARATOR '|'
        )
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration_attempt'
    ) = CONCAT(
        '1:id:bigint unsigned:NO|',
        '2:migration_key:varchar(191):NO|',
        '3:attempt_no:int unsigned:NO|',
        '4:checksum_sha256:char(64):NO|',
        '5:postcheck_sha256:char(64):NO|',
        '6:state:varchar(16):NO|',
        '7:operation:varchar(16):NO|',
        '8:reconciles_attempt_no:int unsigned:YES|',
        '9:release_commit:char(40):NO|',
        '10:started_at:datetime(6):NO|',
        '11:finished_at:datetime(6):YES|',
        '12:installed_by:varchar(128):NO|',
        '13:error_code:varchar(64):YES|',
        '14:error_digest:char(64):YES|',
        '15:error_summary:varchar(1000):YES|',
        '16:gmt_create:datetime(6):NO'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration'
          AND column_name = 'gmt_create'
          AND UPPER(column_default) = 'CURRENT_TIMESTAMP(6)'
          AND LOWER(extra) = 'default_generated'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration'
          AND column_name = 'gmt_updated'
          AND UPPER(column_default) = 'CURRENT_TIMESTAMP(6)'
          AND LOWER(extra) =
              'default_generated on update current_timestamp(6)'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration_attempt'
          AND column_name = 'id'
          AND column_default IS NULL
          AND LOWER(extra) = 'auto_increment'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration_attempt'
          AND column_name = 'gmt_create'
          AND UPPER(column_default) = 'CURRENT_TIMESTAMP(6)'
          AND LOWER(extra) = 'default_generated'
    )
    AND (
        SELECT IF(
            COUNT(*) = 1
            AND MIN(non_unique) = 0
            AND SUM(sub_part IS NULL) = 1
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MIN(collation) = 'A'
            AND SUM(is_visible = 'YES') = 1
            AND SUM(expression IS NULL) = 1
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:migration_key',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration'
          AND index_name = 'PRIMARY'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 2
            AND MIN(non_unique) = 1
            AND MAX(non_unique) = 1
            AND SUM(sub_part IS NULL) = 2
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MAX(UPPER(index_type)) = 'BTREE'
            AND MIN(collation) = 'A'
            AND MAX(collation) = 'A'
            AND SUM(is_visible = 'YES') = 2
            AND SUM(expression IS NULL) = 2
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:state,2:started_at',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration'
          AND index_name = 'idx_nuono_schema_migration_state'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 1
            AND MIN(non_unique) = 1
            AND SUM(sub_part IS NULL) = 1
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MIN(collation) = 'A'
            AND SUM(is_visible = 'YES') = 1
            AND SUM(expression IS NULL) = 1
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:release_commit',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration'
          AND index_name = 'idx_nuono_schema_migration_commit'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 1
            AND MIN(non_unique) = 0
            AND SUM(sub_part IS NULL) = 1
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MIN(collation) = 'A'
            AND SUM(is_visible = 'YES') = 1
            AND SUM(expression IS NULL) = 1
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:id',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration_attempt'
          AND index_name = 'PRIMARY'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 2
            AND MIN(non_unique) = 0
            AND MAX(non_unique) = 0
            AND SUM(sub_part IS NULL) = 2
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MAX(UPPER(index_type)) = 'BTREE'
            AND MIN(collation) = 'A'
            AND MAX(collation) = 'A'
            AND SUM(is_visible = 'YES') = 2
            AND SUM(expression IS NULL) = 2
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:migration_key,2:attempt_no',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration_attempt'
          AND index_name = 'uk_nuono_schema_migration_attempt'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 2
            AND MIN(non_unique) = 1
            AND MAX(non_unique) = 1
            AND SUM(sub_part IS NULL) = 2
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MAX(UPPER(index_type)) = 'BTREE'
            AND MIN(collation) = 'A'
            AND MAX(collation) = 'A'
            AND SUM(is_visible = 'YES') = 2
            AND SUM(expression IS NULL) = 2
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:state,2:started_at',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'nuono_schema_migration_attempt'
          AND index_name = 'idx_nuono_schema_migration_attempt_state'
    ) = 1,
    1,
    0
);
