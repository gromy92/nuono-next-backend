-- Carry Web-issued outbound and packing data needed by the warehouse keeper app.

SET @warehouse_shipping_batch_source_logical_store_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_shipping_batch_source'
      AND COLUMN_NAME = 'logical_store_id'
);

SET @warehouse_shipping_batch_source_logical_store_ddl := IF(
    @warehouse_shipping_batch_source_logical_store_exists = 0,
    'ALTER TABLE `warehouse_shipping_batch_source` ADD COLUMN `logical_store_id` BIGINT DEFAULT NULL AFTER `owner_user_id`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_shipping_batch_source_logical_store_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_outbound_order_line_logical_store_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_outbound_order_line'
      AND COLUMN_NAME = 'logical_store_id'
);

SET @warehouse_outbound_order_line_logical_store_ddl := IF(
    @warehouse_outbound_order_line_logical_store_exists = 0,
    'ALTER TABLE `warehouse_outbound_order_line` ADD COLUMN `logical_store_id` BIGINT DEFAULT NULL AFTER `owner_user_id`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_outbound_order_line_logical_store_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_outbound_order_line_source_store_code_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_outbound_order_line'
      AND COLUMN_NAME = 'source_store_code'
);

SET @warehouse_outbound_order_line_source_store_code_ddl := IF(
    @warehouse_outbound_order_line_source_store_code_exists = 0,
    'ALTER TABLE `warehouse_outbound_order_line` ADD COLUMN `source_store_code` VARCHAR(100) DEFAULT NULL AFTER `logical_store_id`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_outbound_order_line_source_store_code_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_outbound_order_line_source_store_name_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_outbound_order_line'
      AND COLUMN_NAME = 'source_store_name'
);

SET @warehouse_outbound_order_line_source_store_name_ddl := IF(
    @warehouse_outbound_order_line_source_store_name_exists = 0,
    'ALTER TABLE `warehouse_outbound_order_line` ADD COLUMN `source_store_name` VARCHAR(200) DEFAULT NULL AFTER `source_store_code`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_outbound_order_line_source_store_name_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_outbound_order_line_source_store_code_snapshot_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_outbound_order_line_source'
      AND COLUMN_NAME = 'source_store_code'
);

SET @warehouse_outbound_order_line_source_store_code_snapshot_ddl := IF(
    @warehouse_outbound_order_line_source_store_code_snapshot_exists = 0,
    'ALTER TABLE `warehouse_outbound_order_line_source` ADD COLUMN `source_store_code` VARCHAR(100) DEFAULT NULL AFTER `fulfillment_balance_id`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_outbound_order_line_source_store_code_snapshot_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_outbound_order_line_source_store_name_snapshot_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_outbound_order_line_source'
      AND COLUMN_NAME = 'source_store_name'
);

SET @warehouse_outbound_order_line_source_store_name_snapshot_ddl := IF(
    @warehouse_outbound_order_line_source_store_name_snapshot_exists = 0,
    'ALTER TABLE `warehouse_outbound_order_line_source` ADD COLUMN `source_store_name` VARCHAR(200) DEFAULT NULL AFTER `source_store_code`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_outbound_order_line_source_store_name_snapshot_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @warehouse_packing_box_status_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'warehouse_packing_box'
      AND COLUMN_NAME = 'status'
);

SET @warehouse_packing_box_status_ddl := IF(
    @warehouse_packing_box_status_exists = 0,
    'ALTER TABLE `warehouse_packing_box` ADD COLUMN `status` VARCHAR(40) NOT NULL DEFAULT ''DRAFT'' AFTER `box_no`',
    'SELECT 1'
);

PREPARE stmt FROM @warehouse_packing_box_status_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE warehouse_packing_box box
JOIN warehouse_packing_list packing_list
  ON packing_list.id = box.packing_list_id
 AND packing_list.is_deleted = b'0'
