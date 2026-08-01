-- Converge owner-scoped request idempotency for warehouse shipping batches.
-- Historical rows remain NULL; malformed or duplicate non-null keys fail closed.
SET SESSION `lock_wait_timeout` = 5;
SET SESSION `innodb_lock_wait_timeout` = 5;

SET @shipping_batch_table_exact := (
    SELECT COUNT(*) = 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_shipping_batch'
      AND table_type = 'BASE TABLE'
      AND UPPER(engine) = 'INNODB'
);

SET @shipping_batch_owner_exact := (
    SELECT COUNT(*) = 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_shipping_batch'
      AND column_name = 'owner_user_id'
      AND data_type = 'bigint'
      AND column_type = 'bigint'
      AND is_nullable = 'NO'
      AND column_default IS NULL
      AND extra = ''
      AND generation_expression = ''
);

SET @shipping_batch_idempotency_column_drift := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_shipping_batch'
      AND column_name IN ('client_request_id', 'request_fingerprint')
      AND (
          NOT (
              (
                  column_name = 'client_request_id'
                  AND data_type = 'varchar'
                  AND column_type = 'varchar(100)'
                  AND character_maximum_length = 100
                  AND character_set_name = 'utf8mb4'
                  AND collation_name = 'utf8mb4_bin'
              )
              OR (
                  column_name = 'request_fingerprint'
                  AND data_type = 'char'
                  AND column_type = 'char(64)'
                  AND character_maximum_length = 64
                  AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin'
              )
          )
          OR is_nullable <> 'YES'
          OR column_default IS NOT NULL
          OR extra <> ''
          OR generation_expression <> ''
      )
);

DROP TEMPORARY TABLE IF EXISTS `nuono_235_shipping_batch_schema_guard`;
CREATE TEMPORARY TABLE `nuono_235_shipping_batch_schema_guard` (
    `invalid_schema_count` BIGINT NOT NULL,
    CONSTRAINT `chk_235_shipping_batch_schema`
        CHECK (`invalid_schema_count` = 0)
) ENGINE=MEMORY;
INSERT INTO `nuono_235_shipping_batch_schema_guard`
VALUES (IF(
    @shipping_batch_table_exact
    AND @shipping_batch_owner_exact
    AND @shipping_batch_idempotency_column_drift = 0,
    0,
    1
));
DROP TEMPORARY TABLE `nuono_235_shipping_batch_schema_guard`;

SET @shipping_batch_client_request_exists := EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_shipping_batch'
      AND column_name = 'client_request_id'
);
SET @shipping_batch_request_fingerprint_exists := EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_shipping_batch'
      AND column_name = 'request_fingerprint'
);

SET @shipping_batch_idempotency_columns_sql := CASE
    WHEN @shipping_batch_client_request_exists = 0
        AND @shipping_batch_request_fingerprint_exists = 0
    THEN 'ALTER TABLE `warehouse_shipping_batch`
        ADD COLUMN `client_request_id` VARCHAR(100) CHARACTER SET utf8mb4
            COLLATE utf8mb4_bin NULL DEFAULT NULL AFTER `owner_user_id`,
        ADD COLUMN `request_fingerprint` CHAR(64) CHARACTER SET ascii
            COLLATE ascii_bin NULL DEFAULT NULL AFTER `client_request_id`,
        ALGORITHM=INSTANT'
    WHEN @shipping_batch_client_request_exists = 0
    THEN 'ALTER TABLE `warehouse_shipping_batch`
        ADD COLUMN `client_request_id` VARCHAR(100) CHARACTER SET utf8mb4
            COLLATE utf8mb4_bin NULL DEFAULT NULL AFTER `owner_user_id`,
        ALGORITHM=INSTANT'
    WHEN @shipping_batch_request_fingerprint_exists = 0
    THEN 'ALTER TABLE `warehouse_shipping_batch`
        ADD COLUMN `request_fingerprint` CHAR(64) CHARACTER SET ascii
            COLLATE ascii_bin NULL DEFAULT NULL AFTER `client_request_id`,
        ALGORITHM=INSTANT'
    ELSE 'DO 0'
