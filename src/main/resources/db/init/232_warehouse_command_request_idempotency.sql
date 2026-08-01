-- Converge warehouse receipt/dispatch idempotency; duplicate request keys require evidenced repair.
SET SESSION `lock_wait_timeout` = 5;
SET SESSION `innodb_lock_wait_timeout` = 5;
SET @warehouse_idempotency_table_count := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'procurement_dispatch_plan',
          'procurement_fulfillment_confirmation'
      )
      AND table_type = 'BASE TABLE'
      AND UPPER(engine) = 'INNODB'
);
SET @warehouse_idempotency_owner_column_count := (
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
);
SET @warehouse_idempotency_conflicting_column_count := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND (
          (
              table_name = 'procurement_dispatch_plan'
              AND column_name IN (
                  'client_request_id',
                  'request_fingerprint'
              )
          )
          OR (
              table_name = 'procurement_fulfillment_confirmation'
              AND column_name IN (
                  'client_request_id',
                  'request_fingerprint'
              )
          )
      )
      AND (
          character_set_name <> 'utf8mb4'
          OR (column_name = 'client_request_id' AND collation_name <> 'utf8mb4_bin')
          OR is_nullable <> 'YES'
          OR column_default IS NOT NULL
          OR extra <> ''
          OR generation_expression <> ''
          OR NOT (
              (
                  column_name = 'client_request_id'
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
                  AND column_name = 'request_fingerprint'
                  AND data_type = 'char'
                  AND character_maximum_length = 64
              )
          )
      )
);
DROP TEMPORARY TABLE IF EXISTS `nuono_232_warehouse_idempotency_schema_guard`;
CREATE TEMPORARY TABLE `nuono_232_warehouse_idempotency_schema_guard` (
    `invalid_schema_count` BIGINT NOT NULL,
    CONSTRAINT `chk_232_warehouse_idempotency_schema` CHECK (`invalid_schema_count` = 0)
) ENGINE=MEMORY;
INSERT INTO `nuono_232_warehouse_idempotency_schema_guard`
VALUES (IF(@warehouse_idempotency_table_count = 2
    AND @warehouse_idempotency_owner_column_count = 2
    AND @warehouse_idempotency_conflicting_column_count = 0, 0, 1));
DROP TEMPORARY TABLE `nuono_232_warehouse_idempotency_schema_guard`;
SET @dispatch_client_request_column_exists := EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_dispatch_plan'
      AND column_name = 'client_request_id'
);
SET @dispatch_request_fingerprint_column_exists := EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_dispatch_plan'
      AND column_name = 'request_fingerprint'
);
SET @dispatch_idempotency_columns_sql := CASE
    WHEN @dispatch_client_request_column_exists = 0
        AND @dispatch_request_fingerprint_column_exists = 0
    THEN 'ALTER TABLE `procurement_dispatch_plan`
        ADD COLUMN `client_request_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL
            AFTER `owner_user_id`,
        ADD COLUMN `request_fingerprint` VARCHAR(64) CHARACTER SET utf8mb4 DEFAULT NULL
            AFTER `client_request_id`'
    WHEN @dispatch_client_request_column_exists = 0
    THEN 'ALTER TABLE `procurement_dispatch_plan`
        ADD COLUMN `client_request_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL
            AFTER `owner_user_id`'
    WHEN @dispatch_request_fingerprint_column_exists = 0
    THEN 'ALTER TABLE `procurement_dispatch_plan`
        ADD COLUMN `request_fingerprint` VARCHAR(64) CHARACTER SET utf8mb4 DEFAULT NULL
            AFTER `client_request_id`'
    ELSE 'DO 0'
END;
PREPARE dispatch_idempotency_columns_stmt FROM @dispatch_idempotency_columns_sql;
EXECUTE dispatch_idempotency_columns_stmt;
DEALLOCATE PREPARE dispatch_idempotency_columns_stmt;
SET @receipt_client_request_column_exists := EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_fulfillment_confirmation'
      AND column_name = 'client_request_id'
);
SET @receipt_request_fingerprint_column_exists := EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_fulfillment_confirmation'
      AND column_name = 'request_fingerprint'
);
SET @receipt_idempotency_columns_sql := CASE
    WHEN @receipt_client_request_column_exists = 0
        AND @receipt_request_fingerprint_column_exists = 0
    THEN 'ALTER TABLE `procurement_fulfillment_confirmation`
        ADD COLUMN `client_request_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL
            AFTER `owner_user_id`,
        ADD COLUMN `request_fingerprint` CHAR(64) CHARACTER SET utf8mb4 DEFAULT NULL
            AFTER `client_request_id`'
    WHEN @receipt_client_request_column_exists = 0
    THEN 'ALTER TABLE `procurement_fulfillment_confirmation`
        ADD COLUMN `client_request_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL
            AFTER `owner_user_id`'
    WHEN @receipt_request_fingerprint_column_exists = 0
    THEN 'ALTER TABLE `procurement_fulfillment_confirmation`
        ADD COLUMN `request_fingerprint` CHAR(64) CHARACTER SET utf8mb4 DEFAULT NULL
            AFTER `client_request_id`'
    ELSE 'DO 0'
