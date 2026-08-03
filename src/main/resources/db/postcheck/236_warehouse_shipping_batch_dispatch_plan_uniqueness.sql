SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'warehouse_shipping_batch',
              'procurement_dispatch_plan'
          )
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
    ) = 2
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND (
              (
                  table_name = 'warehouse_shipping_batch'
                  AND (
                      (
                          column_name = 'dispatch_plan_id'
                          AND data_type = 'bigint'
                          AND column_type = 'bigint'
                          AND is_nullable = 'YES'
                          AND column_default IS NULL
                          AND extra = ''
                          AND generation_expression = ''
                      )
                      OR (
                          column_name = 'owner_user_id'
                          AND data_type = 'bigint'
                          AND column_type = 'bigint'
                          AND is_nullable = 'NO'
                      )
                      OR (
                          column_name = 'is_deleted'
                          AND data_type = 'bit'
                          AND column_type = 'bit(1)'
                          AND is_nullable = 'NO'
                      )
                  )
              )
              OR (
                  table_name = 'procurement_dispatch_plan'
                  AND (
                      (
                          column_name IN ('id', 'owner_user_id')
                          AND data_type = 'bigint'
                          AND column_type = 'bigint'
                          AND is_nullable = 'NO'
                      )
                      OR (
                          column_name = 'is_deleted'
                          AND data_type = 'bit'
                          AND column_type = 'bit(1)'
                          AND is_nullable = 'NO'
                      )
                  )
              )
          )
    ) = 6
    AND (
        SELECT IF(
            COUNT(*) = 1
                AND MIN(non_unique) = 0
                AND MIN(seq_in_index) = 1
                AND MIN(column_name) = 'id'
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
          AND table_name = 'procurement_dispatch_plan'
          AND index_name = 'PRIMARY'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 3
                AND MIN(non_unique) = 1
                AND MAX(non_unique) = 1
                AND MIN(UPPER(index_type)) = 'BTREE'
                AND MAX(UPPER(index_type)) = 'BTREE'
                AND SUM(sub_part IS NULL) = 3
                AND SUM(collation = 'A') = 3
                AND SUM(is_visible = 'YES') = 3
                AND SUM(expression IS NULL) = 3
                AND GROUP_CONCAT(
                    CONCAT(seq_in_index, ':', column_name)
                    ORDER BY seq_in_index
                    SEPARATOR ','
                ) = '1:dispatch_plan_id,2:is_deleted,3:gmt_updated',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'warehouse_shipping_batch'
          AND index_name = 'idx_shipping_batch_dispatch_plan'
    ) = 1
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'warehouse_shipping_batch'
          AND column_name = 'active_dispatch_plan_id'
          AND data_type = 'bigint'
          AND column_type = 'bigint'
          AND is_nullable = 'YES'
          AND column_default IS NULL
          AND UPPER(extra) = 'STORED GENERATED'
          AND REGEXP_REPLACE(
              REGEXP_REPLACE(
                  REPLACE(
                      LOWER(
                          REPLACE(
                              REPLACE(
                                  REPLACE(generation_expression,
                                      '`is_deleted`', 'is_deleted'),
                                  '`dispatch_plan_id`', 'dispatch_plan_id'
                              ),
                              CONCAT(CHAR(92), CHAR(39)),
                              CHAR(39)
                          )
                      ),
                      CONCAT(CHAR(92), '0'),
                      '0'
                  ),
                  '(_binary|b)?''0''',
                  '0'
              ),
              '[[:space:]]+',
              ''
          ) REGEXP
              '^[()]*casewhen[(]*is_deleted[)]*=[(]*(0|0b0|0x00)[)]*then[(]*dispatch_plan_id[)]*elsenullend[()]*$'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 1
                AND MIN(non_unique) = 0
                AND MIN(seq_in_index) = 1
                AND MIN(column_name) = 'active_dispatch_plan_id'
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
          AND table_name = 'warehouse_shipping_batch'
          AND index_name = 'uk_shipping_batch_active_dispatch_plan'
    ) = 1
    AND NOT EXISTS (
        SELECT 1
        FROM warehouse_shipping_batch
        WHERE is_deleted = b'0'
          AND dispatch_plan_id IS NOT NULL
        GROUP BY dispatch_plan_id
        HAVING COUNT(*) > 1
    )
    AND NOT EXISTS (
        SELECT 1
        FROM warehouse_shipping_batch batch
        LEFT JOIN procurement_dispatch_plan plan
          ON plan.id = batch.dispatch_plan_id
        WHERE batch.dispatch_plan_id IS NOT NULL
          AND plan.id IS NULL
    )
    AND NOT EXISTS (
        SELECT 1
        FROM warehouse_shipping_batch batch
        JOIN procurement_dispatch_plan plan
          ON plan.id = batch.dispatch_plan_id
        WHERE batch.is_deleted = b'0'
          AND NOT (plan.is_deleted <=> b'0')
    )
    AND NOT EXISTS (
        SELECT 1
        FROM warehouse_shipping_batch batch
        JOIN procurement_dispatch_plan plan
          ON plan.id = batch.dispatch_plan_id
        WHERE batch.dispatch_plan_id IS NOT NULL
          AND NOT (batch.owner_user_id <=> plan.owner_user_id)
    ),
    1,
    0
);