END;
PREPARE shipping_batch_idempotency_columns_stmt
    FROM @shipping_batch_idempotency_columns_sql;
EXECUTE shipping_batch_idempotency_columns_stmt;
DEALLOCATE PREPARE shipping_batch_idempotency_columns_stmt;

SET @shipping_batch_idempotency_invalid_row_count := (
    SELECT COUNT(*)
    FROM `warehouse_shipping_batch`
    WHERE (`client_request_id` IS NULL) <> (`request_fingerprint` IS NULL)
       OR (`client_request_id` IS NOT NULL AND TRIM(`client_request_id`) = '')
       OR (`client_request_id` IS NOT NULL
           AND BINARY `client_request_id` <> BINARY TRIM(`client_request_id`))
       OR (`client_request_id` IS NOT NULL
           AND `client_request_id` REGEXP '[[:cntrl:]]')
       OR (`request_fingerprint` IS NOT NULL
           AND `request_fingerprint` NOT REGEXP '^[0-9a-f]{64}$')
);
SET @shipping_batch_idempotency_duplicate_group_count := (
    SELECT COUNT(*)
    FROM (
        SELECT 1
        FROM `warehouse_shipping_batch`
        WHERE `client_request_id` IS NOT NULL
        GROUP BY `owner_user_id`, `client_request_id`
        HAVING COUNT(*) > 1
    ) AS duplicate_request_keys
);

DROP TEMPORARY TABLE IF EXISTS `nuono_235_shipping_batch_data_guard`;
CREATE TEMPORARY TABLE `nuono_235_shipping_batch_data_guard` (
    `invalid_data_count` BIGINT NOT NULL,
    CONSTRAINT `chk_235_shipping_batch_data`
        CHECK (`invalid_data_count` = 0)
) ENGINE=MEMORY;
INSERT INTO `nuono_235_shipping_batch_data_guard`
VALUES (
    @shipping_batch_idempotency_invalid_row_count
    + @shipping_batch_idempotency_duplicate_group_count
);
DROP TEMPORARY TABLE `nuono_235_shipping_batch_data_guard`;

SET @shipping_batch_request_key_exists := EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_shipping_batch'
      AND index_name = 'uk_shipping_batch_owner_client_request'
);
SET @shipping_batch_request_key_drift := (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'warehouse_shipping_batch'
          AND index_name = 'uk_shipping_batch_owner_client_request'
        GROUP BY index_name
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

DROP TEMPORARY TABLE IF EXISTS `nuono_235_shipping_batch_index_guard`;
CREATE TEMPORARY TABLE `nuono_235_shipping_batch_index_guard` (
    `conflicting_index_count` BIGINT NOT NULL,
    CONSTRAINT `chk_235_shipping_batch_index`
        CHECK (`conflicting_index_count` = 0)
) ENGINE=MEMORY;
INSERT INTO `nuono_235_shipping_batch_index_guard`
VALUES (@shipping_batch_request_key_drift);
DROP TEMPORARY TABLE `nuono_235_shipping_batch_index_guard`;

SET @shipping_batch_request_key_sql := IF(
    @shipping_batch_request_key_exists = 0,
    'ALTER TABLE `warehouse_shipping_batch`
        ADD UNIQUE KEY `uk_shipping_batch_owner_client_request`
            (`owner_user_id`, `client_request_id`),
        ALGORITHM=INPLACE, LOCK=NONE',
    'DO 0'
);
PREPARE shipping_batch_request_key_stmt
    FROM @shipping_batch_request_key_sql;
EXECUTE shipping_batch_request_key_stmt;
DEALLOCATE PREPARE shipping_batch_request_key_stmt;
