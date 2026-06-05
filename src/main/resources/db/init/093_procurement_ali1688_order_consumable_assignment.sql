-- Adds first-class consumable assignment target support for 1688 historical order product lines.

SET @add_ali1688_order_assignment_target_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_ali1688_order_item_assignment'
              AND COLUMN_NAME = 'target_type'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_ali1688_order_item_assignment` ADD COLUMN `target_type` VARCHAR(30) NOT NULL DEFAULT ''STORE_SITE'' AFTER `item_id`'
    )
);
PREPARE stmt FROM @add_ali1688_order_assignment_target_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `procurement_ali1688_order_item_assignment`
    MODIFY COLUMN `target_store_code` VARCHAR(120) DEFAULT NULL,
    MODIFY COLUMN `target_site_code` VARCHAR(40) DEFAULT NULL;

UPDATE `procurement_ali1688_order_item_assignment`
SET `target_type` = 'STORE_SITE',
    `target_site_code` = COALESCE(`target_site_code`, '*')
WHERE (`target_type` IS NULL OR TRIM(`target_type`) = '')
  AND `is_deleted` = b'0';
