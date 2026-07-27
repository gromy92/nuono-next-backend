-- One durable confirmation claim per validated dry-run.
--
-- Historical task rows remain unchanged. INSERT IGNORE deterministically records one
-- existing REAL_RUN task for each owner/source pair, including failed and rejected
-- outcomes that migration 186 did not reserve in its generated unique column.

CREATE TABLE IF NOT EXISTS `product_listing_real_run_attempt_claim` (
  `owner_user_id` BIGINT NOT NULL,
  `source_task_id` BIGINT NOT NULL,
  `attempt_task_id` BIGINT NOT NULL,
  `claimed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`owner_user_id`, `source_task_id`),
  KEY `idx_product_listing_attempt_claim_task` (`attempt_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO product_listing_real_run_attempt_claim (
  owner_user_id,
  source_task_id,
  attempt_task_id,
  claimed_at,
  gmt_updated
)
SELECT
  `owner_user_id`,
  `source_task_id`,
  MIN(`id`) AS `attempt_task_id`,
  COALESCE(MIN(`submitted_at`), NOW()) AS `claimed_at`,
  NOW() AS `gmt_updated`
FROM `product_listing_task`
WHERE `mode` = 'REAL_RUN'
  AND `source_task_id` IS NOT NULL
GROUP BY `owner_user_id`, `source_task_id`;
