SET @official_warehouse_asn_add_noon_updated_at := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `official_warehouse_asn` ADD COLUMN `noon_updated_at` DATETIME DEFAULT NULL AFTER `noon_asn_status`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'official_warehouse_asn'
      AND COLUMN_NAME = 'noon_updated_at'
);
PREPARE stmt FROM @official_warehouse_asn_add_noon_updated_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
