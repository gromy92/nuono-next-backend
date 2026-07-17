-- Link generated logistics batches back to their source dispatch plan.

SET @warehouse_shipping_batch_dispatch_plan_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_shipping_batch'
      AND COLUMN_NAME = 'dispatch_plan_id'
);

SET @warehouse_shipping_batch_dispatch_plan_ddl := IF(
    @warehouse_shipping_batch_dispatch_plan_exists = 0,
    'ALTER TABLE `warehouse_shipping_batch` ADD COLUMN `dispatch_plan_id` BIGINT DEFAULT NULL AFTER `owner_user_id`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_shipping_batch_dispatch_plan_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_shipping_batch_dispatch_plan_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_shipping_batch'
      AND INDEX_NAME = 'idx_shipping_batch_dispatch_plan'
);

SET @warehouse_shipping_batch_dispatch_plan_index_ddl := IF(
    @warehouse_shipping_batch_dispatch_plan_index_exists = 0,
    'ALTER TABLE `warehouse_shipping_batch` ADD KEY `idx_shipping_batch_dispatch_plan` (`dispatch_plan_id`, `is_deleted`, `gmt_updated`)',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_shipping_batch_dispatch_plan_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE warehouse_shipping_batch batch
JOIN procurement_dispatch_plan plan
  ON batch.dispatch_plan_id IS NULL
 AND batch.remark = CONCAT('来自发货申请单 ', plan.plan_no)
SET batch.dispatch_plan_id = plan.id
WHERE batch.is_deleted = b'0';
