-- Add version source labels to advanced operations configuration tables.

SET NAMES utf8mb4;

SET @add_operation_calendar_rule_source_role := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_calendar_rule'
        AND COLUMN_NAME = 'publish_source_role'
    ),
    'SELECT 1',
    'ALTER TABLE `operation_calendar_rule` ADD COLUMN `publish_source_role` VARCHAR(60) NOT NULL DEFAULT ''operator'' AFTER `publish_status`'
  )
);
PREPARE stmt FROM @add_operation_calendar_rule_source_role;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_operation_calendar_rule_source_label := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_calendar_rule'
        AND COLUMN_NAME = 'publish_source_label'
    ),
    'SELECT 1',
    'ALTER TABLE `operation_calendar_rule` ADD COLUMN `publish_source_label` VARCHAR(80) NOT NULL DEFAULT ''运营发布'' AFTER `publish_source_role`'
  )
);
PREPARE stmt FROM @add_operation_calendar_rule_source_label;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_operation_lifecycle_rule_source_role := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_lifecycle_rule'
        AND COLUMN_NAME = 'publish_source_role'
    ),
    'SELECT 1',
    'ALTER TABLE `operation_lifecycle_rule` ADD COLUMN `publish_source_role` VARCHAR(60) NOT NULL DEFAULT ''operator'' AFTER `publish_status`'
  )
);
PREPARE stmt FROM @add_operation_lifecycle_rule_source_role;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_operation_lifecycle_rule_source_label := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'operation_lifecycle_rule'
        AND COLUMN_NAME = 'publish_source_label'
    ),
    'SELECT 1',
    'ALTER TABLE `operation_lifecycle_rule` ADD COLUMN `publish_source_label` VARCHAR(80) NOT NULL DEFAULT ''运营发布'' AFTER `publish_source_role`'
  )
);
PREPARE stmt FROM @add_operation_lifecycle_rule_source_label;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
