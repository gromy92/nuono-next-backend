SET NAMES utf8mb4;

SET @official_warehouse_departure_column_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'official_warehouse_appointment'
              AND COLUMN_NAME = 'warehouse_from'
              AND IS_NULLABLE = 'NO'
        ),
        'ALTER TABLE `official_warehouse_appointment` MODIFY COLUMN `warehouse_from` VARCHAR(120) DEFAULT NULL',
        'SELECT 1'
    )
);

PREPARE official_warehouse_departure_column_stmt FROM @official_warehouse_departure_column_sql;
EXECUTE official_warehouse_departure_column_stmt;
DEALLOCATE PREPARE official_warehouse_departure_column_stmt;
