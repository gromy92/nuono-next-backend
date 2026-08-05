SET @add_official_warehouse_asn_line_manual_quantity := IF(
  EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_asn_line'
      AND COLUMN_NAME = 'manual_quantity'
  ),
  'SELECT ''official_warehouse_asn_line_manual_quantity_exists'' AS stage',
  'ALTER TABLE official_warehouse_asn_line ADD COLUMN manual_quantity INT DEFAULT NULL AFTER qty'
);
PREPARE add_official_warehouse_asn_line_manual_quantity_stmt
  FROM @add_official_warehouse_asn_line_manual_quantity;
EXECUTE add_official_warehouse_asn_line_manual_quantity_stmt;
DEALLOCATE PREPARE add_official_warehouse_asn_line_manual_quantity_stmt;
