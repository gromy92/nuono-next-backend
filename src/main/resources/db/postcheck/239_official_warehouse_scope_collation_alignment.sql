SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'official_warehouse_asn',
              'official_warehouse_asn_line',
              'official_warehouse_asn_shipping_batch_link'
          )
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
    ) = 3
    AND (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'official_warehouse_asn_shipping_batch_link'
          AND table_collation = 'utf8mb4_0900_ai_ci'
    ) = 1
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND (
              (
                  table_name IN (
                      'official_warehouse_asn',
                      'official_warehouse_asn_shipping_batch_link'
                  )
                  AND (
                      (column_name IN ('id', 'owner_user_id')
                          AND data_type = 'bigint' AND column_type = 'bigint'
                          AND is_nullable = 'NO')
                      OR (table_name = 'official_warehouse_asn_shipping_batch_link'
                          AND column_name IN ('asn_id', 'asn_line_id')
                          AND data_type = 'bigint' AND column_type = 'bigint'
                          AND is_nullable = 'NO')
                      OR (column_name = 'store_code' AND data_type = 'varchar'
                          AND character_maximum_length = 100
                          AND character_set_name = 'utf8mb4'
                          AND collation_name = 'utf8mb4_0900_ai_ci'
                          AND is_nullable = 'NO')
                      OR (column_name = 'site_code' AND data_type = 'varchar'
                          AND character_maximum_length = 20
                          AND character_set_name = 'utf8mb4'
                          AND collation_name = 'utf8mb4_0900_ai_ci'
                          AND is_nullable = 'NO')
                      OR (column_name = 'is_deleted' AND data_type = 'bit'
                          AND column_type = 'bit(1)' AND is_nullable = 'NO')
                  )
              )
              OR (
                  table_name = 'official_warehouse_asn_line'
                  AND (
                      (column_name IN ('id', 'asn_id', 'owner_user_id')
                          AND data_type = 'bigint' AND column_type = 'bigint'
                          AND is_nullable = 'NO')
                      OR (column_name = 'store_code' AND data_type = 'varchar'
                          AND character_maximum_length = 100
                          AND character_set_name = 'utf8mb4'
                          AND collation_name = 'utf8mb4_0900_ai_ci'
                          AND is_nullable = 'NO')
                      OR (column_name = 'site_code' AND data_type = 'varchar'
                          AND character_maximum_length = 20
                          AND character_set_name = 'utf8mb4'
                          AND collation_name = 'utf8mb4_0900_ai_ci'
                          AND is_nullable = 'NO')
                      OR (column_name = 'is_deleted' AND data_type = 'bit'
                          AND column_type = 'bit(1)' AND is_nullable = 'NO')
                  )
              )
          )
    ) = 18
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name LIKE 'official!_warehouse!_%' ESCAPE '!'
          AND column_name IN ('store_code', 'site_code')
          AND (
              character_set_name <> 'utf8mb4'
              OR collation_name <> 'utf8mb4_0900_ai_ci'
          )
    )
    AND (
        SELECT IF(
            COUNT(*) = 5
                AND MIN(non_unique) = 1 AND MAX(non_unique) = 1
                AND MIN(UPPER(index_type)) = 'BTREE'
                AND MAX(UPPER(index_type)) = 'BTREE'
                AND SUM(sub_part IS NULL) = 5
                AND SUM(collation = 'A') = 5
                AND SUM(is_visible = 'YES') = 5
                AND SUM(expression IS NULL) = 5
                AND GROUP_CONCAT(
                    CONCAT(seq_in_index, ':', column_name)
                    ORDER BY seq_in_index SEPARATOR ','
                ) = '1:owner_user_id,2:store_code,3:site_code,4:product_variant_id,5:is_deleted',
            1, 0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'official_warehouse_asn_shipping_batch_link'
          AND index_name = 'idx_official_warehouse_asn_shipping_product'
    ) = 1
    AND NOT EXISTS (
        SELECT 1
        FROM official_warehouse_asn_shipping_batch_link link
        LEFT JOIN official_warehouse_asn_line parent_line
          ON parent_line.id = link.asn_line_id
        LEFT JOIN official_warehouse_asn parent_asn
          ON parent_asn.id = link.asn_id
        WHERE link.is_deleted = b'0'
          AND (
              parent_line.id IS NULL OR parent_asn.id IS NULL
              OR NOT (parent_line.is_deleted <=> b'0')
              OR NOT (parent_asn.is_deleted <=> b'0')
              OR NOT (parent_line.asn_id <=> parent_asn.id)
              OR NOT (parent_line.owner_user_id <=> parent_asn.owner_user_id)
              OR NOT (link.owner_user_id <=> parent_asn.owner_user_id)
              OR NOT (UPPER(parent_line.store_code) <=> UPPER(parent_asn.store_code))
              OR NOT (UPPER(parent_line.site_code) <=> UPPER(parent_asn.site_code))
              OR NOT (UPPER(parent_asn.store_code) <=> UPPER(link.store_code))
              OR NOT (UPPER(parent_asn.site_code) <=> UPPER(link.site_code))
          )
    ),
    1,
    0
);
