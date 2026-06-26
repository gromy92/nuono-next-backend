SET @official_warehouse_appointment_add_gate := (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'official_warehouse_appointment'
        AND COLUMN_NAME = 'gate'
    ),
    'ALTER TABLE `official_warehouse_appointment` ADD COLUMN `gate` VARCHAR(200) DEFAULT NULL AFTER `appointment_time`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @official_warehouse_appointment_add_gate;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @official_warehouse_appointment_add_docks := (
  SELECT IF(
    NOT EXISTS (
      SELECT 1
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'official_warehouse_appointment'
        AND COLUMN_NAME = 'docks'
    ),
    'ALTER TABLE `official_warehouse_appointment` ADD COLUMN `docks` VARCHAR(500) DEFAULT NULL AFTER `gate`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @official_warehouse_appointment_add_docks;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
