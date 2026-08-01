SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'procurement_dispatch_plan',
              'procurement_fulfillment_confirmation'
          )
          AND table_type = 'BASE TABLE'
          AND UPPER(engine) = 'INNODB'
    ) = 2
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'procurement_dispatch_plan',
              'procurement_fulfillment_confirmation'
          )
          AND column_name = 'owner_user_id'
          AND data_type = 'bigint'
          AND column_type = 'bigint'
          AND is_nullable = 'NO'
          AND column_default IS NULL
          AND extra = ''
          AND generation_expression = ''
    ) = 2
    AND (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND (
              (
                  table_name = 'procurement_dispatch_plan'
                  AND column_name = 'client_request_id'
                  AND data_type = 'varchar'
                  AND character_maximum_length = 100
              )
              OR (
                  table_name = 'procurement_dispatch_plan'
                  AND column_name = 'request_fingerprint'
                  AND data_type = 'varchar'
                  AND character_maximum_length = 64
              )
              OR (
                  table_name = 'procurement_fulfillment_confirmation'
                  AND column_name = 'client_request_id'
                  AND data_type = 'varchar'
                  AND character_maximum_length = 100
              )
              OR (
                  table_name = 'procurement_fulfillment_confirmation'
                  AND column_name = 'request_fingerprint'
                  AND data_type = 'char'
                  AND character_maximum_length = 64
              )
          )
          AND character_set_name = 'utf8mb4'
          AND (column_name <> 'client_request_id' OR collation_name = 'utf8mb4_bin')
          AND is_nullable = 'YES'
          AND column_default IS NULL
          AND extra = ''
          AND generation_expression = ''
    ) = 4
    AND (
        SELECT IF(
            COUNT(*) = 2
                AND MIN(non_unique) = 0
                AND MAX(non_unique) = 0
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
                ) = '1:owner_user_id,2:client_request_id',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'procurement_dispatch_plan'
          AND index_name = 'uk_dispatch_plan_owner_client_request'
    ) = 1
    AND (
        SELECT IF(
            COUNT(*) = 2
                AND MIN(non_unique) = 0
                AND MAX(non_unique) = 0
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
                ) = '1:owner_user_id,2:client_request_id',
            1,
            0
        )
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'procurement_fulfillment_confirmation'
          AND index_name =
              'uk_fulfillment_confirmation_owner_client_request'
    ) = 1
    AND NOT EXISTS (
        SELECT 1
        FROM `procurement_dispatch_plan`
        WHERE `client_request_id` IS NOT NULL
        GROUP BY `owner_user_id`, `client_request_id`
        HAVING COUNT(*) > 1
    )
    AND NOT EXISTS (
        SELECT 1
        FROM `procurement_fulfillment_confirmation`
        WHERE `client_request_id` IS NOT NULL
        GROUP BY `owner_user_id`, `client_request_id`
        HAVING COUNT(*) > 1
    )
    AND NOT EXISTS (
        SELECT 1
        FROM `procurement_dispatch_plan`
        WHERE (`client_request_id` IS NULL) <> (`request_fingerprint` IS NULL)
           OR (`client_request_id` IS NOT NULL AND (
                TRIM(`client_request_id`) = ''
                OR BINARY `client_request_id` <> BINARY TRIM(`client_request_id`)
                OR `client_request_id` REGEXP '[[:cntrl:]]'
                OR NOT (BINARY `request_fingerprint` REGEXP '^[0-9a-f]{64}$')
           ))
    )
    AND NOT EXISTS (
        SELECT 1
        FROM `procurement_fulfillment_confirmation`
        WHERE (`client_request_id` IS NULL) <> (`request_fingerprint` IS NULL)
           OR (`client_request_id` IS NOT NULL AND (
                TRIM(`client_request_id`) = ''
                OR BINARY `client_request_id` <> BINARY TRIM(`client_request_id`)
                OR `client_request_id` REGEXP '[[:cntrl:]]'
                OR NOT (BINARY `request_fingerprint` REGEXP '^[0-9a-f]{64}$')
           ))
    ),
    1,
    0
);
