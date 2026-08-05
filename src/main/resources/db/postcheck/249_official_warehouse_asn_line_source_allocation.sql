SELECT IF(
  (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_asn_line'
      AND COLUMN_NAME = 'manual_quantity'
      AND DATA_TYPE = 'int'
      AND IS_NULLABLE = 'YES'
  ) = 1,
  1,
  0
) AS official_warehouse_asn_line_source_allocation_ready;
