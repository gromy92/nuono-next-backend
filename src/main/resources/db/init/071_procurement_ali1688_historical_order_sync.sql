-- 1688 historical buyer-order sync V1 entry and permissions.
-- Issue 01 adds the page/API permission only; order fact tables are added by later slices.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_authorization` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `provider_code` VARCHAR(60) NOT NULL,
  `provider_account_id` VARCHAR(120) DEFAULT NULL,
  `account_label` VARCHAR(200) DEFAULT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'authorized',
  `scope_summary` VARCHAR(500) DEFAULT NULL,
  `access_token_cipher` TEXT DEFAULT NULL,
  `refresh_token_cipher` TEXT DEFAULT NULL,
  `expires_at` DATETIME DEFAULT NULL,
  `revoked_at` DATETIME DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_proc_ali1688_order_auth_owner_status` (`owner_user_id`, `status`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_authorization', GREATEST(COALESCE(MAX(`id`), 91000), 91000), NOW(), NOW()
FROM `procurement_ali1688_order_authorization`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_store_binding` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `authorization_id` BIGINT NOT NULL,
  `store_code` VARCHAR(120) NOT NULL DEFAULT '*',
  `site_code` VARCHAR(40) NOT NULL DEFAULT '*',
  `status` VARCHAR(30) NOT NULL DEFAULT 'active',
  `priority` INT NOT NULL DEFAULT 100,
  `assignment_mode` VARCHAR(40) NOT NULL DEFAULT 'explicit',
  `remark` VARCHAR(500) DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_ali1688_store_binding_scope` (
    `owner_user_id`, `authorization_id`, `store_code`, `site_code`, `is_deleted`
  ),
  KEY `idx_proc_ali1688_store_binding_lookup` (
    `owner_user_id`, `store_code`, `site_code`, `status`, `is_deleted`, `priority`
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_store_binding', GREATEST(COALESCE(MAX(`id`), 96000), 96000), NOW(), NOW()
FROM `procurement_ali1688_order_store_binding`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

SET @proc_ali1688_order_binding_next_id := (
  SELECT GREATEST(COALESCE(MAX(`id`), 96000), 96000)
  FROM `procurement_ali1688_order_store_binding`
);

INSERT INTO `procurement_ali1688_order_store_binding` (
  `id`, `owner_user_id`, `authorization_id`, `store_code`, `site_code`, `status`,
  `priority`, `assignment_mode`, `remark`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  @proc_ali1688_order_binding_next_id := @proc_ali1688_order_binding_next_id + 1,
  auth.`owner_user_id`,
  auth.`id`,
  '*',
  '*',
  'active',
  100,
  'owner_wide_default',
  '历史授权默认老板级可见；配置具体店铺绑定后按店铺过滤。',
  auth.`created_by`,
  auth.`updated_by`,
  NOW(),
  NOW()
FROM `procurement_ali1688_order_authorization` auth
WHERE auth.`is_deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `procurement_ali1688_order_store_binding` binding
    WHERE binding.`owner_user_id` = auth.`owner_user_id`
      AND binding.`authorization_id` = auth.`id`
      AND binding.`store_code` = '*'
      AND binding.`site_code` = '*'
      AND binding.`is_deleted` = b'0'
  );

UPDATE `product_management_id_sequence`
SET `next_id` = GREATEST(`next_id`, @proc_ali1688_order_binding_next_id),
    `gmt_updated` = NOW()
WHERE `sequence_name` = 'procurement_ali1688_order_store_binding';

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_sync_task` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `authorization_id` BIGINT NOT NULL,
  `task_type` VARCHAR(40) NOT NULL,
  `status` VARCHAR(30) NOT NULL,
  `processed_count` INT NOT NULL DEFAULT 0,
  `imported_count` INT NOT NULL DEFAULT 0,
  `failed_count` INT NOT NULL DEFAULT 0,
  `progress_percent` INT NOT NULL DEFAULT 0,
  `checkpoint_json` TEXT DEFAULT NULL,
  `failure_code` VARCHAR(80) DEFAULT NULL,
  `failure_message` VARCHAR(1000) DEFAULT NULL,
  `retryable` BIT(1) NOT NULL DEFAULT b'1',
  `requires_manual_action` BIT(1) NOT NULL DEFAULT b'0',
  `finished_at` DATETIME DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_proc_ali1688_order_task_owner` (`owner_user_id`, `authorization_id`, `status`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_header` (
  `id` BIGINT NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `authorization_id` BIGINT NOT NULL,
  `order_natural_key` VARCHAR(220) NOT NULL,
  `provider_order_no` VARCHAR(120) NOT NULL,
  `order_time` DATETIME DEFAULT NULL,
  `paid_at` VARCHAR(40) DEFAULT NULL,
  `buyer_company_name` VARCHAR(300) DEFAULT NULL,
  `buyer_member_name` VARCHAR(160) DEFAULT NULL,
  `supplier_name` VARCHAR(300) DEFAULT NULL,
  `seller_member_name` VARCHAR(160) DEFAULT NULL,
  `goods_total_text` VARCHAR(80) DEFAULT NULL,
  `freight_text` VARCHAR(80) DEFAULT NULL,
  `adjustment_text` VARCHAR(80) DEFAULT NULL,
  `paid_amount_text` VARCHAR(80) DEFAULT NULL,
  `amount_text` VARCHAR(80) DEFAULT NULL,
  `amount_value` DECIMAL(18, 4) DEFAULT NULL,
  `currency` VARCHAR(20) DEFAULT NULL,
  `order_status` VARCHAR(80) DEFAULT NULL,
  `logistics_status` VARCHAR(80) DEFAULT NULL,
  `shipper_name` VARCHAR(160) DEFAULT NULL,
  `original_url` VARCHAR(800) DEFAULT NULL,
  `receiver_name` VARCHAR(120) DEFAULT NULL,
  `receiver_postal_code` VARCHAR(40) DEFAULT NULL,
  `receiver_telephone` VARCHAR(120) DEFAULT NULL,
  `receiver_mobile` VARCHAR(120) DEFAULT NULL,
  `receiver_phone` VARCHAR(120) DEFAULT NULL,
  `receiver_address` VARCHAR(1000) DEFAULT NULL,
  `buyer_remark` VARCHAR(1000) DEFAULT NULL,
  `supplier_contact` VARCHAR(500) DEFAULT NULL,
  `initiator_login_name` VARCHAR(160) DEFAULT NULL,
  `source_batch_no` VARCHAR(120) DEFAULT NULL,
  `downstream_order_no` VARCHAR(120) DEFAULT NULL,
  `raw_snapshot_json` LONGTEXT DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_ali1688_order_natural_key` (`order_natural_key`),
  KEY `idx_proc_ali1688_order_owner` (`owner_user_id`, `authorization_id`, `is_deleted`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_item` (
  `id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL,
  `item_natural_key` VARCHAR(360) NOT NULL,
  `offer_id` VARCHAR(80) DEFAULT NULL,
  `sku_id` VARCHAR(120) DEFAULT NULL,
  `title` VARCHAR(500) DEFAULT NULL,
  `sku_text` VARCHAR(300) DEFAULT NULL,
  `model_text` VARCHAR(300) DEFAULT NULL,
  `product_code` VARCHAR(160) DEFAULT NULL,
  `single_product_code` VARCHAR(160) DEFAULT NULL,
  `quantity` INT DEFAULT NULL,
  `unit` VARCHAR(60) DEFAULT NULL,
  `unit_price_text` VARCHAR(80) DEFAULT NULL,
  `amount_text` VARCHAR(80) DEFAULT NULL,
  `image_url` VARCHAR(800) DEFAULT NULL,
  `raw_snapshot_json` LONGTEXT DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_ali1688_order_item_key` (`item_natural_key`),
  KEY `idx_proc_ali1688_order_item_order` (`order_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_logistics` (
  `id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL,
  `item_id` BIGINT DEFAULT NULL,
  `logistics_natural_key` VARCHAR(420) NOT NULL,
  `logistics_company` VARCHAR(200) DEFAULT NULL,
  `tracking_no` VARCHAR(160) DEFAULT NULL,
  `raw_snapshot_json` LONGTEXT DEFAULT NULL,
  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_ali1688_order_logistics_key` (`logistics_natural_key`),
  KEY `idx_proc_ali1688_order_logistics_order` (`order_id`, `item_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @proc_ali1688_order_add_paid_at := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'paid_at'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `paid_at` VARCHAR(40) DEFAULT NULL AFTER `order_time`')
);
PREPARE stmt FROM @proc_ali1688_order_add_paid_at; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_buyer_company := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'buyer_company_name'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `buyer_company_name` VARCHAR(300) DEFAULT NULL AFTER `paid_at`')
);
PREPARE stmt FROM @proc_ali1688_order_add_buyer_company; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_buyer_member := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'buyer_member_name'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `buyer_member_name` VARCHAR(160) DEFAULT NULL AFTER `buyer_company_name`')
);
PREPARE stmt FROM @proc_ali1688_order_add_buyer_member; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_seller_member := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'seller_member_name'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `seller_member_name` VARCHAR(160) DEFAULT NULL AFTER `supplier_name`')
);
PREPARE stmt FROM @proc_ali1688_order_add_seller_member; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_goods_total := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'goods_total_text'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `goods_total_text` VARCHAR(80) DEFAULT NULL AFTER `seller_member_name`')
);
PREPARE stmt FROM @proc_ali1688_order_add_goods_total; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_freight := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'freight_text'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `freight_text` VARCHAR(80) DEFAULT NULL AFTER `goods_total_text`')
);
PREPARE stmt FROM @proc_ali1688_order_add_freight; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_adjustment := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'adjustment_text'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `adjustment_text` VARCHAR(80) DEFAULT NULL AFTER `freight_text`')
);
PREPARE stmt FROM @proc_ali1688_order_add_adjustment; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_paid_amount := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'paid_amount_text'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `paid_amount_text` VARCHAR(80) DEFAULT NULL AFTER `adjustment_text`')
);
PREPARE stmt FROM @proc_ali1688_order_add_paid_amount; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_shipper := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'shipper_name'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `shipper_name` VARCHAR(160) DEFAULT NULL AFTER `logistics_status`')
);
PREPARE stmt FROM @proc_ali1688_order_add_shipper; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_receiver_name := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'receiver_name'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `receiver_name` VARCHAR(120) DEFAULT NULL AFTER `original_url`')
);
PREPARE stmt FROM @proc_ali1688_order_add_receiver_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_receiver_postal := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'receiver_postal_code'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `receiver_postal_code` VARCHAR(40) DEFAULT NULL AFTER `receiver_name`')
);
PREPARE stmt FROM @proc_ali1688_order_add_receiver_postal; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_receiver_telephone := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'receiver_telephone'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `receiver_telephone` VARCHAR(120) DEFAULT NULL AFTER `receiver_postal_code`')
);
PREPARE stmt FROM @proc_ali1688_order_add_receiver_telephone; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_receiver_mobile := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'receiver_mobile'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `receiver_mobile` VARCHAR(120) DEFAULT NULL AFTER `receiver_telephone`')
);
PREPARE stmt FROM @proc_ali1688_order_add_receiver_mobile; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_initiator := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'initiator_login_name'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `initiator_login_name` VARCHAR(160) DEFAULT NULL AFTER `supplier_contact`')
);
PREPARE stmt FROM @proc_ali1688_order_add_initiator; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_source_batch := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'source_batch_no'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `source_batch_no` VARCHAR(120) DEFAULT NULL AFTER `initiator_login_name`')
);
PREPARE stmt FROM @proc_ali1688_order_add_source_batch; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_downstream := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'downstream_order_no'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `downstream_order_no` VARCHAR(120) DEFAULT NULL AFTER `source_batch_no`')
);
PREPARE stmt FROM @proc_ali1688_order_add_downstream; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_order_add_raw_snapshot := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_header' AND COLUMN_NAME = 'raw_snapshot_json'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `raw_snapshot_json` LONGTEXT DEFAULT NULL AFTER `downstream_order_no`')
);
PREPARE stmt FROM @proc_ali1688_order_add_raw_snapshot; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_item_add_sku_id := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_item' AND COLUMN_NAME = 'sku_id'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item` ADD COLUMN `sku_id` VARCHAR(120) DEFAULT NULL AFTER `offer_id`')
);
PREPARE stmt FROM @proc_ali1688_item_add_sku_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_item_add_model := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_item' AND COLUMN_NAME = 'model_text'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item` ADD COLUMN `model_text` VARCHAR(300) DEFAULT NULL AFTER `sku_text`')
);
PREPARE stmt FROM @proc_ali1688_item_add_model; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_item_add_product_code := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_item' AND COLUMN_NAME = 'product_code'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item` ADD COLUMN `product_code` VARCHAR(160) DEFAULT NULL AFTER `model_text`')
);
PREPARE stmt FROM @proc_ali1688_item_add_product_code; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_item_add_single_code := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_item' AND COLUMN_NAME = 'single_product_code'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item` ADD COLUMN `single_product_code` VARCHAR(160) DEFAULT NULL AFTER `product_code`')
);
PREPARE stmt FROM @proc_ali1688_item_add_single_code; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_item_add_unit := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_item' AND COLUMN_NAME = 'unit'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item` ADD COLUMN `unit` VARCHAR(60) DEFAULT NULL AFTER `quantity`')
);
PREPARE stmt FROM @proc_ali1688_item_add_unit; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @proc_ali1688_item_add_raw_snapshot := (
  SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'procurement_ali1688_order_item' AND COLUMN_NAME = 'raw_snapshot_json'),
    'SELECT 1',
    'ALTER TABLE `procurement_ali1688_order_item` ADD COLUMN `raw_snapshot_json` LONGTEXT DEFAULT NULL AFTER `image_url`')
);
PREPARE stmt FROM @proc_ali1688_item_add_raw_snapshot; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_sync_task', GREATEST(COALESCE(MAX(`id`), 92000), 92000), NOW(), NOW()
FROM `procurement_ali1688_order_sync_task`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_header', GREATEST(COALESCE(MAX(`id`), 93000), 93000), NOW(), NOW()
FROM `procurement_ali1688_order_header`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_item', GREATEST(COALESCE(MAX(`id`), 94000), 94000), NOW(), NOW()
FROM `procurement_ali1688_order_item`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'procurement_ali1688_order_logistics', GREATEST(COALESCE(MAX(`id`), 95000), 95000), NOW(), NOW()
FROM `procurement_ali1688_order_logistics`
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`, `gmt_create`, `gmt_updated`)
VALUES
  (9401, '1688 历史订单', 2, '/purchase/ali1688-orders', b'0', NOW(), NOW()),
  (9402, 'SKU 采购历史', 2, '/purchase/ali1688-sku-purchase-history', b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `parent_id` = VALUES(`parent_id`),
  `url_path` = VALUES(`url_path`),
  `is_deleted` = b'0',
  `gmt_updated` = NOW();

SET @ali1688_order_menu_id = (
  SELECT id
  FROM `menu`
  WHERE `url_path` = '/purchase/ali1688-orders'
    AND `is_deleted` = b'0'
  ORDER BY id ASC
  LIMIT 1
);
SET @ali1688_sku_history_menu_id = (
  SELECT id
  FROM `menu`
  WHERE `url_path` = '/purchase/ali1688-sku-purchase-history'
    AND `is_deleted` = b'0'
  ORDER BY id ASC
  LIMIT 1
);

-- Local acceptance personas for historical-order read-only verification.
-- Guarded to the local development database so production migrations only grant real existing users.
INSERT INTO `user` (
  `id`, `phone`, `email`, `account_no`, `password`, `token`, `role`, `role_id`, `account_type`,
  `real_name`, `company_name`, `level`, `status`, `is_deleted`, `created_by`, `updated_by`,
  `gmt_create`, `gmt_updated`
)
SELECT
  seed.`id`,
  NULL,
  NULL,
  seed.`account_no`,
  seed.`password`,
  NULL,
  seed.`role_name`,
  seed.`role_id`,
  'internal',
  seed.`real_name`,
  'canman',
  seed.`level`,
  1,
  0,
  307,
  307,
  NOW(),
  NOW()
FROM (
  SELECT 90005 AS `id`, 'operations.manager.demo' AS `account_no`, 'opsmanager123' AS `password`, '运营主管' AS `role_name`, 3 AS `role_id`, '运营主管演示账号' AS `real_name`, 2 AS `level`
  UNION ALL SELECT 90003, 'operation.demo', 'operation123', '运营', 4, '运营演示账号', 3
  UNION ALL SELECT 90001, 'procurement.demo', 'procurement123', '采购', 5, '采购演示账号', 3
  UNION ALL SELECT 90004, 'warehouse.demo', 'warehouse123', '仓管', 6, '仓管演示账号', 3
) seed
JOIN `role` r ON r.`id` = seed.`role_id`
  AND r.`is_deleted` = 0
WHERE DATABASE() = 'nuono_new_dev'
ON DUPLICATE KEY UPDATE
  `account_no` = VALUES(`account_no`),
  `role` = VALUES(`role`),
  `role_id` = VALUES(`role_id`),
  `account_type` = VALUES(`account_type`),
  `real_name` = VALUES(`real_name`),
  `company_name` = VALUES(`company_name`),
  `level` = VALUES(`level`),
  `status` = VALUES(`status`),
  `is_deleted` = VALUES(`is_deleted`),
  `updated_by` = VALUES(`updated_by`),
  `gmt_updated` = NOW();

INSERT INTO `user_project_access` (
  `user_id`, `project_id`, `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  member.`id`,
  up.`id`,
  0,
  307,
  307,
  NOW(),
  NOW()
