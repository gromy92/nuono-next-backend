-- A barcode is unique within one logical store, not across every logical store.

UPDATE `product_barcode` pb
JOIN `product_variant` pv
  ON pv.id = pb.variant_id
SET pb.logical_store_id = pv.logical_store_id,
    pb.gmt_updated = NOW()
WHERE pv.logical_store_id IS NOT NULL
  AND (
    pb.logical_store_id IS NULL
    OR pb.logical_store_id <> pv.logical_store_id
  );

SET @pb_has_global_barcode_unique := (
    SELECT EXISTS(
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_barcode'
          AND INDEX_NAME = 'uk_product_barcode_barcode'
    )
);

SET @pb_has_store_barcode_unique := (
    SELECT EXISTS(
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_barcode'
          AND INDEX_NAME = 'uk_product_barcode_store_barcode'
    )
);

-- One atomic ALTER keeps the old global guard in place if NOT NULL conversion or
-- store-scoped unique-key creation fails. MODIFY NOT NULL is also the fail-closed
-- assertion for legacy/orphan rows that could not be assigned to a logical store.
SET @pb_enforce_store_barcode_unique := CONCAT(
    'ALTER TABLE `product_barcode` ',
    'MODIFY COLUMN `logical_store_id` BIGINT NOT NULL',
    IF(
        @pb_has_global_barcode_unique = 1,
        ', DROP INDEX `uk_product_barcode_barcode`',
        ''
    ),
    IF(
        @pb_has_store_barcode_unique = 1,
        '',
        ', ADD UNIQUE KEY `uk_product_barcode_store_barcode` (`logical_store_id`, `barcode`)'
    )
);
PREPARE stmt FROM @pb_enforce_store_barcode_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