END;
PREPARE receipt_idempotency_columns_stmt FROM @receipt_idempotency_columns_sql;
EXECUTE receipt_idempotency_columns_stmt;
DEALLOCATE PREPARE receipt_idempotency_columns_stmt;
SET @dispatch_request_key_exists := EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_dispatch_plan'
      AND index_name = 'uk_dispatch_plan_owner_client_request'
);
SET @receipt_request_key_exists := EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'procurement_fulfillment_confirmation'
      AND index_name =
          'uk_fulfillment_confirmation_owner_client_request'
);
SET @warehouse_idempotency_conflicting_index_count := (
    SELECT COUNT(*)
    FROM (
        SELECT table_name, index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND (
              (
                  table_name = 'procurement_dispatch_plan'
                  AND index_name =
                      'uk_dispatch_plan_owner_client_request'
              )
              OR (
                  table_name = 'procurement_fulfillment_confirmation'
                  AND index_name =
                      'uk_fulfillment_confirmation_owner_client_request'
              )
          )
        GROUP BY table_name, index_name
        HAVING NOT (
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
            ) = '1:owner_user_id,2:client_request_id'
        )
    ) AS conflicting_indexes
);
DROP TEMPORARY TABLE IF EXISTS `nuono_232_warehouse_idempotency_index_guard`;
CREATE TEMPORARY TABLE `nuono_232_warehouse_idempotency_index_guard` (
    `conflicting_index_count` BIGINT NOT NULL,
    CONSTRAINT `chk_232_warehouse_idempotency_index` CHECK (`conflicting_index_count` = 0)
) ENGINE=MEMORY;
INSERT INTO `nuono_232_warehouse_idempotency_index_guard`
VALUES (@warehouse_idempotency_conflicting_index_count);
DROP TEMPORARY TABLE `nuono_232_warehouse_idempotency_index_guard`;
SET @warehouse_idempotency_duplicate_group_count := (
    SELECT
        (
            SELECT COUNT(*)
            FROM (
                SELECT 1
                FROM `procurement_dispatch_plan`
                WHERE `client_request_id` IS NOT NULL
                GROUP BY `owner_user_id`, `client_request_id`
                HAVING COUNT(*) > 1
            ) AS `dispatch_duplicates`
        )
        +
        (
            SELECT COUNT(*)
            FROM (
                SELECT 1
                FROM `procurement_fulfillment_confirmation`
                WHERE `client_request_id` IS NOT NULL
                GROUP BY `owner_user_id`, `client_request_id`
                HAVING COUNT(*) > 1
            ) AS `receipt_duplicates`
        )
);
SET @warehouse_idempotency_invalid_request_row_count := (
    SELECT
        (
            SELECT COUNT(*)
            FROM `procurement_dispatch_plan`
            WHERE (`client_request_id` IS NULL) <> (`request_fingerprint` IS NULL)
               OR (`client_request_id` IS NOT NULL AND (
                    TRIM(`client_request_id`) = ''
                    OR BINARY `client_request_id` <> BINARY TRIM(`client_request_id`)
                    OR `client_request_id` REGEXP '[[:cntrl:]]'
                    OR NOT (`request_fingerprint` COLLATE utf8mb4_bin REGEXP '^[0-9a-f]{64}$')
               ))
        )
        +
        (
            SELECT COUNT(*)
            FROM `procurement_fulfillment_confirmation`
            WHERE (`client_request_id` IS NULL) <> (`request_fingerprint` IS NULL)
               OR (`client_request_id` IS NOT NULL AND (
                    TRIM(`client_request_id`) = ''
                    OR BINARY `client_request_id` <> BINARY TRIM(`client_request_id`)
                    OR `client_request_id` REGEXP '[[:cntrl:]]'
                    OR NOT (`request_fingerprint` COLLATE utf8mb4_bin REGEXP '^[0-9a-f]{64}$')
               ))
        )
);
DROP TEMPORARY TABLE IF EXISTS `nuono_232_warehouse_idempotency_data_guard`;
CREATE TEMPORARY TABLE `nuono_232_warehouse_idempotency_data_guard` (
    `invalid_row_count` BIGINT NOT NULL,
    CONSTRAINT `chk_232_warehouse_idempotency_data`
        CHECK (`invalid_row_count` = 0)
) ENGINE=MEMORY;
INSERT INTO `nuono_232_warehouse_idempotency_data_guard`
VALUES (
    @warehouse_idempotency_duplicate_group_count
    + @warehouse_idempotency_invalid_request_row_count
);
DROP TEMPORARY TABLE `nuono_232_warehouse_idempotency_data_guard`;
SET @dispatch_request_key_sql := IF(
    @dispatch_request_key_exists = 0,
    'ALTER TABLE `procurement_dispatch_plan`
        ADD UNIQUE KEY `uk_dispatch_plan_owner_client_request`
        (`owner_user_id`, `client_request_id`)',
    'DO 0'
);
PREPARE dispatch_request_key_stmt FROM @dispatch_request_key_sql;
EXECUTE dispatch_request_key_stmt;
DEALLOCATE PREPARE dispatch_request_key_stmt;
SET @receipt_request_key_sql := IF(
    @receipt_request_key_exists = 0,
    'ALTER TABLE `procurement_fulfillment_confirmation`
        ADD UNIQUE KEY `uk_fulfillment_confirmation_owner_client_request`
        (`owner_user_id`, `client_request_id`)',
    'DO 0'
);
PREPARE receipt_request_key_stmt FROM @receipt_request_key_sql;
EXECUTE receipt_request_key_stmt;
DEALLOCATE PREPARE receipt_request_key_stmt;
