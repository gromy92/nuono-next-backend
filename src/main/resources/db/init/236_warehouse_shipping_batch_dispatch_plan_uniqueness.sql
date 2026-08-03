-- Enforce one active logistics batch per warehouse dispatch plan.
-- Relationship anomalies are evidence for a separate correction and fail closed here.
SET SESSION `lock_wait_timeout` = 5;
SET SESSION `innodb_lock_wait_timeout` = 5;

SET @shipping_plan_link_table_count := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('warehouse_shipping_batch', 'procurement_dispatch_plan')
      AND table_type = 'BASE TABLE'
      AND UPPER(engine) = 'INNODB'
);
SET @shipping_plan_link_base_column_count := (
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
);
SET @shipping_plan_parent_primary_index_exact := (
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
);
SET @shipping_plan_link_lookup_index_exact := (
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
);

DROP TEMPORARY TABLE IF EXISTS `nuono_236_shipping_plan_link_base_guard`;
CREATE TEMPORARY TABLE `nuono_236_shipping_plan_link_base_guard` (
    `invalid_schema_count` BIGINT NOT NULL,
    CONSTRAINT `chk_236_shipping_plan_link_base`
        CHECK (`invalid_schema_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_236_shipping_plan_link_base_guard`
VALUES (IF(
    @shipping_plan_link_table_count = 2
        AND @shipping_plan_link_base_column_count = 6
        AND @shipping_plan_parent_primary_index_exact = 1
        AND @shipping_plan_link_lookup_index_exact = 1,
    0,
    1
));
DROP TEMPORARY TABLE `nuono_236_shipping_plan_link_base_guard`;

SET @shipping_plan_active_column_count := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_shipping_batch'
      AND column_name = 'active_dispatch_plan_id'
);
SET @shipping_plan_active_column_exact := (
    SELECT COUNT(*) = 1
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
);
SET @shipping_plan_active_index_count := (
    SELECT COUNT(DISTINCT index_name)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'warehouse_shipping_batch'
      AND index_name = 'uk_shipping_batch_active_dispatch_plan'
);
SET @shipping_plan_active_index_exact := (
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
);
SET @shipping_plan_uniqueness_fully_applied := (
    @shipping_plan_active_column_count = 1
    AND @shipping_plan_active_column_exact = 1
    AND @shipping_plan_active_index_count = 1
    AND @shipping_plan_active_index_exact = 1
);

DROP TEMPORARY TABLE IF EXISTS `nuono_236_shipping_plan_link_target_guard`;
CREATE TEMPORARY TABLE `nuono_236_shipping_plan_link_target_guard` (
    `invalid_schema_count` BIGINT NOT NULL,
    CONSTRAINT `chk_236_shipping_plan_link_target`
        CHECK (`invalid_schema_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_236_shipping_plan_link_target_guard`
VALUES (IF(
    (
        @shipping_plan_active_column_count = 0
        AND @shipping_plan_active_index_count = 0
    )
    OR @shipping_plan_uniqueness_fully_applied,
    0,
    1
));
DROP TEMPORARY TABLE `nuono_236_shipping_plan_link_target_guard`;

SET @shipping_plan_duplicate_active_count := (
    SELECT COUNT(*)
    FROM (
        SELECT dispatch_plan_id
        FROM warehouse_shipping_batch
        WHERE is_deleted = b'0'
          AND dispatch_plan_id IS NOT NULL
        GROUP BY dispatch_plan_id
        HAVING COUNT(*) > 1
    ) duplicate_active
);
SET @shipping_plan_orphan_count := (
    SELECT COUNT(*)
    FROM warehouse_shipping_batch batch
    LEFT JOIN procurement_dispatch_plan plan
      ON plan.id = batch.dispatch_plan_id
    WHERE batch.dispatch_plan_id IS NOT NULL
      AND plan.id IS NULL
);
SET @shipping_plan_deleted_parent_count := (
    SELECT COUNT(*)
    FROM warehouse_shipping_batch batch
    JOIN procurement_dispatch_plan plan
      ON plan.id = batch.dispatch_plan_id
    WHERE batch.is_deleted = b'0'
      AND NOT (plan.is_deleted <=> b'0')
);
SET @shipping_plan_owner_mismatch_count := (
    SELECT COUNT(*)
    FROM warehouse_shipping_batch batch
    JOIN procurement_dispatch_plan plan
      ON plan.id = batch.dispatch_plan_id
    WHERE batch.dispatch_plan_id IS NOT NULL
      AND NOT (batch.owner_user_id <=> plan.owner_user_id)
);

DROP TEMPORARY TABLE IF EXISTS `nuono_236_shipping_plan_link_data_guard`;
CREATE TEMPORARY TABLE `nuono_236_shipping_plan_link_data_guard` (
    `invalid_data_count` BIGINT NOT NULL,
    CONSTRAINT `chk_236_shipping_plan_link_data`
        CHECK (`invalid_data_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_236_shipping_plan_link_data_guard`
VALUES (
    @shipping_plan_duplicate_active_count
    + @shipping_plan_orphan_count
    + @shipping_plan_deleted_parent_count
    + @shipping_plan_owner_mismatch_count
);
DROP TEMPORARY TABLE `nuono_236_shipping_plan_link_data_guard`;

SET @shipping_plan_uniqueness_sql := IF(
    @shipping_plan_uniqueness_fully_applied,
    'DO 0',
    'ALTER TABLE `warehouse_shipping_batch`
        ADD COLUMN `active_dispatch_plan_id` BIGINT
            GENERATED ALWAYS AS (
                CASE
                    WHEN `is_deleted` = b''0'' THEN `dispatch_plan_id`
                    ELSE NULL
                END
            ) STORED
            AFTER `dispatch_plan_id`,
        ADD UNIQUE KEY `uk_shipping_batch_active_dispatch_plan`
            (`active_dispatch_plan_id`)'
);
PREPARE shipping_plan_uniqueness_stmt FROM @shipping_plan_uniqueness_sql;
EXECUTE shipping_plan_uniqueness_stmt;
DEALLOCATE PREPARE shipping_plan_uniqueness_stmt;
