-- Persistent rolling-window throttle for manual Noon official-warehouse ASN list sync.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `official_warehouse_asn_sync_throttle` (
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `last_started_at` DATETIME NOT NULL,
    `claim_token` VARCHAR(64) NOT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`owner_user_id`, `store_code`, `site_code`),
    KEY `idx_official_warehouse_asn_sync_throttle_time` (`last_started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
