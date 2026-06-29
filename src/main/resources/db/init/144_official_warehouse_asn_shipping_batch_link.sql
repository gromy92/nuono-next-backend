CREATE TABLE IF NOT EXISTS `official_warehouse_asn_shipping_batch_link` (
  `id` BIGINT NOT NULL,
  `asn_id` BIGINT NOT NULL,
  `asn_line_id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `store_code` VARCHAR(100) NOT NULL,
  `site_code` VARCHAR(20) NOT NULL,
  `shipping_batch_id` BIGINT DEFAULT NULL,
  `shipping_batch_no` VARCHAR(80) DEFAULT NULL,
  `shipping_batch_source_id` BIGINT DEFAULT NULL,
  `in_transit_batch_id` BIGINT DEFAULT NULL,
  `batch_reference_no` VARCHAR(160) DEFAULT NULL,
  `tracking_no` VARCHAR(160) DEFAULT NULL,
  `external_shipment_no` VARCHAR(160) DEFAULT NULL,
  `forwarder_name` VARCHAR(255) DEFAULT NULL,
  `transport_mode` VARCHAR(20) DEFAULT NULL,
  `latest_node_status` VARCHAR(60) DEFAULT NULL,
  `in_transit_goods_line_id` BIGINT DEFAULT NULL,
  `fulfillment_balance_id` BIGINT DEFAULT NULL,
  `purchase_order_id` BIGINT DEFAULT NULL,
  `purchase_order_no` VARCHAR(80) DEFAULT NULL,
  `purchase_order_item_id` BIGINT DEFAULT NULL,
  `purchase_order_item_site_id` BIGINT DEFAULT NULL,
  `product_master_id` BIGINT DEFAULT NULL,
  `product_variant_id` BIGINT DEFAULT NULL,
  `partner_sku` VARCHAR(100) DEFAULT NULL,
  `psku_code` VARCHAR(100) DEFAULT NULL,
  `quantity` INT NOT NULL DEFAULT 0,
  `relation_status` VARCHAR(40) NOT NULL DEFAULT 'LINKED',
  `relation_basis` VARCHAR(80) NOT NULL DEFAULT 'ASN_CREATE_SELECTED_BATCH',
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_official_warehouse_asn_shipping_source` (`asn_line_id`, `shipping_batch_source_id`, `is_deleted`),
  UNIQUE KEY `uk_official_warehouse_asn_in_transit_line` (`asn_line_id`, `in_transit_goods_line_id`, `is_deleted`),
  KEY `idx_official_warehouse_asn_shipping_asn` (`asn_id`, `is_deleted`),
  KEY `idx_official_warehouse_asn_shipping_batch` (`shipping_batch_id`, `shipping_batch_source_id`, `is_deleted`),
  KEY `idx_official_warehouse_asn_in_transit_batch` (`in_transit_batch_id`, `in_transit_goods_line_id`, `is_deleted`),
  KEY `idx_official_warehouse_asn_shipping_product` (`owner_user_id`, `store_code`, `site_code`, `product_variant_id`, `is_deleted`),
  KEY `idx_official_warehouse_asn_shipping_purchase` (`purchase_order_id`, `purchase_order_item_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @modify_asn_shipping_batch_id_nullable = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'shipping_batch_id'
      AND is_nullable = 'NO'
  ),
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` MODIFY COLUMN `shipping_batch_id` BIGINT DEFAULT NULL',
  'SELECT ''official_warehouse_asn_shipping_batch_id_nullable'' AS stage'
);
PREPARE modify_asn_shipping_batch_id_nullable_stmt FROM @modify_asn_shipping_batch_id_nullable;
EXECUTE modify_asn_shipping_batch_id_nullable_stmt;
DEALLOCATE PREPARE modify_asn_shipping_batch_id_nullable_stmt;

SET @modify_asn_shipping_batch_no_nullable = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'shipping_batch_no'
      AND is_nullable = 'NO'
  ),
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` MODIFY COLUMN `shipping_batch_no` VARCHAR(80) DEFAULT NULL',
  'SELECT ''official_warehouse_asn_shipping_batch_no_nullable'' AS stage'
);
PREPARE modify_asn_shipping_batch_no_nullable_stmt FROM @modify_asn_shipping_batch_no_nullable;
EXECUTE modify_asn_shipping_batch_no_nullable_stmt;
DEALLOCATE PREPARE modify_asn_shipping_batch_no_nullable_stmt;

SET @modify_asn_shipping_batch_source_nullable = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'shipping_batch_source_id'
      AND is_nullable = 'NO'
  ),
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` MODIFY COLUMN `shipping_batch_source_id` BIGINT DEFAULT NULL',
  'SELECT ''official_warehouse_asn_shipping_batch_source_nullable'' AS stage'
);
PREPARE modify_asn_shipping_batch_source_nullable_stmt FROM @modify_asn_shipping_batch_source_nullable;
EXECUTE modify_asn_shipping_batch_source_nullable_stmt;
DEALLOCATE PREPARE modify_asn_shipping_batch_source_nullable_stmt;

SET @add_asn_in_transit_batch_id = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'in_transit_batch_id'
  ),
  'SELECT ''official_warehouse_asn_in_transit_batch_id_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD COLUMN `in_transit_batch_id` BIGINT DEFAULT NULL AFTER `shipping_batch_source_id`'
);
PREPARE add_asn_in_transit_batch_id_stmt FROM @add_asn_in_transit_batch_id;
EXECUTE add_asn_in_transit_batch_id_stmt;
DEALLOCATE PREPARE add_asn_in_transit_batch_id_stmt;

