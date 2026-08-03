SELECT IF(
    (
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND BINARY table_name =
              BINARY 'procurement_fulfillment_balance'
          AND BINARY table_type = BINARY 'BASE TABLE'
          AND BINARY UPPER(engine) = BINARY 'INNODB'
    ) = 1
    AND (
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
    ) = 6
    AND (
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
    ) = 7
    AND (
        SELECT COUNT(*) = 7
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
            ) = 7
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
    ),
    1,
    0
);
