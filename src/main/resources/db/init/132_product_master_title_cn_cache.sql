SET @add_product_master_title_cn_cache := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND COLUMN_NAME = 'title_cn_cache'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD COLUMN `title_cn_cache` VARCHAR(500) DEFAULT NULL AFTER `title_cache`'
    )
);
PREPARE stmt FROM @add_product_master_title_cn_cache;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_product_master_title_cn_cache_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_master'
              AND INDEX_NAME = 'idx_product_master_title_cn_cache'
        ),
        'SELECT 1',
        'ALTER TABLE `product_master` ADD KEY `idx_product_master_title_cn_cache` (`title_cn_cache`(191))'
    )
);
PREPARE stmt FROM @add_product_master_title_cn_cache_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