FROM `user` member
JOIN `user_project` up ON up.`user_id` = 307
  AND up.`project_code` IN ('PRJ108065', 'PRJ245027', 'PRJ244978', 'PRJ69486')
  AND up.`is_deleted` = 0
WHERE DATABASE() = 'nuono_new_dev'
  AND member.`account_no` IN ('operations.manager.demo', 'operation.demo', 'procurement.demo', 'warehouse.demo')
  AND member.`is_deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `user_project_access` existing
    WHERE existing.`user_id` = member.`id`
      AND existing.`project_id` = up.`id`
      AND existing.`is_deleted` = 0
  );

SET @next_role_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `role_menu`);

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`, `gmt_create`, `gmt_updated`)
SELECT
  @next_role_menu_id := @next_role_menu_id + 1,
  r.`id`,
  menu_scope.`menu_id`,
  b'0',
  NOW(),
  NOW()
FROM `role` r
JOIN (
  SELECT @ali1688_order_menu_id AS `menu_id`
  UNION ALL SELECT @ali1688_sku_history_menu_id
) menu_scope ON menu_scope.`menu_id` IS NOT NULL
WHERE r.`id` IN (2, 3, 4, 5, 6)
  AND r.`is_deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `role_menu` existing
    WHERE existing.`role_id` = r.`id`
      AND existing.`menu_id` = menu_scope.`menu_id`
  );

UPDATE `role_menu` rm
JOIN (
  SELECT
    existing.`role_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = b'0' THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `role_menu` existing
  WHERE existing.`role_id` IN (2, 3, 4, 5, 6)
    AND existing.`menu_id` IN (@ali1688_order_menu_id, @ali1688_sku_history_menu_id)
  GROUP BY existing.`role_id`, existing.`menu_id`
) keep_row ON keep_row.`id` = rm.`id`
SET rm.`is_deleted` = b'0',
    rm.`gmt_updated` = NOW();

