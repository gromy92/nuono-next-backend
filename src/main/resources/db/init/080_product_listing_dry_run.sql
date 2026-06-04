CREATE TABLE IF NOT EXISTS `product_listing_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_listing_id_sequence` (`sequence_name`, `next_id`)
VALUES ('product_listing_draft', 10000),
       ('product_listing_task', 10000)
ON DUPLICATE KEY UPDATE `next_id` = `next_id`;

CREATE TABLE IF NOT EXISTS `product_listing_draft` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `draft_no` VARCHAR(80) NOT NULL,
    `source_type` VARCHAR(40) DEFAULT NULL,
    `source_ref_id` BIGINT DEFAULT NULL,
    `optional_purchase_order_id` BIGINT DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'draft',
    `draft_json` LONGTEXT NOT NULL,
    `validation_json` LONGTEXT DEFAULT NULL,
    `created_by` BIGINT NOT NULL,
    `updated_by` BIGINT NOT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_listing_draft_no` (`draft_no`),
    KEY `idx_product_listing_draft_owner_store` (`owner_user_id`, `store_code`, `gmt_updated`),
    KEY `idx_product_listing_draft_source` (`source_type`, `source_ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_listing_task` (
    `id` BIGINT NOT NULL,
    `draft_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `task_no` VARCHAR(80) NOT NULL,
    `mode` VARCHAR(30) NOT NULL,
    `status` VARCHAR(40) NOT NULL,
    `source_task_id` BIGINT DEFAULT NULL,
    `input_snapshot_json` LONGTEXT NOT NULL,
    `validation_json` LONGTEXT DEFAULT NULL,
    `confirmation_json` LONGTEXT DEFAULT NULL,
    `noon_result_json` LONGTEXT DEFAULT NULL,
    `failure_category` VARCHAR(80) DEFAULT NULL,
    `failure_code` VARCHAR(100) DEFAULT NULL,
    `failure_message` VARCHAR(500) DEFAULT NULL,
    `submitted_by` BIGINT NOT NULL,
    `submitted_at` DATETIME NOT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `completed_at` DATETIME DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_listing_task_no` (`task_no`),
    KEY `idx_product_listing_task_draft` (`draft_id`),
    KEY `idx_product_listing_task_owner_store` (`owner_user_id`, `store_code`, `submitted_at`),
    KEY `idx_product_listing_task_source` (`owner_user_id`, `source_task_id`, `mode`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
