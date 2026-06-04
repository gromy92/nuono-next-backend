-- Renamed from 080_noon_finance_transaction_fact.sql to keep migration prefixes unique.
CREATE TABLE IF NOT EXISTS `noon_finance_transaction_id_sequence` (
  `sequence_name` VARCHAR(80) NOT NULL,
  `next_id` BIGINT NOT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `noon_finance_transaction_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES ('finance_transaction_fact', 300000, NOW(), NOW())
ON DUPLICATE KEY UPDATE `next_id` = `next_id`, `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `noon_finance_transaction_fact` (
  `id` BIGINT NOT NULL,
  `source_system` VARCHAR(80) NOT NULL,
  `source_batch_id` VARCHAR(160) DEFAULT NULL,
  `file_digest_sha256` VARCHAR(128) DEFAULT NULL,
  `row_hash` VARCHAR(128) NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `store_code` VARCHAR(80) NOT NULL,
  `site_code` VARCHAR(20) NOT NULL,
  `contract_code` VARCHAR(80) DEFAULT NULL,
  `contract_title` VARCHAR(160) DEFAULT NULL,
  `reference_nr` VARCHAR(160) NOT NULL,
  `order_nr` VARCHAR(160) NOT NULL,
  `item_nr` VARCHAR(160) DEFAULT NULL,
  `order_date` DATE DEFAULT NULL,
  `transaction_date` DATE NOT NULL,
  `title` VARCHAR(1024) DEFAULT NULL,
  `sku` VARCHAR(160) DEFAULT NULL,
  `partner_sku` VARCHAR(160) DEFAULT NULL,
  `transaction_type` VARCHAR(80) NOT NULL,
  `currency` VARCHAR(20) NOT NULL,
  `net_proceeds` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `referral_fee_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `fulfillment_logistics_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `shipping_credits_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `other_order_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `order_subsidies_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `non_order_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `non_order_subsidies_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `others_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `total_amount` DECIMAL(18,6) NOT NULL DEFAULT 0,
  `report_date_from` DATE NOT NULL,
  `report_date_to` DATE NOT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_noon_finance_transaction_fact_natural` (
    `source_system`, `owner_user_id`, `store_code`, `site_code`, `row_hash`
  ),
  KEY `idx_noon_finance_scope_transaction_date` (`owner_user_id`, `store_code`, `site_code`, `transaction_date`),
  KEY `idx_noon_finance_sku` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `sku`),
  KEY `idx_noon_finance_order` (`owner_user_id`, `store_code`, `site_code`, `order_nr`),
  KEY `idx_noon_finance_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELETE victim
FROM `noon_finance_transaction_fact` victim
JOIN `noon_finance_transaction_fact` keeper
  ON keeper.`source_system` = victim.`source_system`
  AND keeper.`owner_user_id` = victim.`owner_user_id`
  AND keeper.`store_code` = victim.`store_code`
  AND keeper.`site_code` = victim.`site_code`
  AND keeper.`row_hash` = victim.`row_hash`
  AND (
    keeper.`gmt_updated` > victim.`gmt_updated`
    OR (keeper.`gmt_updated` = victim.`gmt_updated` AND keeper.`id` > victim.`id`)
  );

SET @noon_finance_natural_key_column_count := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'noon_finance_transaction_fact'
    AND INDEX_NAME = 'uk_noon_finance_transaction_fact_natural'
);

SET @noon_finance_natural_key_expected_column_count := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'noon_finance_transaction_fact'
    AND INDEX_NAME = 'uk_noon_finance_transaction_fact_natural'
    AND COLUMN_NAME IN ('source_system', 'owner_user_id', 'store_code', 'site_code', 'row_hash')
);

SET @noon_finance_drop_old_natural_key_sql := IF(
  @noon_finance_natural_key_column_count > 0 AND (
    @noon_finance_natural_key_column_count != 5
    OR @noon_finance_natural_key_expected_column_count != 5
  ),
  'ALTER TABLE `noon_finance_transaction_fact` DROP INDEX `uk_noon_finance_transaction_fact_natural`',
  'SELECT 1'
);
PREPARE noon_finance_drop_old_natural_key_stmt FROM @noon_finance_drop_old_natural_key_sql;
EXECUTE noon_finance_drop_old_natural_key_stmt;
DEALLOCATE PREPARE noon_finance_drop_old_natural_key_stmt;

SET @noon_finance_natural_key_exists := (
  SELECT COUNT(DISTINCT INDEX_NAME)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'noon_finance_transaction_fact'
    AND INDEX_NAME = 'uk_noon_finance_transaction_fact_natural'
);

SET @noon_finance_add_natural_key_sql := IF(
  @noon_finance_natural_key_exists = 0,
  'ALTER TABLE `noon_finance_transaction_fact` ADD UNIQUE KEY `uk_noon_finance_transaction_fact_natural` (`source_system`, `owner_user_id`, `store_code`, `site_code`, `row_hash`)',
  'SELECT 1'
);
PREPARE noon_finance_add_natural_key_stmt FROM @noon_finance_add_natural_key_sql;
EXECUTE noon_finance_add_natural_key_stmt;
DEALLOCATE PREPARE noon_finance_add_natural_key_stmt;
