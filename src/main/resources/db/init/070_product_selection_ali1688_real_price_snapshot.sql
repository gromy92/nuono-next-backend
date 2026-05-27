-- Product selection 1688 real price snapshot contract.
-- Snapshots are preview-only and separate from list-page price hints.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `product_selection_ali1688_real_price_snapshot` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `source_collection_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT DEFAULT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `status` VARCHAR(30) NOT NULL,
    `safety_mode` VARCHAR(40) NOT NULL DEFAULT 'preview_only',
    `side_effect_policy` VARCHAR(80) NOT NULL DEFAULT 'no_payment_no_order_no_message',
    `source` VARCHAR(40) NOT NULL,
    `sku_text` VARCHAR(500) DEFAULT NULL,
    `quantity` INT NOT NULL DEFAULT 1,
    `unit_price` DECIMAL(18,4) DEFAULT NULL,
    `freight_price` DECIMAL(18,4) DEFAULT NULL,
    `discount_price` DECIMAL(18,4) DEFAULT NULL,
    `total_price` DECIMAL(18,4) DEFAULT NULL,
    `currency` VARCHAR(20) NOT NULL DEFAULT 'CNY',
    `rmb_total_price` DECIMAL(18,4) DEFAULT NULL,
    `exchange_rate_to_rmb` DECIMAL(18,8) DEFAULT NULL,
    `region_text` VARCHAR(200) DEFAULT NULL,
    `address_context_json` TEXT DEFAULT NULL,
    `captured_at` DATETIME NOT NULL,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(500) DEFAULT NULL,
    `raw_snapshot_json` LONGTEXT DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_selection_ali1688_real_price_candidate` (`candidate_id`, `captured_at`, `is_deleted`),
    KEY `idx_product_selection_ali1688_real_price_task` (`task_id`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_selection_ali1688_real_price_snapshot', GREATEST(COALESCE(MAX(`id`), 91000), 91000), NOW(), NOW()
FROM `product_selection_ali1688_real_price_snapshot`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
