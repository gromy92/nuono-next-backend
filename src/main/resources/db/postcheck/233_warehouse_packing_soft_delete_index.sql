SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'warehouse_packing_box_item'
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
    ) = 1
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'warehouse_packing_box_item'
          AND (
              (
                  column_name = 'packing_list_id'
                  AND data_type = 'bigint'
                  AND column_type = 'bigint'
                  AND is_nullable = 'NO'
                  AND column_default IS NULL
                  AND extra = ''
                  AND generation_expression = ''
              )
              OR (
                  column_name = 'is_deleted'
                  AND data_type = 'bit'
                  AND column_type = 'bit(1)'
                  AND is_nullable = 'NO'
                  AND extra = ''
                  AND generation_expression = ''
              )
          )
    ) = 2
    AND (
        SELECT IF(
            COUNT(*) = 2
                AND MIN(non_unique) = 1
                AND MAX(non_unique) = 1
                AND SUM(sub_part IS NULL) = 2
                AND MIN(UPPER(index_type)) = 'BTREE'
                AND MAX(UPPER(index_type)) = 'BTREE'
                AND SUM(collation = 'A') = 2
                AND SUM(is_visible = 'YES') = 2
                AND SUM(expression IS NULL) = 2
                AND GROUP_CONCAT(
                    CONCAT(seq_in_index, ':', column_name)
                    ORDER BY seq_in_index
                    SEPARATOR ','
                ) = '1:packing_list_id,2:is_deleted',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'warehouse_packing_box_item'
          AND index_name = 'idx_packing_box_item_list'
    ) = 1,
    1,
    0
);
