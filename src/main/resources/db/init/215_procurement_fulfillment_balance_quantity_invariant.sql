-- Enforce the warehouse fulfillment-balance quantity state.
-- Existing violations require separately scoped data review; never repair them
-- as a side effect of this schema migration.
-- Separate named checks keep exact metadata verification grouping-safe.
SET SESSION `lock_wait_timeout` = 5;
SET SESSION `innodb_lock_wait_timeout` = 5;

SET @fulfillment_balance_table_count := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND BINARY table_name =
          BINARY 'procurement_fulfillment_balance'
      AND BINARY table_type = BINARY 'BASE TABLE'
      AND BINARY UPPER(engine) = BINARY 'INNODB'
);

SET @fulfillment_balance_quantity_column_count := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND BINARY table_name =
          BINARY 'procurement_fulfillment_balance'
      AND (
          BINARY column_name = BINARY 'planned_quantity'
          OR BINARY column_name = BINARY 'confirmed_quantity'
          OR BINARY column_name = BINARY 'abnormal_quantity'
          OR BINARY column_name = BINARY 'reserved_quantity'
          OR BINARY column_name =
              BINARY 'logistics_handoff_quantity'
          OR BINARY column_name = BINARY 'available_quantity'
      )
      AND BINARY data_type = BINARY 'int'
      AND LOWER(column_type) NOT LIKE '%unsigned%'
      AND BINARY is_nullable = BINARY 'NO'
      AND CAST(column_default AS CHAR) = '0'
);

DROP TEMPORARY TABLE IF EXISTS
    `nuono_215_fulfillment_balance_schema_guard`;

CREATE TEMPORARY TABLE
    `nuono_215_fulfillment_balance_schema_guard` (
        `invalid_schema_count` BIGINT NOT NULL,
        CONSTRAINT `chk_215_fulfillment_balance_schema`
            CHECK (`invalid_schema_count` = 0)
    ) ENGINE=MEMORY;

INSERT INTO `nuono_215_fulfillment_balance_schema_guard`
    (`invalid_schema_count`)
VALUES (
    IF(
        @fulfillment_balance_table_count = 1
            AND @fulfillment_balance_quantity_column_count = 6,
        0,
        1
    )
);

DROP TEMPORARY TABLE `nuono_215_fulfillment_balance_schema_guard`;

SET @fulfillment_balance_invalid_row_count := (
    SELECT COUNT(*)
    FROM `procurement_fulfillment_balance`
    WHERE `planned_quantity` < 0
       OR `confirmed_quantity` < 0
       OR `abnormal_quantity` < 0
       OR `reserved_quantity` < 0
       OR `logistics_handoff_quantity` < 0
       OR `available_quantity` < 0
       OR `confirmed_quantity` <> (
           `abnormal_quantity`
           + `reserved_quantity`
           + `logistics_handoff_quantity`
           + `available_quantity`
       )
);

DROP TEMPORARY TABLE IF EXISTS
    `nuono_215_fulfillment_balance_data_guard`;

CREATE TEMPORARY TABLE
    `nuono_215_fulfillment_balance_data_guard` (
        `invalid_row_count` BIGINT NOT NULL,
        CONSTRAINT `chk_215_fulfillment_balance_data`
            CHECK (`invalid_row_count` = 0)
    ) ENGINE=MEMORY;

INSERT INTO `nuono_215_fulfillment_balance_data_guard`
    (`invalid_row_count`)
VALUES (@fulfillment_balance_invalid_row_count);

DROP TEMPORARY TABLE `nuono_215_fulfillment_balance_data_guard`;

SET @fulfillment_balance_constraint_symbol_count := (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_name IN (
          'chk_fulfillment_balance_planned_nonnegative',
          'chk_fulfillment_balance_confirmed_nonnegative',
          'chk_fulfillment_balance_abnormal_nonnegative',
          'chk_fulfillment_balance_reserved_nonnegative',
          'chk_fulfillment_balance_logistics_handoff_nonnegative',
          'chk_fulfillment_balance_available_nonnegative',
          'chk_fulfillment_balance_quantity_conservation'
      )
);

