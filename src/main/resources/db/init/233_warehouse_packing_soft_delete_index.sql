-- Bound packing-item soft deletes and active reads to one packing list.
-- A conflicting canonical index fails closed instead of being replaced.
SET SESSION `lock_wait_timeout` = 5;
SET SESSION `innodb_lock_wait_timeout` = 5;

SET @packing_item_table_count := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_packing_box_item'
      AND table_type = 'BASE TABLE'
      AND UPPER(engine) = 'INNODB'
);

SET @packing_item_key_column_count := (
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
);

DROP TEMPORARY TABLE IF EXISTS `nuono_233_packing_item_schema_guard`;
CREATE TEMPORARY TABLE `nuono_233_packing_item_schema_guard` (
    `invalid_schema_count` BIGINT NOT NULL,
    CONSTRAINT `chk_233_packing_item_schema`
        CHECK (`invalid_schema_count` = 0)
) ENGINE=MEMORY;
INSERT INTO `nuono_233_packing_item_schema_guard`
VALUES (IF(
    @packing_item_table_count = 1
        AND @packing_item_key_column_count = 2,
    0,
    1
));
DROP TEMPORARY TABLE `nuono_233_packing_item_schema_guard`;

SET @packing_item_list_index_exists := EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_packing_box_item'
      AND index_name = 'idx_packing_box_item_list'
);

SET @packing_item_list_index_is_exact := (
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
);

DROP TEMPORARY TABLE IF EXISTS `nuono_233_packing_item_index_guard`;
CREATE TEMPORARY TABLE `nuono_233_packing_item_index_guard` (
    `conflicting_index_count` BIGINT NOT NULL,
    CONSTRAINT `chk_233_packing_item_index`
        CHECK (`conflicting_index_count` = 0)
) ENGINE=MEMORY;
INSERT INTO `nuono_233_packing_item_index_guard`
VALUES (IF(
    @packing_item_list_index_exists = 1
        AND @packing_item_list_index_is_exact = 0,
    1,
    0
));
DROP TEMPORARY TABLE `nuono_233_packing_item_index_guard`;

SET @packing_item_list_index_sql := IF(
    @packing_item_list_index_exists = 0,
    'ALTER TABLE `warehouse_packing_box_item`
        ADD KEY `idx_packing_box_item_list` (`packing_list_id`, `is_deleted`),
        ALGORITHM=INPLACE, LOCK=NONE',
    'DO 0'
);
PREPARE packing_item_list_index_stmt
    FROM @packing_item_list_index_sql;
EXECUTE packing_item_list_index_stmt;
DEALLOCATE PREPARE packing_item_list_index_stmt;
