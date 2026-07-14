-- Product barcode PSKU identity.
-- product_barcode.variant_id remains a compatibility row reference, while cleanup and lookup
-- can use the current PSKU product row address directly.

SET @pb_add_product_master_id := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_barcode` ADD COLUMN `product_master_id` BIGINT DEFAULT NULL AFTER `id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_barcode'
      AND COLUMN_NAME = 'product_master_id'
);
PREPARE stmt FROM @pb_add_product_master_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pb_add_logical_store_id := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_barcode` ADD COLUMN `logical_store_id` BIGINT DEFAULT NULL AFTER `product_master_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_barcode'
      AND COLUMN_NAME = 'logical_store_id'
);
PREPARE stmt FROM @pb_add_logical_store_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pb_add_partner_sku := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_barcode` ADD COLUMN `partner_sku` VARCHAR(100) DEFAULT NULL AFTER `logical_store_id`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_barcode'
      AND COLUMN_NAME = 'partner_sku'
);
PREPARE stmt FROM @pb_add_partner_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_barcode` barcode
JOIN `product_variant` pv
  ON pv.id = barcode.variant_id
JOIN `product_master` pm
  ON pm.id = pv.product_master_id
SET barcode.product_master_id = pm.id,
    barcode.logical_store_id = pm.logical_store_id,
    barcode.partner_sku = TRIM(pv.partner_sku),
    barcode.gmt_updated = NOW()
WHERE barcode.is_deleted = b'0'
  AND pv.is_deleted = b'0'
  AND pm.is_deleted = b'0'
  AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL
  AND (
      barcode.product_master_id IS NULL
      OR barcode.product_master_id <> pm.id
      OR barcode.logical_store_id IS NULL
      OR barcode.logical_store_id <> pm.logical_store_id
      OR NULLIF(TRIM(barcode.partner_sku), '') IS NULL
      OR TRIM(barcode.partner_sku) <> TRIM(pv.partner_sku)
  );

SET @pb_add_master_index := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_barcode` ADD KEY `idx_product_barcode_master` (`product_master_id`, `is_deleted`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_barcode'
      AND INDEX_NAME = 'idx_product_barcode_master'
);
PREPARE stmt FROM @pb_add_master_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pb_add_store_psku_index := (
    SELECT IF(
        COUNT(1) = 0,
        'ALTER TABLE `product_barcode` ADD KEY `idx_product_barcode_store_psku` (`logical_store_id`, `partner_sku`, `is_deleted`)',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_barcode'
      AND INDEX_NAME = 'idx_product_barcode_store_psku'
);
PREPARE stmt FROM @pb_add_store_psku_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
