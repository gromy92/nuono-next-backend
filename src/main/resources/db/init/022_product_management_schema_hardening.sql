CREATE TABLE IF NOT EXISTS `product_management_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'logical_store', GREATEST(COALESCE(MAX(`id`), 50000), 50000), NOW(), NOW() FROM `logical_store`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'logical_store_site', GREATEST(COALESCE(MAX(`id`), 51000), 51000), NOW(), NOW() FROM `logical_store_site`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'store_initialization_snapshot', GREATEST(COALESCE(MAX(`id`), 40000), 40000), NOW(), NOW() FROM `store_initialization_snapshot`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_master', GREATEST(COALESCE(MAX(`id`), 52000), 52000), NOW(), NOW() FROM `product_master`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_variant', GREATEST(COALESCE(MAX(`id`), 53000), 53000), NOW(), NOW() FROM `product_variant`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_barcode', GREATEST(COALESCE(MAX(`id`), 54000), 54000), NOW(), NOW() FROM `product_barcode`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_site_offer', GREATEST(COALESCE(MAX(`id`), 55000), 55000), NOW(), NOW() FROM `product_site_offer`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_master_snapshot', GREATEST(COALESCE(MAX(`id`), 56000), 56000), NOW(), NOW() FROM `product_master_snapshot`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_master_draft', GREATEST(COALESCE(MAX(`id`), 57000), 57000), NOW(), NOW() FROM `product_master_draft`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_action_log', GREATEST(COALESCE(MAX(`id`), 58000), 58000), NOW(), NOW() FROM `product_action_log`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_key_content_history', GREATEST(COALESCE(MAX(`id`), 59000), 59000), NOW(), NOW() FROM `product_key_content_history`
ON DUPLICATE KEY UPDATE `next_id` = GREATEST(`next_id`, VALUES(`next_id`)), `gmt_updated` = NOW();

SET @drop_product_variant_partner_sku := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant'
              AND INDEX_NAME = 'uk_product_variant_partner_sku'
        ),
        'ALTER TABLE `product_variant` DROP INDEX `uk_product_variant_partner_sku`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_product_variant_partner_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_variant_master_partner_sku := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant'
              AND INDEX_NAME = 'uk_product_variant_master_partner_sku'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant` ADD UNIQUE KEY `uk_product_variant_master_partner_sku` (`product_master_id`, `partner_sku`)'
    )
);
PREPARE stmt FROM @add_product_variant_master_partner_sku;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_master_snapshot_hash_lookup := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master_snapshot'
              AND INDEX_NAME = 'idx_product_master_snapshot_hash'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master_snapshot` ADD KEY `idx_product_master_snapshot_hash` (`product_master_id`, `snapshot_type`, `snapshot_hash`)'
    )
);
PREPARE stmt FROM @add_product_master_snapshot_hash_lookup;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
