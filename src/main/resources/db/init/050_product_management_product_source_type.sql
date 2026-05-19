-- Distinguish self-built Z-code products from follow-sell N-code products in product management.

SET NAMES utf8mb4;

SET @add_product_master_source_type := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_master'
        AND COLUMN_NAME = 'product_source_type'
    ),
    'SELECT 1',
    'ALTER TABLE `product_master` ADD COLUMN `product_source_type` VARCHAR(30) NOT NULL DEFAULT ''SELF_BUILT'' AFTER `sku_parent`'
  )
);
PREPARE stmt FROM @add_product_master_source_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_master_source_type_index := (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'product_master'
        AND INDEX_NAME = 'idx_product_master_source_type'
    ),
    'SELECT 1',
    'ALTER TABLE `product_master` ADD KEY `idx_product_master_source_type` (`logical_store_id`, `product_source_type`)'
  )
);
PREPARE stmt FROM @add_product_master_source_type_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_master` pm
SET pm.`product_source_type` = CASE
  WHEN EXISTS (
    SELECT 1
    FROM `product_variant` pv
    WHERE pv.`product_master_id` = pm.`id`
      AND pv.`is_deleted` = b'0'
      AND UPPER(COALESCE(pv.`child_sku`, '')) LIKE 'N%'
  ) THEN 'FOLLOW_SELL'
  WHEN UPPER(COALESCE(pm.`sku_parent`, '')) LIKE 'N%' THEN 'FOLLOW_SELL'
  ELSE 'SELF_BUILT'
END
WHERE pm.`is_deleted` = b'0';
