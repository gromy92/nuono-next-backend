-- 1688 historical order Excel upload preview batches.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_excel_import_batch` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `authorization_id` BIGINT NOT NULL,
  `store_code` VARCHAR(120) NOT NULL,
  `site_code` VARCHAR(40) NOT NULL DEFAULT '*',
  `file_name` VARCHAR(300) DEFAULT NULL,
  `file_size` BIGINT DEFAULT NULL,
  `file_hash` VARCHAR(80) DEFAULT NULL,
  `status` VARCHAR(40) NOT NULL,
  `header_version` VARCHAR(80) DEFAULT NULL,
  `order_header_row_count` INT NOT NULL DEFAULT 0,
  `product_line_count` INT NOT NULL DEFAULT 0,
  `logistics_line_count` INT NOT NULL DEFAULT 0,
  `valid_row_count` INT NOT NULL DEFAULT 0,
  `duplicate_candidate_count` INT NOT NULL DEFAULT 0,
  `error_count` INT NOT NULL DEFAULT 0,
  `warning_count` INT NOT NULL DEFAULT 0,
  `failure_code` VARCHAR(80) DEFAULT NULL,
  `failure_message` VARCHAR(1000) DEFAULT NULL,
  `error_summary_json` LONGTEXT DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_proc_ali1688_excel_batch_scope` (
    `owner_user_id`, `authorization_id`, `store_code`, `site_code`, `status`, `gmt_updated`
  ),
  KEY `idx_proc_ali1688_excel_batch_file_hash` (`owner_user_id`, `authorization_id`, `file_hash`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_excel_import_batch', GREATEST(COALESCE(MAX(`id`), 97000), 97000), NOW(), NOW()
FROM `procurement_ali1688_order_excel_import_batch`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_excel_import_row` (
  `id` BIGINT NOT NULL,
  `batch_id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `authorization_id` BIGINT NOT NULL,
  `row_number` INT NOT NULL,
  `continuation_row` BIT(1) NOT NULL DEFAULT b'0',
  `order_no` VARCHAR(120) NOT NULL,
  `buyer_company_name` VARCHAR(300) DEFAULT NULL,
  `buyer_member_name` VARCHAR(160) DEFAULT NULL,
  `supplier_name` VARCHAR(300) DEFAULT NULL,
  `seller_member_name` VARCHAR(160) DEFAULT NULL,
  `goods_total_text` VARCHAR(80) DEFAULT NULL,
  `freight_text` VARCHAR(80) DEFAULT NULL,
  `adjustment_text` VARCHAR(80) DEFAULT NULL,
  `paid_amount_text` VARCHAR(80) DEFAULT NULL,
  `order_status` VARCHAR(80) DEFAULT NULL,
  `order_time` DATETIME DEFAULT NULL,
  `paid_at` VARCHAR(40) DEFAULT NULL,
  `shipper_name` VARCHAR(160) DEFAULT NULL,
  `receiver_name` VARCHAR(120) DEFAULT NULL,
  `receiver_postal_code` VARCHAR(40) DEFAULT NULL,
  `receiver_telephone` VARCHAR(120) DEFAULT NULL,
  `receiver_mobile` VARCHAR(120) DEFAULT NULL,
  `receiver_address` VARCHAR(1000) DEFAULT NULL,
  `buyer_remark` VARCHAR(1000) DEFAULT NULL,
  `title` VARCHAR(500) DEFAULT NULL,
  `offer_id` VARCHAR(80) DEFAULT NULL,
  `sku_id` VARCHAR(120) DEFAULT NULL,
  `product_code` VARCHAR(160) DEFAULT NULL,
  `model_text` VARCHAR(300) DEFAULT NULL,
  `single_product_code` VARCHAR(160) DEFAULT NULL,
  `quantity_text` VARCHAR(80) DEFAULT NULL,
  `unit` VARCHAR(60) DEFAULT NULL,
  `unit_price_text` VARCHAR(80) DEFAULT NULL,
  `logistics_company` VARCHAR(200) DEFAULT NULL,
  `tracking_no` VARCHAR(160) DEFAULT NULL,
  `source_batch_no` VARCHAR(120) DEFAULT NULL,
  `downstream_channel` VARCHAR(80) DEFAULT NULL,
  `downstream_order_no` VARCHAR(120) DEFAULT NULL,
  `initiator_login_name` VARCHAR(160) DEFAULT NULL,
  `raw_snapshot_json` LONGTEXT DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_proc_ali1688_excel_row_batch` (`owner_user_id`, `batch_id`, `row_number`, `is_deleted`),
  KEY `idx_proc_ali1688_excel_row_order` (`owner_user_id`, `authorization_id`, `order_no`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_excel_import_row', GREATEST(COALESCE(MAX(`id`), 98000), 98000), NOW(), NOW()
FROM `procurement_ali1688_order_excel_import_row`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();