SET @fulfillment_balance_constraint_is_exact := (
    SELECT IF(
        COUNT(*) = 7
            AND SUM(
                CASE
                    WHEN BINARY constraints.constraint_name =
                        BINARY 'chk_fulfillment_balance_planned_nonnegative'
                    THEN BINARY REGEXP_REPLACE(
                        COALESCE(checks.check_clause, ''),
                        '[[:space:]`]+' ,
                        ''
                    ) = BINARY '(planned_quantity>=0)'
                    WHEN BINARY constraints.constraint_name =
                        BINARY 'chk_fulfillment_balance_confirmed_nonnegative'
                    THEN BINARY REGEXP_REPLACE(
                        COALESCE(checks.check_clause, ''),
                        '[[:space:]`]+' ,
                        ''
                    ) = BINARY '(confirmed_quantity>=0)'
                    WHEN BINARY constraints.constraint_name =
                        BINARY 'chk_fulfillment_balance_abnormal_nonnegative'
                    THEN BINARY REGEXP_REPLACE(
                        COALESCE(checks.check_clause, ''),
                        '[[:space:]`]+' ,
                        ''
                    ) = BINARY '(abnormal_quantity>=0)'
                    WHEN BINARY constraints.constraint_name =
                        BINARY 'chk_fulfillment_balance_reserved_nonnegative'
                    THEN BINARY REGEXP_REPLACE(
                        COALESCE(checks.check_clause, ''),
                        '[[:space:]`]+' ,
                        ''
                    ) = BINARY '(reserved_quantity>=0)'
                    WHEN BINARY constraints.constraint_name = BINARY
                        'chk_fulfillment_balance_logistics_handoff_nonnegative'
                    THEN BINARY REGEXP_REPLACE(
                        COALESCE(checks.check_clause, ''),
                        '[[:space:]`]+' ,
                        ''
                    ) = BINARY '(logistics_handoff_quantity>=0)'
                    WHEN BINARY constraints.constraint_name =
                        BINARY 'chk_fulfillment_balance_available_nonnegative'
                    THEN BINARY REGEXP_REPLACE(
                        COALESCE(checks.check_clause, ''),
                        '[[:space:]`]+' ,
                        ''
                    ) = BINARY '(available_quantity>=0)'
                    WHEN BINARY constraints.constraint_name =
                        BINARY 'chk_fulfillment_balance_quantity_conservation'
                    THEN BINARY REGEXP_REPLACE(
                        COALESCE(checks.check_clause, ''),
                        '[[:space:]`]+' ,
                        ''
                    ) = BINARY
                        '(confirmed_quantity='
                        '(((abnormal_quantity+reserved_quantity)'
                        '+logistics_handoff_quantity)+available_quantity))'
                    ELSE 0
                END
            ) = 7,
        1,
        0
    )
    FROM information_schema.table_constraints constraints
    JOIN information_schema.check_constraints checks
      ON checks.constraint_schema = constraints.constraint_schema
     AND checks.constraint_name = constraints.constraint_name
    WHERE constraints.constraint_schema = DATABASE()
      AND (
          BINARY constraints.constraint_name =
              BINARY 'chk_fulfillment_balance_planned_nonnegative'
          OR BINARY constraints.constraint_name =
              BINARY 'chk_fulfillment_balance_confirmed_nonnegative'
          OR BINARY constraints.constraint_name =
              BINARY 'chk_fulfillment_balance_abnormal_nonnegative'
          OR BINARY constraints.constraint_name =
              BINARY 'chk_fulfillment_balance_reserved_nonnegative'
          OR BINARY constraints.constraint_name = BINARY
              'chk_fulfillment_balance_logistics_handoff_nonnegative'
          OR BINARY constraints.constraint_name =
              BINARY 'chk_fulfillment_balance_available_nonnegative'
          OR BINARY constraints.constraint_name =
              BINARY 'chk_fulfillment_balance_quantity_conservation'
      )
      AND BINARY constraints.table_name =
          BINARY 'procurement_fulfillment_balance'
      AND BINARY constraints.constraint_type = BINARY 'CHECK'
      AND BINARY constraints.enforced = BINARY 'YES'
);

DROP TEMPORARY TABLE IF EXISTS
    `nuono_215_fulfillment_balance_constraint_guard`;

CREATE TEMPORARY TABLE
    `nuono_215_fulfillment_balance_constraint_guard` (
        `conflicting_constraint_count` BIGINT NOT NULL,
        CONSTRAINT `chk_215_fulfillment_balance_constraint`
            CHECK (`conflicting_constraint_count` = 0)
    ) ENGINE=MEMORY;

INSERT INTO `nuono_215_fulfillment_balance_constraint_guard`
    (`conflicting_constraint_count`)
VALUES (
    IF(
        @fulfillment_balance_constraint_symbol_count = 0
            OR @fulfillment_balance_constraint_is_exact = 1,
        0,
        1
    )
);

DROP TEMPORARY TABLE
    `nuono_215_fulfillment_balance_constraint_guard`;

SET @fulfillment_balance_add_constraint_sql := IF(
    @fulfillment_balance_constraint_symbol_count = 0,
    'ALTER TABLE `procurement_fulfillment_balance`
        ADD CONSTRAINT `chk_fulfillment_balance_planned_nonnegative`
            CHECK (`planned_quantity` >= 0) ENFORCED,
        ADD CONSTRAINT `chk_fulfillment_balance_confirmed_nonnegative`
            CHECK (`confirmed_quantity` >= 0) ENFORCED,
        ADD CONSTRAINT `chk_fulfillment_balance_abnormal_nonnegative`
            CHECK (`abnormal_quantity` >= 0) ENFORCED,
        ADD CONSTRAINT `chk_fulfillment_balance_reserved_nonnegative`
            CHECK (`reserved_quantity` >= 0) ENFORCED,
        ADD CONSTRAINT `chk_fulfillment_balance_logistics_handoff_nonnegative`
            CHECK (`logistics_handoff_quantity` >= 0) ENFORCED,
        ADD CONSTRAINT `chk_fulfillment_balance_available_nonnegative`
            CHECK (`available_quantity` >= 0) ENFORCED,
        ADD CONSTRAINT `chk_fulfillment_balance_quantity_conservation`
            CHECK (
                `confirmed_quantity` = (
                `abnormal_quantity`
                + `reserved_quantity`
                + `logistics_handoff_quantity`
                + `available_quantity`
                )
            ) ENFORCED',
    'DO 0'
);

PREPARE fulfillment_balance_add_constraint_stmt
    FROM @fulfillment_balance_add_constraint_sql;
EXECUTE fulfillment_balance_add_constraint_stmt;
DEALLOCATE PREPARE fulfillment_balance_add_constraint_stmt;
