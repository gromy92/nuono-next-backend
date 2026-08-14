-- Immutable evidence for product identity preflight failures before an ASN exists.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `official_warehouse_asn_preflight_audit` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `operator_user_id` BIGINT DEFAULT NULL,
    `logical_store_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_id` VARCHAR(80) NOT NULL,
    `attempt_asn_id` BIGINT NOT NULL,
    `attempt_ref` VARCHAR(120) NOT NULL,
    `operation` VARCHAR(80) NOT NULL,
    `request_line_count` INT NOT NULL,
    `invalid_line_count` INT NOT NULL,
    `failure_code` VARCHAR(120) NOT NULL,
    `failure_message` VARCHAR(1000) NOT NULL,
    `reason_summary` VARCHAR(1000) DEFAULT NULL,
    `invalid_lines_json` LONGTEXT NOT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_official_warehouse_asn_preflight_audit_scope`
        (`owner_user_id`, `store_code`, `site_code`, `gmt_create`),
    KEY `idx_official_warehouse_asn_preflight_audit_attempt` (`attempt_asn_id`, `attempt_ref`),
    KEY `idx_official_warehouse_asn_preflight_audit_failure` (`failure_code`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES ('official_warehouse_asn_preflight_audit', 630000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();