SET @add_asn_batch_reference_no = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'batch_reference_no'
  ),
  'SELECT ''official_warehouse_asn_batch_reference_no_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD COLUMN `batch_reference_no` VARCHAR(160) DEFAULT NULL AFTER `in_transit_batch_id`'
);
PREPARE add_asn_batch_reference_no_stmt FROM @add_asn_batch_reference_no;
EXECUTE add_asn_batch_reference_no_stmt;
DEALLOCATE PREPARE add_asn_batch_reference_no_stmt;

SET @add_asn_tracking_no = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'tracking_no'
  ),
  'SELECT ''official_warehouse_asn_tracking_no_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD COLUMN `tracking_no` VARCHAR(160) DEFAULT NULL AFTER `batch_reference_no`'
);
PREPARE add_asn_tracking_no_stmt FROM @add_asn_tracking_no;
EXECUTE add_asn_tracking_no_stmt;
DEALLOCATE PREPARE add_asn_tracking_no_stmt;

SET @add_asn_external_shipment_no = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'external_shipment_no'
  ),
  'SELECT ''official_warehouse_asn_external_shipment_no_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD COLUMN `external_shipment_no` VARCHAR(160) DEFAULT NULL AFTER `tracking_no`'
);
PREPARE add_asn_external_shipment_no_stmt FROM @add_asn_external_shipment_no;
EXECUTE add_asn_external_shipment_no_stmt;
DEALLOCATE PREPARE add_asn_external_shipment_no_stmt;

SET @add_asn_forwarder_name = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'forwarder_name'
  ),
  'SELECT ''official_warehouse_asn_forwarder_name_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD COLUMN `forwarder_name` VARCHAR(255) DEFAULT NULL AFTER `external_shipment_no`'
);
PREPARE add_asn_forwarder_name_stmt FROM @add_asn_forwarder_name;
EXECUTE add_asn_forwarder_name_stmt;
DEALLOCATE PREPARE add_asn_forwarder_name_stmt;

SET @add_asn_transport_mode = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'transport_mode'
  ),
  'SELECT ''official_warehouse_asn_transport_mode_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD COLUMN `transport_mode` VARCHAR(20) DEFAULT NULL AFTER `forwarder_name`'
);
PREPARE add_asn_transport_mode_stmt FROM @add_asn_transport_mode;
EXECUTE add_asn_transport_mode_stmt;
DEALLOCATE PREPARE add_asn_transport_mode_stmt;

SET @add_asn_latest_node_status = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'latest_node_status'
  ),
  'SELECT ''official_warehouse_asn_latest_node_status_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD COLUMN `latest_node_status` VARCHAR(60) DEFAULT NULL AFTER `transport_mode`'
);
PREPARE add_asn_latest_node_status_stmt FROM @add_asn_latest_node_status;
EXECUTE add_asn_latest_node_status_stmt;
DEALLOCATE PREPARE add_asn_latest_node_status_stmt;

SET @add_asn_in_transit_goods_line_id = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND column_name = 'in_transit_goods_line_id'
  ),
  'SELECT ''official_warehouse_asn_in_transit_goods_line_id_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD COLUMN `in_transit_goods_line_id` BIGINT DEFAULT NULL AFTER `latest_node_status`'
);
PREPARE add_asn_in_transit_goods_line_id_stmt FROM @add_asn_in_transit_goods_line_id;
EXECUTE add_asn_in_transit_goods_line_id_stmt;
DEALLOCATE PREPARE add_asn_in_transit_goods_line_id_stmt;

SET @add_asn_in_transit_line_unique = IF(
  EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND index_name = 'uk_official_warehouse_asn_in_transit_line'
  ),
  'SELECT ''official_warehouse_asn_in_transit_line_unique_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD UNIQUE KEY `uk_official_warehouse_asn_in_transit_line` (`asn_line_id`, `in_transit_goods_line_id`, `is_deleted`)'
);
PREPARE add_asn_in_transit_line_unique_stmt FROM @add_asn_in_transit_line_unique;
EXECUTE add_asn_in_transit_line_unique_stmt;
DEALLOCATE PREPARE add_asn_in_transit_line_unique_stmt;

SET @add_asn_in_transit_batch_idx = IF(
  EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND index_name = 'idx_official_warehouse_asn_in_transit_batch'
  ),
  'SELECT ''official_warehouse_asn_in_transit_batch_idx_exists'' AS stage',
  'ALTER TABLE `official_warehouse_asn_shipping_batch_link` ADD KEY `idx_official_warehouse_asn_in_transit_batch` (`in_transit_batch_id`, `in_transit_goods_line_id`, `is_deleted`)'
);
PREPARE add_asn_in_transit_batch_idx_stmt FROM @add_asn_in_transit_batch_idx;
EXECUTE add_asn_in_transit_batch_idx_stmt;
DEALLOCATE PREPARE add_asn_in_transit_batch_idx_stmt;

INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)
VALUES ('official_warehouse_asn_shipping_batch_link', 620000, NOW(), NOW())
ON DUPLICATE KEY UPDATE next_id = GREATEST(next_id, VALUES(next_id)), gmt_updated = NOW();
