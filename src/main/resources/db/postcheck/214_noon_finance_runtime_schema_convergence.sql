WITH
required_tables (`table_name`) AS (
    VALUES
        ROW('noon_finance_transaction_id_sequence'),
        ROW('noon_finance_transaction_fact')
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
        ROW('noon_finance_transaction_id_sequence', 1, 'sequence_name', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_id_sequence', 2, 'next_id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_id_sequence', 3, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('noon_finance_transaction_id_sequence', 4, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp'),

        ROW('noon_finance_transaction_fact', 1, 'id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 2, 'source_system', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 3, 'source_batch_id', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 4, 'file_digest_sha256', 'varchar(128)', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 5, 'row_hash', 'varchar(128)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 6, 'owner_user_id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 7, 'store_code', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 8, 'site_code', 'varchar(20)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 9, 'contract_code', 'varchar(80)', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 10, 'contract_title', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 11, 'reference_nr', 'varchar(160)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 12, 'order_nr', 'varchar(160)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 13, 'item_nr', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 14, 'order_date', 'date', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 15, 'transaction_date', 'date', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 16, 'title', 'varchar(1024)', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 17, 'sku', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 18, 'partner_sku', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_finance_transaction_fact', 19, 'transaction_type', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 20, 'currency', 'varchar(20)', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 21, 'net_proceeds', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 22, 'referral_fee_including_vat', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 23, 'fulfillment_logistics_fees_including_vat', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 24, 'shipping_credits_including_vat', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 25, 'other_order_fees_including_vat', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 26, 'order_subsidies_including_vat', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 27, 'non_order_fees_including_vat', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 28, 'non_order_subsidies_including_vat', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 29, 'others_including_vat', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 30, 'total_amount', 'decimal(18,6)', 'NO', '0', ''),
        ROW('noon_finance_transaction_fact', 31, 'report_date_from', 'date', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 32, 'report_date_to', 'date', 'NO', '<null>', ''),
        ROW('noon_finance_transaction_fact', 33, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('noon_finance_transaction_fact', 34, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp')
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
        ROW('noon_finance_transaction_id_sequence', 'PRIMARY', 0, 1, 'sequence_name', 'BTREE'),

        ROW('noon_finance_transaction_fact', 'PRIMARY', 0, 1, 'id', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'uk_noon_finance_transaction_fact_natural', 0, 1, 'source_system', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'uk_noon_finance_transaction_fact_natural', 0, 2, 'owner_user_id', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'uk_noon_finance_transaction_fact_natural', 0, 3, 'store_code', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'uk_noon_finance_transaction_fact_natural', 0, 4, 'site_code', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'uk_noon_finance_transaction_fact_natural', 0, 5, 'row_hash', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_scope_transaction_date', 1, 1, 'owner_user_id', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_scope_transaction_date', 1, 2, 'store_code', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_scope_transaction_date', 1, 3, 'site_code', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_scope_transaction_date', 1, 4, 'transaction_date', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_sku', 1, 1, 'owner_user_id', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_sku', 1, 2, 'store_code', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_sku', 1, 3, 'site_code', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_sku', 1, 4, 'partner_sku', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_sku', 1, 5, 'sku', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_order', 1, 1, 'owner_user_id', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_order', 1, 2, 'store_code', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_order', 1, 3, 'site_code', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_order', 1, 4, 'order_nr', 'BTREE'),
        ROW('noon_finance_transaction_fact', 'idx_noon_finance_batch', 1, 1, 'source_batch_id', 'BTREE')
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
          AND actual.table_collation LIKE 'utf8mb4%'
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
    AND NOT EXISTS (
        SELECT 1
        FROM `noon_finance_transaction_fact`
        GROUP BY
            `source_system`,
            `owner_user_id`,
            `store_code`,
            `site_code`,
            `row_hash`
        HAVING COUNT(*) > 1
    )
    AND EXISTS (
        SELECT 1
        FROM `noon_finance_transaction_id_sequence` sequence_row
        WHERE sequence_row.`sequence_name` = 'finance_transaction_fact'
          AND sequence_row.`next_id` >= GREATEST(
              300000,
              COALESCE(
                  (SELECT MAX(`id`) FROM `noon_finance_transaction_fact`),
                  300000
              )
          )
    ),
    1,
    0
);
