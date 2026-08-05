-- Migration 246: bounded DP06 advertising generation, facts, and current head.
-- Expected successor after 243/244/245. Exact post/live checks must reject same-name drift.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `dp_pull_advertising_generation` (
    `task_id` BIGINT NOT NULL, `active_fence_epoch` BIGINT NOT NULL, `sealed_fence_epoch` BIGINT DEFAULT NULL,
    `state` VARCHAR(16) NOT NULL DEFAULT 'PREPARING', `owner_user_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) NOT NULL, `store_code` VARCHAR(100) NOT NULL, `site_code` VARCHAR(32) NOT NULL,
    `report_date` DATE NOT NULL, `schedule_slot` DATETIME(3) NOT NULL, `business_window_key` VARCHAR(160) NOT NULL,
    `authority_token_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `active_campaign_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `provider_as_of_utc` DATETIME(3) NOT NULL, `declared_campaign_count` BIGINT NOT NULL,
    `active_campaign_count` INT NOT NULL, `last_page` INT NOT NULL, `staged_campaign_item_count` BIGINT NOT NULL,
    `campaign_business_skipped_item_count` BIGINT NOT NULL, `staged_item_count` BIGINT NOT NULL,
    `source_item_count` BIGINT NOT NULL, `business_skipped_item_count` BIGINT NOT NULL,
    `cursor_page_no` INT NOT NULL DEFAULT 0, `cursor_item_ordinal` INT NOT NULL DEFAULT -1,
    `processed_item_count` BIGINT NOT NULL DEFAULT 0, `campaign_fact_count` BIGINT NOT NULL DEFAULT 0,
    `query_fact_count` BIGINT NOT NULL DEFAULT 0, `identity_skipped_item_count` BIGINT NOT NULL DEFAULT 0,
    `campaign_identity_skipped_item_count` BIGINT NOT NULL DEFAULT 0,
    `query_page_proof_count` INT NOT NULL DEFAULT 0, `matched_active_campaign_count` INT NOT NULL DEFAULT 0,
    `batch_id` BIGINT NOT NULL, `campaign_id_start` BIGINT DEFAULT NULL, `query_id_start` BIGINT DEFAULT NULL,
    `digest_chain_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `source_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    `sealed_at` DATETIME(3) DEFAULT NULL, `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `gmt_updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`task_id`), UNIQUE KEY `uk_dp_ad_generation_batch` (`batch_id`),
    UNIQUE KEY `uk_dp_ad_generation_task_batch` (`task_id`, `batch_id`),
    KEY `idx_dp_ad_generation_scope` (`owner_user_id`, `project_code`, `store_code`, `site_code`, `report_date`, `schedule_slot`, `task_id`),
    KEY `idx_dp_ad_generation_retention` (`state`, `sealed_at`, `task_id`),
    CONSTRAINT `chk_dp_ad_generation_state` CHECK (`state` IN ('PREPARING', 'SEALED')),
    CONSTRAINT `chk_dp_ad_generation_fence` CHECK (`active_fence_epoch`>0 AND ((`state`='PREPARING' AND `sealed_fence_epoch` IS NULL AND `sealed_at` IS NULL AND `source_digest_sha256` IS NULL) OR (`state`='SEALED' AND `sealed_fence_epoch` IS NOT NULL AND `sealed_fence_epoch`=`active_fence_epoch` AND `sealed_at` IS NOT NULL AND `source_digest_sha256` IS NOT NULL AND `source_digest_sha256`=`digest_chain_sha256`))),
    CONSTRAINT `chk_dp_ad_generation_identity` CHECK (`owner_user_id`>0 AND CHAR_LENGTH(TRIM(`project_code`))>0 AND CHAR_LENGTH(TRIM(`store_code`))>0 AND CHAR_LENGTH(TRIM(`site_code`))>0 AND CHAR_LENGTH(TRIM(`business_window_key`))>0),
    CONSTRAINT `chk_dp_ad_generation_digest` CHECK (`authority_token_sha256` REGEXP '^[0-9a-f]{64}$' AND `active_campaign_digest_sha256` REGEXP '^[0-9a-f]{64}$' AND `digest_chain_sha256` REGEXP '^[0-9a-f]{64}$' AND (`source_digest_sha256` IS NULL OR `source_digest_sha256` REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT `chk_dp_ad_generation_extent` CHECK (`declared_campaign_count`>=0 AND `active_campaign_count` BETWEEN 0 AND `declared_campaign_count` AND `last_page`=`active_campaign_count`+1 AND `staged_campaign_item_count`>=0 AND `campaign_business_skipped_item_count`>=0 AND `staged_campaign_item_count`+`campaign_business_skipped_item_count`=`declared_campaign_count` AND `staged_item_count`>=`staged_campaign_item_count`+`active_campaign_count` AND `business_skipped_item_count`>=`campaign_business_skipped_item_count` AND `source_item_count`=`staged_item_count`+`business_skipped_item_count`),
    CONSTRAINT `chk_dp_ad_generation_cursor` CHECK (`cursor_page_no` BETWEEN 0 AND `last_page` AND `cursor_item_ordinal`>=-1 AND ((`processed_item_count`=0 AND `cursor_page_no`=0 AND `cursor_item_ordinal`=-1) OR (`processed_item_count`>0 AND `cursor_page_no`>0 AND `cursor_item_ordinal`>=0))),
    CONSTRAINT `chk_dp_ad_generation_accounting` CHECK (`processed_item_count` BETWEEN 0 AND `staged_item_count` AND `campaign_fact_count`>=0 AND `query_fact_count`>=0 AND `identity_skipped_item_count`>=0 AND `campaign_identity_skipped_item_count` BETWEEN 0 AND `identity_skipped_item_count` AND `query_page_proof_count` BETWEEN 0 AND `active_campaign_count` AND `matched_active_campaign_count` BETWEEN 0 AND `active_campaign_count` AND `processed_item_count`=`campaign_fact_count`+`query_fact_count`+`identity_skipped_item_count`+`query_page_proof_count` AND `campaign_fact_count`+`campaign_identity_skipped_item_count`<=`staged_campaign_item_count`),
    CONSTRAINT `chk_dp_ad_generation_ids` CHECK (`batch_id`>0 AND ((`staged_campaign_item_count`=0 AND `campaign_id_start` IS NULL) OR (`staged_campaign_item_count`>0 AND `campaign_id_start` IS NOT NULL AND `campaign_id_start`>0)) AND ((`staged_item_count`=`staged_campaign_item_count`+`active_campaign_count` AND `query_id_start` IS NULL) OR (`staged_item_count`>`staged_campaign_item_count`+`active_campaign_count` AND `query_id_start` IS NOT NULL AND `query_id_start`>0))),
    CONSTRAINT `chk_dp_ad_generation_sealed` CHECK (`state`<>'SEALED' OR (`processed_item_count`=`staged_item_count` AND `campaign_fact_count`+`campaign_identity_skipped_item_count`=`staged_campaign_item_count` AND `staged_campaign_item_count`+`campaign_business_skipped_item_count`=`declared_campaign_count` AND `query_page_proof_count`=`active_campaign_count` AND `source_item_count`-`active_campaign_count`-`declared_campaign_count`=`query_fact_count`+`identity_skipped_item_count`-`campaign_identity_skipped_item_count`+`business_skipped_item_count`-`campaign_business_skipped_item_count` AND `campaign_fact_count`+`query_fact_count`+`identity_skipped_item_count`+`query_page_proof_count`=`staged_item_count`)),
    CONSTRAINT `fk_dp_ad_generation_task` FOREIGN KEY (`task_id`) REFERENCES `dp_pull_task` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `dp_pull_advertising_campaign_fact` (
    `task_id` BIGINT NOT NULL, `page_no` INT NOT NULL, `item_ordinal` INT NOT NULL,
    `normalized_identity` VARCHAR(240) NOT NULL, `content_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `id` BIGINT NOT NULL, `batch_id` BIGINT NOT NULL, `source_system` VARCHAR(80) NOT NULL,
    `owner_user_id` BIGINT NOT NULL, `project_code` VARCHAR(100) NOT NULL, `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(32) NOT NULL, `report_date_from` DATE NOT NULL, `report_date_to` DATE NOT NULL,
    `campaign_code` VARCHAR(120) NOT NULL, `campaign_name` VARCHAR(500) DEFAULT NULL,
    `campaign_status` VARCHAR(80) DEFAULT NULL, `qc_status` VARCHAR(80) DEFAULT NULL,
    `adgroup_code` VARCHAR(120) DEFAULT NULL, `campaign_start_date` DATE DEFAULT NULL, `campaign_end_date` DATE DEFAULT NULL,
    `views` BIGINT NOT NULL DEFAULT 0, `clicks` BIGINT NOT NULL DEFAULT 0, `orders_count` BIGINT NOT NULL DEFAULT 0,
    `assisted_orders` BIGINT NOT NULL DEFAULT 0, `atc_count` BIGINT NOT NULL DEFAULT 0,
    `spend_amount` DECIMAL(18,6) DEFAULT NULL, `ad_revenue` DECIMAL(18,6) DEFAULT NULL,
    `ctr_percentage` DECIMAL(10,4) DEFAULT NULL, `roas` DECIMAL(18,6) DEFAULT NULL,
    `cpc` DECIMAL(18,6) DEFAULT NULL, `cps` DECIMAL(18,6) DEFAULT NULL, `cvr_percentage` DECIMAL(10,4) DEFAULT NULL,
    `raw_payload_json` LONGTEXT DEFAULT NULL, `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `gmt_updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`task_id`, `page_no`, `item_ordinal`), UNIQUE KEY `uk_dp_ad_campaign_id` (`id`),
    UNIQUE KEY `uk_dp_ad_campaign_identity` (`task_id`, `normalized_identity`),
    UNIQUE KEY `uk_dp_ad_campaign_code` (`task_id`, `campaign_code`), KEY `idx_dp_ad_campaign_batch` (`task_id`, `batch_id`),
    CONSTRAINT `chk_dp_ad_campaign_position` CHECK (`page_no`=1 AND `item_ordinal`>=0),
    CONSTRAINT `chk_dp_ad_campaign_identity` CHECK (CHAR_LENGTH(TRIM(`normalized_identity`))>0 AND CHAR_LENGTH(TRIM(`campaign_code`))>0 AND `content_fingerprint` REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT `chk_dp_ad_campaign_scope` CHECK (`id`>0 AND `batch_id`>0 AND `owner_user_id`>0 AND BINARY `source_system`=BINARY 'noon_ads' AND CHAR_LENGTH(TRIM(`project_code`))>0 AND CHAR_LENGTH(TRIM(`store_code`))>0 AND CHAR_LENGTH(TRIM(`site_code`))>0 AND `report_date_from`=`report_date_to`),
    CONSTRAINT `chk_dp_ad_campaign_counts` CHECK (`views`>=0 AND `clicks`>=0 AND `orders_count`>=0 AND `assisted_orders`>=0 AND `atc_count`>=0),
    CONSTRAINT `fk_dp_ad_campaign_generation` FOREIGN KEY (`task_id`, `batch_id`) REFERENCES `dp_pull_advertising_generation` (`task_id`, `batch_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `dp_pull_advertising_query_fact` (
    `task_id` BIGINT NOT NULL, `page_no` INT NOT NULL, `item_ordinal` INT NOT NULL,
    `normalized_identity` VARCHAR(240) NOT NULL, `content_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `id` BIGINT NOT NULL, `batch_id` BIGINT NOT NULL, `source_system` VARCHAR(80) NOT NULL,
    `owner_user_id` BIGINT NOT NULL, `project_code` VARCHAR(100) NOT NULL, `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(32) NOT NULL, `report_date_from` DATE NOT NULL, `report_date_to` DATE NOT NULL,
    `campaign_code` VARCHAR(120) NOT NULL, `campaign_name` VARCHAR(500) DEFAULT NULL,
    `ad_sku_code` VARCHAR(160) NOT NULL DEFAULT '', `partner_sku` VARCHAR(160) NOT NULL DEFAULT '',
    `query_text` VARCHAR(1000) NOT NULL, `query_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `query_kind` VARCHAR(40) DEFAULT NULL, `views` BIGINT NOT NULL DEFAULT 0, `clicks` BIGINT NOT NULL DEFAULT 0,
    `orders_count` BIGINT NOT NULL DEFAULT 0, `assisted_orders` BIGINT NOT NULL DEFAULT 0, `atc_count` BIGINT NOT NULL DEFAULT 0,
    `spend_amount` DECIMAL(18,6) DEFAULT NULL, `ad_revenue` DECIMAL(18,6) DEFAULT NULL,
    `ctr_percentage` DECIMAL(10,4) DEFAULT NULL, `roas` DECIMAL(18,6) DEFAULT NULL,
    `cpc` DECIMAL(18,6) DEFAULT NULL, `cps` DECIMAL(18,6) DEFAULT NULL, `cvr_percentage` DECIMAL(10,4) DEFAULT NULL,
    `raw_payload_json` LONGTEXT DEFAULT NULL, `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `gmt_updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`task_id`, `page_no`, `item_ordinal`), UNIQUE KEY `uk_dp_ad_query_id` (`id`),
    UNIQUE KEY `uk_dp_ad_query_identity` (`task_id`, `normalized_identity`), KEY `idx_dp_ad_query_batch` (`task_id`, `batch_id`),
    CONSTRAINT `chk_dp_ad_query_position` CHECK (`page_no`>1 AND `item_ordinal`>=0),
    CONSTRAINT `chk_dp_ad_query_identity` CHECK (CHAR_LENGTH(TRIM(`normalized_identity`))>0 AND CHAR_LENGTH(TRIM(`campaign_code`))>0 AND CHAR_LENGTH(TRIM(`query_text`))>0 AND `query_hash` REGEXP '^[0-9a-f]{64}$' AND `content_fingerprint` REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT `chk_dp_ad_query_scope` CHECK (`id`>0 AND `batch_id`>0 AND `owner_user_id`>0 AND BINARY `source_system`=BINARY 'noon_ads' AND CHAR_LENGTH(TRIM(`project_code`))>0 AND CHAR_LENGTH(TRIM(`store_code`))>0 AND CHAR_LENGTH(TRIM(`site_code`))>0 AND `report_date_from`=`report_date_to`),
    CONSTRAINT `chk_dp_ad_query_counts` CHECK (`views`>=0 AND `clicks`>=0 AND `orders_count`>=0 AND `assisted_orders`>=0 AND `atc_count`>=0),
    CONSTRAINT `fk_dp_ad_query_generation` FOREIGN KEY (`task_id`, `batch_id`) REFERENCES `dp_pull_advertising_generation` (`task_id`, `batch_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS `dp_pull_advertising_current_head` (
    `owner_user_id` BIGINT NOT NULL, `project_code` VARCHAR(100) NOT NULL, `store_code` VARCHAR(100) NOT NULL,
    `site_code` VARCHAR(32) NOT NULL, `report_date` DATE NOT NULL, `task_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL, `schedule_slot` DATETIME(3) NOT NULL,
    `authority_token_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `source_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `gmt_updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`owner_user_id`, `project_code`, `store_code`, `site_code`, `report_date`),
    UNIQUE KEY `uk_dp_ad_head_task` (`task_id`), UNIQUE KEY `uk_dp_ad_head_batch` (`batch_id`),
    KEY `idx_dp_ad_head_generation` (`task_id`, `batch_id`),
    CONSTRAINT `chk_dp_ad_head_identity` CHECK (`owner_user_id`>0 AND CHAR_LENGTH(TRIM(`project_code`))>0 AND CHAR_LENGTH(TRIM(`store_code`))>0 AND CHAR_LENGTH(TRIM(`site_code`))>0),
    CONSTRAINT `chk_dp_ad_head_digest` CHECK (`authority_token_sha256` REGEXP '^[0-9a-f]{64}$' AND `source_digest_sha256` REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT `fk_dp_ad_head_generation` FOREIGN KEY (`task_id`, `batch_id`) REFERENCES `dp_pull_advertising_generation` (`task_id`, `batch_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
-- Fence shared legacy sequences above both legacy and invisible generation IDs.
INSERT INTO `noon_ad_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES ('noon_ad_report_batch', GREATEST(200000,COALESCE((SELECT MAX(`id`) FROM `noon_ad_report_batch`),0),COALESCE((SELECT MAX(`batch_id`) FROM `dp_pull_advertising_generation`),0)),NOW(),NOW())
ON DUPLICATE KEY UPDATE `next_id`=GREATEST(`next_id`,VALUES(`next_id`)),`gmt_updated`=NOW();
INSERT INTO `noon_ad_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES ('noon_ad_campaign_fact', GREATEST(210000,COALESCE((SELECT MAX(`id`) FROM `noon_ad_campaign_fact`),0),COALESCE((SELECT MAX(`id`) FROM `dp_pull_advertising_campaign_fact`),0)),NOW(),NOW())
ON DUPLICATE KEY UPDATE `next_id`=GREATEST(`next_id`,VALUES(`next_id`)),`gmt_updated`=NOW();
INSERT INTO `noon_ad_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES ('noon_ad_query_fact', GREATEST(220000,COALESCE((SELECT MAX(`id`) FROM `noon_ad_query_fact`),0),COALESCE((SELECT MAX(`id`) FROM `dp_pull_advertising_query_fact`),0)),NOW(),NOW())
ON DUPLICATE KEY UPDATE `next_id`=GREATEST(`next_id`,VALUES(`next_id`)),`gmt_updated`=NOW();
-- Stable public projections are explicit; malformed or incomplete generations disappear.
CREATE OR REPLACE ALGORITHM=TEMPTABLE SQL SECURITY INVOKER VIEW `dp_pull_advertising_sealed_current_generation` AS
SELECT g.task_id,g.owner_user_id,g.project_code,g.store_code,g.site_code,g.report_date,g.schedule_slot,
       g.authority_token_sha256,g.source_digest_sha256,g.batch_id,g.campaign_fact_count,g.query_fact_count,g.gmt_create,g.gmt_updated
FROM `dp_pull_advertising_current_head` h JOIN `dp_pull_advertising_generation` g ON g.task_id=h.task_id AND g.batch_id=h.batch_id
WHERE g.state='SEALED' AND g.owner_user_id=h.owner_user_id AND BINARY g.project_code=BINARY h.project_code
  AND BINARY g.store_code=BINARY h.store_code AND BINARY g.site_code=BINARY h.site_code
  AND g.report_date=h.report_date AND g.schedule_slot=h.schedule_slot
  AND g.authority_token_sha256=h.authority_token_sha256 AND g.source_digest_sha256=h.source_digest_sha256
  AND g.processed_item_count=g.staged_item_count
  AND g.campaign_fact_count+g.campaign_identity_skipped_item_count=g.staged_campaign_item_count
  AND g.staged_campaign_item_count+g.campaign_business_skipped_item_count=g.declared_campaign_count
  AND g.query_page_proof_count=g.active_campaign_count
  AND g.source_item_count-g.active_campaign_count-g.declared_campaign_count=g.query_fact_count+g.identity_skipped_item_count-g.campaign_identity_skipped_item_count+g.business_skipped_item_count-g.campaign_business_skipped_item_count
  AND g.campaign_fact_count+g.query_fact_count+g.identity_skipped_item_count+g.query_page_proof_count=g.staged_item_count
  AND g.campaign_fact_count=(SELECT COUNT(*) FROM `dp_pull_advertising_campaign_fact` c WHERE c.task_id=g.task_id AND c.batch_id=g.batch_id AND c.owner_user_id=g.owner_user_id AND BINARY c.project_code=BINARY g.project_code AND BINARY c.store_code=BINARY g.store_code AND BINARY c.site_code=BINARY g.site_code AND c.report_date_from=g.report_date AND c.report_date_to=g.report_date AND BINARY c.source_system=BINARY 'noon_ads')
  AND g.campaign_fact_count=(SELECT COUNT(*) FROM `dp_pull_advertising_campaign_fact` c WHERE c.task_id=g.task_id)
  AND g.query_fact_count=(SELECT COUNT(*) FROM `dp_pull_advertising_query_fact` q WHERE q.task_id=g.task_id AND q.batch_id=g.batch_id AND q.owner_user_id=g.owner_user_id AND BINARY q.project_code=BINARY g.project_code AND BINARY q.store_code=BINARY g.store_code AND BINARY q.site_code=BINARY g.site_code AND q.report_date_from=g.report_date AND q.report_date_to=g.report_date AND BINARY q.source_system=BINARY 'noon_ads')
  AND g.query_fact_count=(SELECT COUNT(*) FROM `dp_pull_advertising_query_fact` q WHERE q.task_id=g.task_id);
CREATE OR REPLACE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW `noon_ad_effective_report_batch` AS
SELECT b.id,b.source_system,b.source_name,b.source_digest_sha256,b.owner_user_id,b.project_code,b.store_code,b.site_code,
       b.report_date_from,b.report_date_to,b.status,b.campaign_row_count,b.query_row_count,b.notes,b.created_by,b.updated_by,b.gmt_create,b.gmt_updated
FROM `noon_ad_report_batch` b WHERE NOT EXISTS (SELECT 1 FROM `dp_pull_advertising_current_head` h WHERE h.owner_user_id=b.owner_user_id AND BINARY h.project_code=BINARY b.project_code AND BINARY h.store_code=BINARY b.store_code AND BINARY h.site_code=BINARY b.site_code AND h.report_date=b.report_date_from AND b.report_date_from=b.report_date_to)
UNION ALL
SELECT g.batch_id AS id,'noon_ads' AS source_system,'DP06 scheduled pull' AS source_name,g.source_digest_sha256,
       g.owner_user_id,g.project_code,g.store_code,g.site_code,g.report_date AS report_date_from,g.report_date AS report_date_to,
       'imported' AS status,g.campaign_fact_count AS campaign_row_count,g.query_fact_count AS query_row_count,NULL AS notes,
       NULL AS created_by,NULL AS updated_by,g.gmt_create,g.gmt_updated FROM `dp_pull_advertising_sealed_current_generation` g;
CREATE OR REPLACE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW `noon_ad_effective_campaign_fact` AS
SELECT c.id,c.batch_id,c.source_system,c.owner_user_id,c.project_code,c.store_code,c.site_code,c.report_date_from,c.report_date_to,
       c.campaign_code,c.campaign_name,c.campaign_status,c.qc_status,c.adgroup_code,c.campaign_start_date,c.campaign_end_date,
       c.views,c.clicks,c.orders_count,c.assisted_orders,c.atc_count,c.spend_amount,c.ad_revenue,c.ctr_percentage,c.roas,c.cpc,c.cps,
       c.cvr_percentage,c.zero_order_spend_amount,c.zero_order_spend_share,c.raw_payload_json,c.gmt_create,c.gmt_updated
FROM `noon_ad_campaign_fact` c WHERE NOT EXISTS (SELECT 1 FROM `dp_pull_advertising_current_head` h WHERE h.owner_user_id=c.owner_user_id AND BINARY h.project_code=BINARY c.project_code AND BINARY h.store_code=BINARY c.store_code AND BINARY h.site_code=BINARY c.site_code AND h.report_date=c.report_date_from AND c.report_date_from=c.report_date_to)
UNION ALL
SELECT c.id,c.batch_id,c.source_system,c.owner_user_id,c.project_code,c.store_code,c.site_code,c.report_date_from,c.report_date_to,
       c.campaign_code,c.campaign_name,c.campaign_status,c.qc_status,c.adgroup_code,c.campaign_start_date,c.campaign_end_date,
       c.views,c.clicks,c.orders_count,c.assisted_orders,c.atc_count,c.spend_amount,c.ad_revenue,c.ctr_percentage,c.roas,c.cpc,c.cps,
       c.cvr_percentage,CAST(NULL AS DECIMAL(18,6)) AS zero_order_spend_amount,CAST(NULL AS DECIMAL(10,6)) AS zero_order_spend_share,
       c.raw_payload_json,c.gmt_create,c.gmt_updated
FROM `dp_pull_advertising_sealed_current_generation` g JOIN `dp_pull_advertising_campaign_fact` c
  ON c.task_id=g.task_id AND c.batch_id=g.batch_id AND c.owner_user_id=g.owner_user_id
 AND BINARY c.project_code=BINARY g.project_code AND BINARY c.store_code=BINARY g.store_code
 AND BINARY c.site_code=BINARY g.site_code AND c.report_date_from=g.report_date AND c.report_date_to=g.report_date;
CREATE OR REPLACE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW `noon_ad_effective_query_fact` AS
SELECT q.id,q.batch_id,q.source_system,q.owner_user_id,q.project_code,q.store_code,q.site_code,q.report_date_from,q.report_date_to,
       q.campaign_code,q.campaign_name,q.ad_sku_code,q.partner_sku,q.query_text,q.query_hash,q.query_kind,q.views,q.clicks,
       q.orders_count,q.assisted_orders,q.atc_count,q.spend_amount,q.ad_revenue,q.ctr_percentage,q.roas,q.cpc,q.cps,q.cvr_percentage,
       q.raw_payload_json,q.gmt_create,q.gmt_updated FROM `noon_ad_query_fact` q
WHERE NOT EXISTS (SELECT 1 FROM `dp_pull_advertising_current_head` h WHERE h.owner_user_id=q.owner_user_id AND BINARY h.project_code=BINARY q.project_code AND BINARY h.store_code=BINARY q.store_code AND BINARY h.site_code=BINARY q.site_code AND h.report_date=q.report_date_from AND q.report_date_from=q.report_date_to)
UNION ALL
SELECT q.id,q.batch_id,q.source_system,q.owner_user_id,q.project_code,q.store_code,q.site_code,q.report_date_from,q.report_date_to,
       q.campaign_code,q.campaign_name,q.ad_sku_code,q.partner_sku,q.query_text,q.query_hash,q.query_kind,q.views,q.clicks,
       q.orders_count,q.assisted_orders,q.atc_count,q.spend_amount,q.ad_revenue,q.ctr_percentage,q.roas,q.cpc,q.cps,q.cvr_percentage,
       q.raw_payload_json,q.gmt_create,q.gmt_updated
FROM `dp_pull_advertising_sealed_current_generation` g JOIN `dp_pull_advertising_query_fact` q
  ON q.task_id=g.task_id AND q.batch_id=g.batch_id AND q.owner_user_id=g.owner_user_id
 AND BINARY q.project_code=BINARY g.project_code AND BINARY q.store_code=BINARY g.store_code
 AND BINARY q.site_code=BINARY g.site_code AND q.report_date_from=g.report_date AND q.report_date_to=g.report_date;
-- Registration gates: MySQL 8 rerun, exact/live metadata, generation/head/fact closure, high-cardinality
-- coverage, and technical size/range failures rejected without row truncation.
