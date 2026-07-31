WITH
required_tables (`table_name`) AS (
    VALUES
        ROW('sales_data_id_sequence'),
        ROW('daily_sales_fact'),
        ROW('noon_order_id_sequence'),
        ROW('noon_order_line_fact')
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
        ROW('sales_data_id_sequence', 1, 'sequence_name', 'varchar(80)', 'NO', '<null>', ''),
        ROW('sales_data_id_sequence', 2, 'next_id', 'bigint', 'NO', '<null>', ''),
        ROW('sales_data_id_sequence', 3, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('sales_data_id_sequence', 4, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp'),

        ROW('daily_sales_fact', 1, 'id', 'bigint', 'NO', '<null>', ''),
        ROW('daily_sales_fact', 2, 'source_system', 'varchar(80)', 'NO', '<null>', ''),
        ROW('daily_sales_fact', 3, 'source_batch_id', 'bigint', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 4, 'owner_user_id', 'bigint', 'NO', '<null>', ''),
        ROW('daily_sales_fact', 5, 'logical_store_id', 'bigint', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 6, 'store_code', 'varchar(80)', 'NO', '<null>', ''),
        ROW('daily_sales_fact', 7, 'site_code', 'varchar(20)', 'NO', '<null>', ''),
        ROW('daily_sales_fact', 8, 'fact_date', 'date', 'NO', '<null>', ''),
        ROW('daily_sales_fact', 9, 'partner_sku', 'varchar(160)', 'NO', '<null>', ''),
        ROW('daily_sales_fact', 10, 'sku', 'varchar(160)', 'NO', '<null>', ''),
        ROW('daily_sales_fact', 11, 'sku_config', 'varchar(160)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 12, 'country_code', 'varchar(20)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 13, 'currency_code', 'varchar(20)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 14, 'product_title', 'varchar(1000)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 15, 'your_visitors', 'int', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 16, 'total_visitors', 'int', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 17, 'gross_units', 'int', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 18, 'shipped_units', 'int', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 19, 'cancelled_units', 'int', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 20, 'net_units', 'int', 'NO', '0', ''),
        ROW('daily_sales_fact', 21, 'revenue_shipped', 'decimal(18,6)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 22, 'buy_box_visitor_percentage', 'decimal(10,4)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 23, 'conversion_visitors_percentage', 'decimal(10,4)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 24, 'asp_shipped_percentage', 'decimal(18,6)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 25, 'source_row_hash', 'varchar(128)', 'YES', '<null>', ''),
        ROW('daily_sales_fact', 26, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('daily_sales_fact', 27, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp'),

        ROW('noon_order_id_sequence', 1, 'sequence_name', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_order_id_sequence', 2, 'next_id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_order_id_sequence', 3, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('noon_order_id_sequence', 4, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp'),

        ROW('noon_order_line_fact', 1, 'id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 2, 'source_system', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 3, 'source_batch_id', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 4, 'owner_user_id', 'bigint', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 5, 'store_code', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 6, 'site_code', 'varchar(20)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 7, 'id_partner', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 8, 'src_country', 'varchar(20)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 9, 'country_code', 'varchar(20)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 10, 'dest_country', 'varchar(20)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 11, 'bayan_nr', 'varchar(120)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 12, 'item_nr', 'varchar(160)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 13, 'order_identity', 'varchar(160)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 14, 'partner_sku', 'varchar(160)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 15, 'sku', 'varchar(160)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 16, 'status', 'varchar(80)', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 17, 'offer_price', 'decimal(18,6)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 18, 'gmv_lcy', 'decimal(18,6)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 19, 'currency_code', 'varchar(20)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 20, 'brand_code', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 21, 'family', 'varchar(255)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 22, 'fulfillment_model', 'varchar(160)', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 23, 'order_timestamp', 'datetime', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 24, 'shipment_timestamp', 'datetime', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 25, 'delivered_timestamp', 'datetime', 'YES', '<null>', ''),
        ROW('noon_order_line_fact', 26, 'report_date_from', 'date', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 27, 'report_date_to', 'date', 'NO', '<null>', ''),
        ROW('noon_order_line_fact', 28, 'gmt_create', 'datetime', 'NO', 'current_timestamp', ''),
        ROW('noon_order_line_fact', 29, 'gmt_updated', 'datetime', 'NO', 'current_timestamp', 'on update current_timestamp')
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
        ROW('sales_data_id_sequence', 'PRIMARY', 0, 1, 'sequence_name', 'BTREE'),

        ROW('daily_sales_fact', 'PRIMARY', 0, 1, 'id', 'BTREE'),
        ROW('daily_sales_fact', 'uk_daily_sales_fact_source_scope', 0, 1, 'source_system', 'BTREE'),
        ROW('daily_sales_fact', 'uk_daily_sales_fact_source_scope', 0, 2, 'owner_user_id', 'BTREE'),
        ROW('daily_sales_fact', 'uk_daily_sales_fact_source_scope', 0, 3, 'store_code', 'BTREE'),
        ROW('daily_sales_fact', 'uk_daily_sales_fact_source_scope', 0, 4, 'site_code', 'BTREE'),
        ROW('daily_sales_fact', 'uk_daily_sales_fact_source_scope', 0, 5, 'fact_date', 'BTREE'),
        ROW('daily_sales_fact', 'uk_daily_sales_fact_source_scope', 0, 6, 'partner_sku', 'BTREE'),
        ROW('daily_sales_fact', 'uk_daily_sales_fact_source_scope', 0, 7, 'sku', 'BTREE'),
        ROW('daily_sales_fact', 'idx_daily_sales_fact_scope_date', 1, 1, 'owner_user_id', 'BTREE'),
        ROW('daily_sales_fact', 'idx_daily_sales_fact_scope_date', 1, 2, 'store_code', 'BTREE'),
        ROW('daily_sales_fact', 'idx_daily_sales_fact_scope_date', 1, 3, 'site_code', 'BTREE'),
        ROW('daily_sales_fact', 'idx_daily_sales_fact_scope_date', 1, 4, 'fact_date', 'BTREE'),
        ROW('daily_sales_fact', 'idx_daily_sales_fact_product', 1, 1, 'owner_user_id', 'BTREE'),
        ROW('daily_sales_fact', 'idx_daily_sales_fact_product', 1, 2, 'partner_sku', 'BTREE'),
        ROW('daily_sales_fact', 'idx_daily_sales_fact_product', 1, 3, 'sku', 'BTREE'),
        ROW('daily_sales_fact', 'idx_daily_sales_fact_batch', 1, 1, 'source_batch_id', 'BTREE'),

        ROW('noon_order_id_sequence', 'PRIMARY', 0, 1, 'sequence_name', 'BTREE'),

        ROW('noon_order_line_fact', 'PRIMARY', 0, 1, 'id', 'BTREE'),
        ROW('noon_order_line_fact', 'uk_noon_order_line_fact_natural', 0, 1, 'source_system', 'BTREE'),
        ROW('noon_order_line_fact', 'uk_noon_order_line_fact_natural', 0, 2, 'id_partner', 'BTREE'),
        ROW('noon_order_line_fact', 'uk_noon_order_line_fact_natural', 0, 3, 'country_code', 'BTREE'),
        ROW('noon_order_line_fact', 'uk_noon_order_line_fact_natural', 0, 4, 'item_nr', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_scope_date', 1, 1, 'owner_user_id', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_scope_date', 1, 2, 'store_code', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_scope_date', 1, 3, 'site_code', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_scope_date', 1, 4, 'report_date_from', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_scope_date', 1, 5, 'report_date_to', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_product', 1, 1, 'owner_user_id', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_product', 1, 2, 'partner_sku', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_product', 1, 3, 'sku', 'BTREE'),
        ROW('noon_order_line_fact', 'idx_noon_order_line_fact_batch', 1, 1, 'source_batch_id', 'BTREE')
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
    AND EXISTS (
        SELECT 1
        FROM `sales_data_id_sequence` sequence_row
        WHERE sequence_row.`sequence_name` = 'daily_sales_fact'
          AND sequence_row.`next_id` >= GREATEST(
              100000,
              COALESCE((SELECT MAX(`id`) FROM `daily_sales_fact`), 100000)
          )
    )
    AND EXISTS (
        SELECT 1
        FROM `noon_order_id_sequence` sequence_row
        WHERE sequence_row.`sequence_name` = 'order_line_fact'
          AND sequence_row.`next_id` >= GREATEST(
              200000,
              COALESCE((SELECT MAX(`id`) FROM `noon_order_line_fact`), 200000)
          )
    ),
    1,
    0
);
