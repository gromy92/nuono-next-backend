-- Treat every explicit browser collection run as a new batch while keeping exact request retries idempotent.

SET @plugin_batch_id_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_selection_source_collection'
      AND COLUMN_NAME = 'plugin_batch_id'
);

SET @plugin_batch_id_column_ddl := IF(
    @plugin_batch_id_column_exists = 0,
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `plugin_batch_id` VARCHAR(100) DEFAULT NULL AFTER `collection_source`',
    'SELECT 1'
);

PREPARE stmt FROM @plugin_batch_id_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @plugin_item_key_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_selection_source_collection'
      AND COLUMN_NAME = 'plugin_item_key'
);

SET @plugin_item_key_column_ddl := IF(
    @plugin_item_key_column_exists = 0,
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `plugin_item_key` VARCHAR(120) DEFAULT NULL AFTER `plugin_batch_id`',
    'SELECT 1'
);

PREPARE stmt FROM @plugin_item_key_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @plugin_extractor_version_column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_selection_source_collection'
      AND COLUMN_NAME = 'extractor_version'
);

SET @plugin_extractor_version_column_ddl := IF(
    @plugin_extractor_version_column_exists = 0,
    'ALTER TABLE `product_selection_source_collection` ADD COLUMN `extractor_version` VARCHAR(160) DEFAULT NULL AFTER `plugin_item_key`',
    'SELECT 1'
);

PREPARE stmt FROM @plugin_extractor_version_column_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @plugin_batch_item_index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'product_selection_source_collection'
      AND INDEX_NAME = 'uk_source_collection_plugin_batch_item'
);

SET @plugin_batch_item_index_ddl := IF(
    @plugin_batch_item_index_exists = 0,
    'ALTER TABLE `product_selection_source_collection` ADD UNIQUE KEY `uk_source_collection_plugin_batch_item` (`owner_user_id`, `logical_store_id`, `plugin_batch_id`, `plugin_item_key`)',
    'SELECT 1'
);

PREPARE stmt FROM @plugin_batch_item_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
