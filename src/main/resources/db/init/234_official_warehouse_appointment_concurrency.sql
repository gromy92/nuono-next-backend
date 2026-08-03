-- Fence appointment execution and active uniqueness; duplicates/in-flight rows require reconciliation.
SET SESSION `lock_wait_timeout` = 5;
SET SESSION `innodb_lock_wait_timeout` = 5;
SET @appointment_table_count := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'official_warehouse_appointment'
      AND table_type = 'BASE TABLE' AND UPPER(engine) = 'INNODB'
      AND UPPER(row_format) IN ('DYNAMIC', 'COMPRESSED')
);
SET @appointment_parent_table_count := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'official_warehouse_asn'
      AND table_type = 'BASE TABLE' AND UPPER(engine) = 'INNODB'
);
SET @appointment_parent_scope_column_count := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'official_warehouse_asn'
      AND (
          (column_name IN ('id', 'owner_user_id') AND data_type = 'bigint'
              AND column_type = 'bigint' AND is_nullable = 'NO')
          OR (column_name = 'store_code' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO')
          OR (column_name = 'site_code' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO')
          OR (column_name = 'is_deleted' AND data_type = 'bit' AND column_type = 'bit(1)')
      )
);
SET @appointment_base_column_count := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'official_warehouse_appointment'
      AND (
          (column_name IN ('id', 'asn_id', 'owner_user_id') AND data_type = 'bigint'
              AND column_type = 'bigint' AND is_nullable = 'NO')
          OR (column_name = 'store_code' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO')
          OR (column_name = 'site_code' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO')
          OR (column_name = 'status' AND data_type = 'varchar' AND character_maximum_length = 40 AND is_nullable = 'NO')
          OR (column_name = 'is_deleted' AND data_type = 'bit' AND column_type = 'bit(1)')
          OR (column_name = 'attempt_count' AND data_type = 'int' AND is_nullable = 'NO')
          OR (column_name = 'project_code' AND data_type = 'varchar' AND character_maximum_length = 100)
          OR (column_name = 'partner_id' AND data_type = 'varchar' AND character_maximum_length = 80)
          OR (column_name = 'noon_asn_nr' AND data_type = 'varchar' AND character_maximum_length = 120 AND is_nullable = 'NO')
      )
);
DROP TEMPORARY TABLE IF EXISTS `nuono_234_appointment_base_guard`;
CREATE TEMPORARY TABLE `nuono_234_appointment_base_guard` (
    `invalid_schema_count` BIGINT NOT NULL, CONSTRAINT `chk_234_appointment_base`
    CHECK (`invalid_schema_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_234_appointment_base_guard`
VALUES (IF(
    @appointment_table_count = 1
        AND @appointment_base_column_count = 11
        AND @appointment_parent_table_count = 1
        AND @appointment_parent_scope_column_count = 5,
    0, 1
));
DROP TEMPORARY TABLE `nuono_234_appointment_base_guard`;
SET @appointment_target_column_count := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'official_warehouse_appointment'
      AND column_name IN ('execution_version', 'active_asn_slot', 'active_remote_slot')
);
-- Normalize only known literal renderings; unknown quoted values must remain distinguishable.
SET @appointment_exact_target_column_count := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_appointment'
      AND (
          (
              column_name = 'execution_version'
              AND data_type = 'bigint'
              AND column_type = 'bigint'
              AND is_nullable = 'NO'
              AND column_default = '0'
              AND extra = ''
              AND generation_expression = ''
          )
          OR (
              column_name = 'active_asn_slot'
              AND data_type = 'bigint'
              AND column_type = 'bigint'
              AND is_nullable = 'YES'
              AND column_default IS NULL
              AND UPPER(extra) = 'STORED GENERATED'
              AND REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(REGEXP_REPLACE(LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(generation_expression, '`is_deleted`', 'is_deleted'), '`status`', 'status'), '`asn_id`', 'asn_id'), '`project_code`', 'project_code'), '`partner_id`', 'partner_id'), '`site_code`', 'site_code'), '`noon_asn_nr`', 'noon_asn_nr'), CONCAT(CHAR(92), CHAR(39)), CHAR(39))), '(_utf8mb4)?''canceled''', '@canceled@', 1, 0, 'c'), CONCAT(CHAR(92), '0'), '0'), '(_binary|b)?''0''', '@zero@'), '(_utf8mb4)?'':''', '@colon@'), '(_utf8mb4)?''[|]''', '@pipe@'), '(_utf8mb4)?''''', '@empty@'), '[[:space:]]+', '') REGEXP
                  '^[()]*casewhen[(]*coalesce[(]+[(]*is_deleted[)]*,(@zero@|0b0|0x00|0)[)][)]*=[(]*(@zero@|0b0|0x00|0)[)]*and[(]*status[)]*<>[(]*@canceled@[)]*[)]*then[(]*asn_id[)]*elsenullend[()]*$'
          )
          OR (
              column_name = 'active_remote_slot'
              AND data_type = 'varchar'
              AND character_maximum_length = 384
              AND character_set_name = 'utf8mb4'
              AND collation_name = 'utf8mb4_bin'
              AND is_nullable = 'YES'
              AND column_default IS NULL
              AND UPPER(extra) = 'STORED GENERATED'
              AND REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(REGEXP_REPLACE(LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(generation_expression, '`is_deleted`', 'is_deleted'), '`status`', 'status'), '`asn_id`', 'asn_id'), '`project_code`', 'project_code'), '`partner_id`', 'partner_id'), '`site_code`', 'site_code'), '`noon_asn_nr`', 'noon_asn_nr'), CONCAT(CHAR(92), CHAR(39)), CHAR(39))), '(_utf8mb4)?''canceled''', '@canceled@', 1, 0, 'c'), CONCAT(CHAR(92), '0'), '0'), '(_binary|b)?''0''', '@zero@'), '(_utf8mb4)?'':''', '@colon@'), '(_utf8mb4)?''[|]''', '@pipe@'), '(_utf8mb4)?''''', '@empty@'), '[[:space:]]+', '') REGEXP
                  '^[()]*casewhen[(]*coalesce[(]+[(]*is_deleted[)]*,(@zero@|0b0|0x00|0)[)][)]*=[(]*(@zero@|0b0|0x00|0)[)]*and[(]*status[)]*<>[(]*@canceled@[)]*[)]*then[(]*concat[(]+char_length[(]+upper[(]+trim[(]+coalesce[(]+[(]*project_code[)]*,@empty@[)]{3,}[)]+,@colon@,upper[(]+trim[(]+coalesce[(]+[(]*project_code[)]*,@empty@[)]{3,},@pipe@,char_length[(]+upper[(]+trim[(]+coalesce[(]+[(]*partner_id[)]*,@empty@[)]{3,}[)]+,@colon@,upper[(]+trim[(]+coalesce[(]+[(]*partner_id[)]*,@empty@[)]{3,},@pipe@,char_length[(]+upper[(]+trim[(]+coalesce[(]+[(]*site_code[)]*,@empty@[)]{3,}[)]+,@colon@,upper[(]+trim[(]+coalesce[(]+[(]*site_code[)]*,@empty@[)]{3,},@pipe@,char_length[(]+upper[(]+trim[(]+coalesce[(]+[(]*noon_asn_nr[)]*,@empty@[)]{3,}[)]+,@colon@,upper[(]+trim[(]+coalesce[(]+[(]*noon_asn_nr[)]*,@empty@[)]{3,}[)]*[)]*elsenullend[()]*$'
          )
      )
);
SET @appointment_active_index_exists := EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_appointment'
      AND index_name = 'uk_official_warehouse_appointment_active_asn'
);
SET @appointment_active_index_is_exact := (
    SELECT IF(
        COUNT(*) = 1
            AND MIN(non_unique) = 0
            AND MAX(non_unique) = 0
            AND MIN(seq_in_index) = 1
            AND MAX(seq_in_index) = 1
            AND MIN(column_name) = 'active_asn_slot'
            AND SUM(sub_part IS NULL) = 1
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND SUM(collation = 'A') = 1
            AND SUM(is_visible = 'YES') = 1
            AND SUM(expression IS NULL) = 1,
        1,
        0
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_appointment'
      AND index_name = 'uk_official_warehouse_appointment_active_asn'
);
SET @appointment_remote_index_exists := EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_appointment'
      AND index_name = 'uk_official_warehouse_appointment_active_remote'
);
SET @appointment_remote_index_is_exact := (
    SELECT IF(
        COUNT(*) = 1
            AND MIN(non_unique) = 0
            AND MIN(seq_in_index) = 1
            AND MIN(column_name) = 'active_remote_slot'
            AND SUM(sub_part IS NULL) = 1
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND SUM(collation = 'A') = 1
            AND SUM(is_visible = 'YES') = 1
            AND SUM(expression IS NULL) = 1,
        1,
        0
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_appointment'
      AND index_name = 'uk_official_warehouse_appointment_active_remote'
);
SET @appointment_schema_fully_applied := (
    @appointment_target_column_count = 3
    AND @appointment_exact_target_column_count = 3
    AND @appointment_active_index_exists = 1
    AND @appointment_active_index_is_exact = 1
    AND @appointment_remote_index_exists = 1
    AND @appointment_remote_index_is_exact = 1
    AND EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'official_warehouse_appointment'
          AND column_name = 'is_deleted' AND data_type = 'bit' AND column_type = 'bit(1)'
          AND is_nullable = 'NO' AND LOWER(COALESCE(column_default, '')) IN ('0', '0x00', 'b''0'''))
);
DROP TEMPORARY TABLE IF EXISTS `nuono_234_appointment_target_guard`;
CREATE TEMPORARY TABLE `nuono_234_appointment_target_guard` (
    `invalid_schema_count` BIGINT NOT NULL,
    CONSTRAINT `chk_234_appointment_target` CHECK (`invalid_schema_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_234_appointment_target_guard`
VALUES (IF(
    (
        @appointment_target_column_count = 0
        AND @appointment_active_index_exists = 0
        AND @appointment_remote_index_exists = 0
    )
    OR @appointment_schema_fully_applied,
    0,
    1
));
DROP TEMPORARY TABLE `nuono_234_appointment_target_guard`;
SET @appointment_invalid_row_count := (
    SELECT COUNT(*)
    FROM `official_warehouse_appointment`
    WHERE `is_deleted` IS NULL
       OR `asn_id` IS NULL
       OR `status` IS NULL
       OR `status` NOT IN (
           'PENDING', 'RUNNING', 'SCHEDULED', 'FAILED', 'CANCELED'
       )
       OR (
           `is_deleted` = b'0'
           AND `status` <> 'CANCELED'
           AND (
               NULLIF(TRIM(`project_code`), '') IS NULL
               OR NULLIF(TRIM(`partner_id`), '') IS NULL
               OR NULLIF(TRIM(`site_code`), '') IS NULL
               OR NULLIF(TRIM(`noon_asn_nr`), '') IS NULL
           )
       )
);
SET @appointment_duplicate_remote_group_count := (
    SELECT COUNT(*)
    FROM (
        SELECT 1
        FROM `official_warehouse_appointment`
        WHERE `is_deleted` = b'0'
          AND `status` <> 'CANCELED'
        GROUP BY
            UPPER(TRIM(`project_code`)),
            UPPER(TRIM(`partner_id`)),
            UPPER(TRIM(`site_code`)),
            UPPER(TRIM(`noon_asn_nr`))
        HAVING COUNT(*) > 1
    ) AS `remote_duplicates`
);
SET @appointment_duplicate_active_group_count := (
    SELECT COUNT(*)
    FROM (
        SELECT 1
        FROM `official_warehouse_appointment`
        WHERE `is_deleted` = b'0'
          AND `status` <> 'CANCELED'
        GROUP BY `asn_id`
        HAVING COUNT(*) > 1
    ) AS `active_duplicates`
);
SET @appointment_parent_mismatch_count := (
    SELECT COUNT(*)
    FROM `official_warehouse_appointment` appointment
    LEFT JOIN `official_warehouse_asn` parent_asn
      ON parent_asn.id = appointment.asn_id
    WHERE appointment.is_deleted = b'0'
      AND (
          parent_asn.id IS NULL
          OR NOT (parent_asn.is_deleted <=> b'0')
          OR NOT (parent_asn.owner_user_id <=> appointment.owner_user_id)
          OR NOT (UPPER(parent_asn.store_code) <=> UPPER(appointment.store_code))
          OR NOT (UPPER(parent_asn.site_code) <=> UPPER(appointment.site_code))
      )
);
SET @appointment_running_count := (
    SELECT COUNT(*)
    FROM `official_warehouse_appointment`
    WHERE `is_deleted` = b'0'
      AND `status` = 'RUNNING'
);
DROP TEMPORARY TABLE IF EXISTS `nuono_234_appointment_data_guard`;
CREATE TEMPORARY TABLE `nuono_234_appointment_data_guard` (
    `invalid_data_count` BIGINT NOT NULL,
    CONSTRAINT `chk_234_appointment_data` CHECK (`invalid_data_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_234_appointment_data_guard`
VALUES (
    @appointment_invalid_row_count
    + @appointment_duplicate_active_group_count
    + @appointment_duplicate_remote_group_count
    + @appointment_parent_mismatch_count
    + IF(
        @appointment_schema_fully_applied,
        0,
        @appointment_running_count
    )
);
DROP TEMPORARY TABLE `nuono_234_appointment_data_guard`;
SET @appointment_concurrency_sql := IF(
    @appointment_schema_fully_applied,
    'DO 0',
    'ALTER TABLE `official_warehouse_appointment`
        MODIFY COLUMN `is_deleted` BIT(1) NOT NULL DEFAULT b''0'',
        ADD COLUMN `execution_version` BIGINT NOT NULL DEFAULT 0
            AFTER `attempt_count`,
        ADD COLUMN `active_asn_slot` BIGINT GENERATED ALWAYS AS
            (CASE WHEN COALESCE(`is_deleted`, b''0'') = b''0'' AND `status` <> ''CANCELED''
                THEN `asn_id` ELSE NULL END) STORED
            AFTER `is_deleted`,
        ADD COLUMN `active_remote_slot` VARCHAR(384)
            CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
            GENERATED ALWAYS AS
            (CASE WHEN COALESCE(`is_deleted`, b''0'') = b''0'' AND `status` <> ''CANCELED''
                THEN CONCAT(
                    CHAR_LENGTH(UPPER(TRIM(COALESCE(`project_code`, '''')))), '':'',
                    UPPER(TRIM(COALESCE(`project_code`, ''''))), ''|'',
                    CHAR_LENGTH(UPPER(TRIM(COALESCE(`partner_id`, '''')))), '':'',
                    UPPER(TRIM(COALESCE(`partner_id`, ''''))), ''|'',
                    CHAR_LENGTH(UPPER(TRIM(COALESCE(`site_code`, '''')))), '':'',
                    UPPER(TRIM(COALESCE(`site_code`, ''''))), ''|'',
                    CHAR_LENGTH(UPPER(TRIM(COALESCE(`noon_asn_nr`, '''')))), '':'',
                    UPPER(TRIM(COALESCE(`noon_asn_nr`, '''')))
                ) ELSE NULL END) STORED
            AFTER `active_asn_slot`,
        ADD UNIQUE KEY `uk_official_warehouse_appointment_active_asn` (`active_asn_slot`),
        ADD UNIQUE KEY `uk_official_warehouse_appointment_active_remote` (`active_remote_slot`)'
);
PREPARE appointment_concurrency_stmt FROM @appointment_concurrency_sql;
EXECUTE appointment_concurrency_stmt;
DEALLOCATE PREPARE appointment_concurrency_stmt;
