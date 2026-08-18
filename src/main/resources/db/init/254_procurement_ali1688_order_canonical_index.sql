-- Canonical 1688 order reads and cross-authorization writes resolve by provider order.
SET NAMES utf8mb4;

SET @ali1688_order_superseded_column_shape := (
  SELECT CONCAT(column_type, '|', is_nullable)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'procurement_ali1688_order_header'
    AND column_name = 'superseded_by_order_id'
);
SET @ali1688_order_superseded_column_sql := IF(
  @ali1688_order_superseded_column_shape IS NULL,
  'ALTER TABLE `procurement_ali1688_order_header` ADD COLUMN `superseded_by_order_id` BIGINT DEFAULT NULL, ALGORITHM=INSTANT',
  IF(
    @ali1688_order_superseded_column_shape = 'bigint|YES',
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''PROC_ALI1688_ORDER_SUPERSEDED_COLUMN_DRIFT'''
  )
);
PREPARE ali1688_order_superseded_column_stmt FROM @ali1688_order_superseded_column_sql;
EXECUTE ali1688_order_superseded_column_stmt;
DEALLOCATE PREPARE ali1688_order_superseded_column_stmt;

CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_dedup_audit` (
  `correction_code` VARCHAR(80) NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `entity_type` VARCHAR(40) NOT NULL,
  `entity_id` BIGINT NOT NULL,
  `canonical_id` BIGINT DEFAULT NULL,
  `original_authorization_id` BIGINT DEFAULT NULL,
  `original_order_id` BIGINT DEFAULT NULL,
  `original_item_id` BIGINT DEFAULT NULL,
  `original_assignment_id` BIGINT DEFAULT NULL,
  `original_status` VARCHAR(30) DEFAULT NULL,
  `original_is_deleted` BIT(1) DEFAULT NULL,
  `original_deleted_by` BIGINT DEFAULT NULL,
  `original_deleted_at` DATETIME DEFAULT NULL,
  `original_delete_reason` VARCHAR(500) DEFAULT NULL,
  `original_gmt_updated` DATETIME DEFAULT NULL,
  `snapshot_json` LONGTEXT DEFAULT NULL,
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`correction_code`, `entity_type`, `entity_id`),
  KEY `idx_proc_ali1688_dedup_audit_canonical`
    (`owner_user_id`, `entity_type`, `canonical_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ali1688_order_canonical_index_shape := (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'procurement_ali1688_order_header'
    AND index_name = 'idx_proc_ali1688_order_canonical'
);

SET @ali1688_order_canonical_index_sql := IF(
  @ali1688_order_canonical_index_shape IS NULL,
  'ALTER TABLE `procurement_ali1688_order_header` ADD INDEX `idx_proc_ali1688_order_canonical` (`owner_user_id`, `provider_order_no`, `superseded_by_order_id`, `is_deleted`, `authorization_id`, `gmt_updated`, `id`), ALGORITHM=INPLACE, LOCK=NONE',
  IF(
    @ali1688_order_canonical_index_shape = 'owner_user_id,provider_order_no,superseded_by_order_id,is_deleted,authorization_id,gmt_updated,id',
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''PROC_ALI1688_ORDER_CANONICAL_INDEX_DRIFT'''
  )
);
PREPARE ali1688_order_canonical_index_stmt FROM @ali1688_order_canonical_index_sql;
EXECUTE ali1688_order_canonical_index_stmt;
DEALLOCATE PREPARE ali1688_order_canonical_index_stmt;
