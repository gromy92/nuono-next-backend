-- Product maintenance boundary.
-- site_enabled is store/site scope and is maintained by data correction only.
-- maintenance_enabled is product/site scope; new rows default into Nuono maintenance.

SET @lss_site_enabled_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'logical_store_site'
      AND COLUMN_NAME = 'site_enabled'
);

SET @lss_site_enabled_ddl := IF(
    @lss_site_enabled_exists = 0,
    'ALTER TABLE `logical_store_site` ADD COLUMN `site_enabled` BIT(1) NOT NULL DEFAULT b''1'' AFTER `is_mounted`',
    'SELECT 1'
);

PREPARE stmt FROM @lss_site_enabled_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_maintenance_enabled_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND COLUMN_NAME = 'maintenance_enabled'
);

SET @pso_maintenance_enabled_ddl := IF(
    @pso_maintenance_enabled_exists = 0,
    'ALTER TABLE `product_site_offer` ADD COLUMN `maintenance_enabled` BIT(1) NOT NULL DEFAULT b''1'' AFTER `is_active`',
    'SELECT 1'
);

PREPARE stmt FROM @pso_maintenance_enabled_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @lss_site_enabled_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'logical_store_site'
      AND INDEX_NAME = 'idx_logical_store_site_enabled'
);

SET @lss_site_enabled_index_ddl := IF(
    @lss_site_enabled_index_exists = 0,
    'ALTER TABLE `logical_store_site` ADD KEY `idx_logical_store_site_enabled` (`logical_store_id`, `site_enabled`, `site`)',
    'SELECT 1'
);

PREPARE stmt FROM @lss_site_enabled_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pso_maintenance_enabled_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_site_offer'
      AND INDEX_NAME = 'idx_product_site_offer_maintenance'
);

SET @pso_maintenance_enabled_index_ddl := IF(
    @pso_maintenance_enabled_index_exists = 0,
    'ALTER TABLE `product_site_offer` ADD KEY `idx_product_site_offer_maintenance` (`logical_store_id`, `site_id`, `maintenance_enabled`)',
    'SELECT 1'
);

PREPARE stmt FROM @pso_maintenance_enabled_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
