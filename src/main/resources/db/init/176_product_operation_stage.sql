SET NAMES utf8mb4;

SET @pso_operation_stage_code_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'operation_stage_code'
);

SET @pso_operation_stage_code_ddl := IF(
    @pso_operation_stage_code_exists = 0,
    'ALTER TABLE `product_site_offer` ADD COLUMN `operation_stage_code` VARCHAR(32) DEFAULT NULL AFTER `listing_started_source`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_operation_stage_code_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_operation_stage_updated_at_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'operation_stage_updated_at'
);

SET @pso_operation_stage_updated_at_ddl := IF(
    @pso_operation_stage_updated_at_exists = 0,
    'ALTER TABLE `product_site_offer` ADD COLUMN `operation_stage_updated_at` DATETIME DEFAULT NULL AFTER `operation_stage_code`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_operation_stage_updated_at_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_operation_stage_updated_by_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'operation_stage_updated_by'
);

SET @pso_operation_stage_updated_by_ddl := IF(
    @pso_operation_stage_updated_by_exists = 0,
    'ALTER TABLE `product_site_offer` ADD COLUMN `operation_stage_updated_by` BIGINT DEFAULT NULL AFTER `operation_stage_updated_at`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_operation_stage_updated_by_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_operation_stage_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND INDEX_NAME = 'idx_product_site_offer_operation_stage'
);

SET @pso_operation_stage_index_ddl := IF(
    @pso_operation_stage_index_exists = 0,
    'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_operation_stage` (`logical_store_id`, `site_id`, `operation_stage_code`)',
    'SELECT 1'
);

PREPARE stmt FROM @pso_operation_stage_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