UPDATE `role_menu` duplicate
JOIN `role_menu` keep_row
  ON keep_row.`role_id` = duplicate.`role_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = b'0'
  AND duplicate.`is_deleted` = b'0'
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = b'1',
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` IN (@ali1688_order_menu_id, @ali1688_sku_history_menu_id);

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `role_id` NOT IN (2, 3, 4, 5, 6)
  AND `menu_id` IN (@ali1688_order_menu_id, @ali1688_sku_history_menu_id)
  AND `is_deleted` = b'0';

SET @next_user_menu_id = (SELECT COALESCE(MAX(`id`), 0) FROM `user_menu`);

INSERT INTO `user_menu` (
  `id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`,
  `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
  @next_user_menu_id := @next_user_menu_id + 1,
  u.`id`,
  rm.`menu_id`,
  1,
  NOW(),
  COALESCE(u.`expired_time`, '2099-12-31 23:59:59'),
  b'0',
  COALESCE(u.`updated_by`, u.`created_by`, 1),
  COALESCE(u.`updated_by`, u.`created_by`, 1),
  NOW(),
  NOW()
FROM `user` u
JOIN `role_menu` rm ON rm.`role_id` = u.`role_id`
  AND rm.`menu_id` IN (@ali1688_order_menu_id, @ali1688_sku_history_menu_id)
  AND rm.`is_deleted` = b'0'
WHERE u.`is_deleted` = 0
  AND u.`status` = 1
  AND COALESCE(u.`account_type`, '') = 'internal'
  AND NOT EXISTS (
    SELECT 1
    FROM `user_menu` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`menu_id` = rm.`menu_id`
  );

UPDATE `user_menu` um
JOIN (
  SELECT
    existing.`user_id`,
    COALESCE(MIN(CASE WHEN existing.`is_deleted` = 0 THEN existing.`id` END), MIN(existing.`id`)) AS `id`
  FROM `user_menu` existing
  JOIN `user` u ON u.`id` = existing.`user_id`
  WHERE existing.`menu_id` IN (@ali1688_order_menu_id, @ali1688_sku_history_menu_id)
    AND u.`role_id` IN (2, 3, 4, 5, 6)
    AND u.`is_deleted` = 0
    AND u.`status` = 1
    AND COALESCE(u.`account_type`, '') = 'internal'
  GROUP BY existing.`user_id`, existing.`menu_id`
) keep_row ON keep_row.`id` = um.`id`
SET um.`status` = 1,
    um.`is_deleted` = b'0',
    um.`gmt_updated` = NOW();

UPDATE `user_menu` duplicate
JOIN `user_menu` keep_row
  ON keep_row.`user_id` = duplicate.`user_id`
  AND keep_row.`menu_id` = duplicate.`menu_id`
  AND keep_row.`is_deleted` = 0
  AND duplicate.`is_deleted` = 0
  AND keep_row.`id` < duplicate.`id`
SET duplicate.`is_deleted` = 1,
    duplicate.`gmt_updated` = NOW()
WHERE duplicate.`menu_id` IN (@ali1688_order_menu_id, @ali1688_sku_history_menu_id);

UPDATE `user_menu` um
JOIN `user` u ON u.`id` = um.`user_id`
SET um.`is_deleted` = b'1',
    um.`gmt_updated` = NOW()
WHERE u.`role_id` NOT IN (2, 3, 4, 5, 6)
  AND um.`menu_id` IN (@ali1688_order_menu_id, @ali1688_sku_history_menu_id)
  AND um.`is_deleted` = b'0';