SET box.status = 'SEALED'
WHERE box.status = 'DRAFT'
  AND packing_list.status IN ('CONFIRMED', 'SHIPPED')
  AND box.is_deleted = b'0';

UPDATE warehouse_shipping_batch_source source
JOIN procurement_fulfillment_balance balance
  ON balance.id = source.fulfillment_balance_id
 AND balance.is_deleted = b'0'
SET source.logical_store_id = balance.logical_store_id
WHERE source.logical_store_id IS NULL
  AND source.is_deleted = b'0';

UPDATE warehouse_outbound_order_line_source outbound_source
JOIN warehouse_shipping_batch_source batch_source
  ON batch_source.id = outbound_source.batch_source_id
 AND batch_source.is_deleted = b'0'
SET outbound_source.source_store_code = COALESCE(outbound_source.source_store_code, batch_source.source_store_code),
    outbound_source.source_store_name = COALESCE(outbound_source.source_store_name, batch_source.source_store_name)
WHERE outbound_source.is_deleted = b'0'
  AND (outbound_source.source_store_code IS NULL OR outbound_source.source_store_name IS NULL);

UPDATE warehouse_outbound_order_line outbound_line
JOIN (
    SELECT outbound_source.outbound_order_line_id, MIN(outbound_source.batch_source_id) AS batch_source_id
    FROM warehouse_outbound_order_line_source outbound_source
    WHERE outbound_source.is_deleted = b'0'
    GROUP BY outbound_source.outbound_order_line_id
) first_source
  ON first_source.outbound_order_line_id = outbound_line.id
JOIN warehouse_shipping_batch_source batch_source
  ON batch_source.id = first_source.batch_source_id
 AND batch_source.is_deleted = b'0'
SET outbound_line.logical_store_id = COALESCE(outbound_line.logical_store_id, batch_source.logical_store_id),
    outbound_line.source_store_code = COALESCE(outbound_line.source_store_code, batch_source.source_store_code),
    outbound_line.source_store_name = COALESCE(outbound_line.source_store_name, batch_source.source_store_name)
WHERE outbound_line.is_deleted = b'0'
  AND (
      outbound_line.logical_store_id IS NULL
      OR outbound_line.source_store_code IS NULL
      OR outbound_line.source_store_name IS NULL
  );

UPDATE warehouse_outbound_order_line outbound_line
JOIN product_master product
  ON product.id = outbound_line.product_master_id
 AND product.is_deleted = b'0'
JOIN logical_store store
  ON store.id = product.logical_store_id
 AND store.is_deleted = b'0'
JOIN logical_store_site store_site
  ON store_site.logical_store_id = product.logical_store_id
 AND UPPER(store_site.site) = UPPER(outbound_line.site_code)
 AND store_site.is_deleted = b'0'
SET outbound_line.logical_store_id = COALESCE(outbound_line.logical_store_id, product.logical_store_id),
    outbound_line.source_store_code = COALESCE(outbound_line.source_store_code, store_site.store_code),
    outbound_line.source_store_name = COALESCE(
        outbound_line.source_store_name,
        NULLIF(store.project_name, ''),
        store_site.store_code
    )
WHERE outbound_line.is_deleted = b'0'
  AND (
      outbound_line.logical_store_id IS NULL
      OR outbound_line.source_store_code IS NULL
      OR outbound_line.source_store_name IS NULL
  );

UPDATE warehouse_outbound_order_line_source outbound_source
JOIN warehouse_outbound_order_line outbound_line
  ON outbound_line.id = outbound_source.outbound_order_line_id
 AND outbound_line.is_deleted = b'0'
SET outbound_source.source_store_code = COALESCE(outbound_source.source_store_code, outbound_line.source_store_code),
    outbound_source.source_store_name = COALESCE(outbound_source.source_store_name, outbound_line.source_store_name)
WHERE outbound_source.is_deleted = b'0'
  AND (outbound_source.source_store_code IS NULL OR outbound_source.source_store_name IS NULL);
