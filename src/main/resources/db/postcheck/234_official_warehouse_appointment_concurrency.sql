SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'official_warehouse_appointment'
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
          AND UPPER(row_format) IN ('DYNAMIC', 'COMPRESSED')
    ) = 1
    AND (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'official_warehouse_asn'
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
    ) = 1
    -- Preserve literal boundaries while tolerating MySQL 8 metadata formatting.
    AND (
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
                  column_name = 'is_deleted'
                  AND data_type = 'bit'
                  AND column_type = 'bit(1)'
                  AND is_nullable = 'NO'
                  AND LOWER(COALESCE(column_default, ''))
                      IN ('0', '0x00', 'b''0''')
              )
              OR (column_name = 'project_code' AND data_type = 'varchar' AND character_maximum_length = 100)
              OR (column_name = 'partner_id' AND data_type = 'varchar' AND character_maximum_length = 80)
              OR (column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint' AND is_nullable = 'NO')
              OR (column_name = 'owner_user_id' AND data_type = 'bigint' AND column_type = 'bigint' AND is_nullable = 'NO')
              OR (column_name = 'store_code' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO')
              OR (column_name = 'attempt_count' AND data_type = 'int' AND is_nullable = 'NO')
              OR (column_name = 'site_code' AND data_type = 'varchar' AND character_maximum_length = 20 AND is_nullable = 'NO')
              OR (column_name = 'noon_asn_nr' AND data_type = 'varchar' AND character_maximum_length = 120 AND is_nullable = 'NO')
              OR (column_name = 'asn_id' AND data_type = 'bigint' AND column_type = 'bigint' AND is_nullable = 'NO')
              OR (column_name = 'status' AND data_type = 'varchar' AND character_maximum_length = 40 AND is_nullable = 'NO')
              OR (
                  column_name = 'active_asn_slot'
                  AND data_type = 'bigint'
                  AND column_type = 'bigint'
                  AND is_nullable = 'YES'
                  AND column_default IS NULL
                  AND UPPER(extra) = 'STORED GENERATED'
                  AND REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(REGEXP_REPLACE(LOWER(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(generation_expression, '`is_deleted`', 'is_deleted'), '`status`', 'status'), '`asn_id`', 'asn_id'), '`project_code`', 'project_code'), '`partner_id`', 'partner_id'), '`site_code`', 'site_code'), '`noon_asn_nr`', 'noon_asn_nr'), CONCAT(CHAR(92), CHAR(39)), CHAR(39))), '(_utf8mb4)?''CANCELED''', '@canceled@', 1, 0, 'c'), CONCAT(CHAR(92), '0'), '0'), '(_binary|b)?''0''', '@zero@'), '(_utf8mb4)?'':''', '@colon@'), '(_utf8mb4)?''[|]''', '@pipe@'), '(_utf8mb4)?''''', '@empty@'), '[[:space:]]+', '') REGEXP
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
                  AND REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(REGEXP_REPLACE(LOWER(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(generation_expression, '`is_deleted`', 'is_deleted'), '`status`', 'status'), '`asn_id`', 'asn_id'), '`project_code`', 'project_code'), '`partner_id`', 'partner_id'), '`site_code`', 'site_code'), '`noon_asn_nr`', 'noon_asn_nr'), CONCAT(CHAR(92), CHAR(39)), CHAR(39))), '(_utf8mb4)?''CANCELED''', '@canceled@', 1, 0, 'c'), CONCAT(CHAR(92), '0'), '0'), '(_binary|b)?''0''', '@zero@'), '(_utf8mb4)?'':''', '@colon@'), '(_utf8mb4)?''[|]''', '@pipe@'), '(_utf8mb4)?''''', '@empty@'), '[[:space:]]+', '') REGEXP
                      '^[()]*casewhen[(]*coalesce[(]+[(]*is_deleted[)]*,(@zero@|0b0|0x00|0)[)][)]*=[(]*(@zero@|0b0|0x00|0)[)]*and[(]*status[)]*<>[(]*@canceled@[)]*[)]*then[(]*concat[(]+char_length[(]+upper[(]+trim[(]+coalesce[(]+[(]*project_code[)]*,@empty@[)]{3,}[)]+,@colon@,upper[(]+trim[(]+coalesce[(]+[(]*project_code[)]*,@empty@[)]{3,},@pipe@,char_length[(]+upper[(]+trim[(]+coalesce[(]+[(]*partner_id[)]*,@empty@[)]{3,}[)]+,@colon@,upper[(]+trim[(]+coalesce[(]+[(]*partner_id[)]*,@empty@[)]{3,},@pipe@,char_length[(]+upper[(]+trim[(]+coalesce[(]+[(]*site_code[)]*,@empty@[)]{3,}[)]+,@colon@,upper[(]+trim[(]+coalesce[(]+[(]*site_code[)]*,@empty@[)]{3,},@pipe@,char_length[(]+upper[(]+trim[(]+coalesce[(]+[(]*noon_asn_nr[)]*,@empty@[)]{3,}[)]+,@colon@,upper[(]+trim[(]+coalesce[(]+[(]*noon_asn_nr[)]*,@empty@[)]{3,}[)]*[)]*elsenullend[()]*$'
              )
          )
    ) = 14
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'official_warehouse_asn'
          AND (
              (column_name IN ('id', 'owner_user_id') AND data_type = 'bigint'
                  AND column_type = 'bigint' AND is_nullable = 'NO')
              OR (column_name = 'store_code' AND data_type = 'varchar'
                  AND character_maximum_length = 100 AND is_nullable = 'NO')
              OR (column_name = 'site_code' AND data_type = 'varchar'
                  AND character_maximum_length = 20 AND is_nullable = 'NO')
              OR (column_name = 'is_deleted' AND data_type = 'bit'
                  AND column_type = 'bit(1)')
          )
    ) = 5
    AND (
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
    ) = 1
    AND (
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
    ) = 1
    AND NOT EXISTS (
        SELECT 1
        FROM `official_warehouse_appointment`
        WHERE `is_deleted` IS NULL
           OR `asn_id` IS NULL
           OR `status` IS NULL
           OR `status` NOT IN (
               'PENDING', 'RUNNING', 'SCHEDULED', 'FAILED', 'CANCELED'
           )
           OR `execution_version` < 0
           OR (`is_deleted` = b'0' AND `status` = 'RUNNING' AND `execution_version` = 0)
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
           OR NOT (
               `active_asn_slot` <=> CASE
                   WHEN COALESCE(`is_deleted`, b'0') = b'0' AND `status` <> 'CANCELED'
                       THEN `asn_id`
                   ELSE NULL
               END
           )
           OR NOT (
               `active_remote_slot` <=> CASE
                   WHEN COALESCE(`is_deleted`, b'0') = b'0' AND `status` <> 'CANCELED'
                   THEN CONCAT(
                       CHAR_LENGTH(UPPER(TRIM(COALESCE(`project_code`, '')))), ':',
                       UPPER(TRIM(COALESCE(`project_code`, ''))), '|',
                       CHAR_LENGTH(UPPER(TRIM(COALESCE(`partner_id`, '')))), ':',
                       UPPER(TRIM(COALESCE(`partner_id`, ''))), '|',
                       CHAR_LENGTH(UPPER(TRIM(COALESCE(`site_code`, '')))), ':',
                       UPPER(TRIM(COALESCE(`site_code`, ''))), '|',
                       CHAR_LENGTH(UPPER(TRIM(COALESCE(`noon_asn_nr`, '')))), ':',
                       UPPER(TRIM(COALESCE(`noon_asn_nr`, '')))
                   )
                   ELSE NULL
               END
           )
    )
    AND NOT EXISTS (
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
    )
    AND NOT EXISTS (
        SELECT 1
        FROM `official_warehouse_appointment`
        WHERE `is_deleted` = b'0'
          AND `status` <> 'CANCELED'
        GROUP BY `asn_id`
        HAVING COUNT(*) > 1
    )
    AND NOT EXISTS (
        SELECT 1
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
    ),
    1,
    0
);
