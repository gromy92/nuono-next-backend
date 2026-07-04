-- Persist richer metadata captured during competitor keyword ranking searches.

SET NAMES utf8mb4;

SET @ops_comp_search_result_add_title_en := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_search_result'
              AND COLUMN_NAME = 'title_en_snapshot'
        ),
        'ALTER TABLE `operations_competitor_search_result` ADD COLUMN `title_en_snapshot` VARCHAR(500) DEFAULT NULL AFTER `title_snapshot`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_search_result_add_title_en;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_search_result_add_title_ar := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_search_result'
              AND COLUMN_NAME = 'title_ar_snapshot'
        ),
        'ALTER TABLE `operations_competitor_search_result` ADD COLUMN `title_ar_snapshot` VARCHAR(500) DEFAULT NULL AFTER `title_en_snapshot`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_search_result_add_title_ar;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_search_result_add_tags := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_search_result'
              AND COLUMN_NAME = 'tags_json'
        ),
        'ALTER TABLE `operations_competitor_search_result` ADD COLUMN `tags_json` JSON DEFAULT NULL AFTER `is_sponsored`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_search_result_add_tags;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `operations_competitor_search_result`
SET `title_en_snapshot` = `title_snapshot`
WHERE `title_en_snapshot` IS NULL
  AND NULLIF(TRIM(`title_snapshot`), '') IS NOT NULL;

SET @ops_comp_product_add_title_en := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_product'
              AND COLUMN_NAME = 'title_en_snapshot'
        ),
        'ALTER TABLE `operations_competitor_product` ADD COLUMN `title_en_snapshot` VARCHAR(500) DEFAULT NULL AFTER `title_snapshot`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_product_add_title_en;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_product_add_title_ar := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_product'
              AND COLUMN_NAME = 'title_ar_snapshot'
        ),
        'ALTER TABLE `operations_competitor_product` ADD COLUMN `title_ar_snapshot` VARCHAR(500) DEFAULT NULL AFTER `title_en_snapshot`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_product_add_title_ar;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ops_comp_product_add_tags := (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'operations_competitor_product'
              AND COLUMN_NAME = 'tags_snapshot_json'
        ),
        'ALTER TABLE `operations_competitor_product` ADD COLUMN `tags_snapshot_json` JSON DEFAULT NULL AFTER `review_count_snapshot`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ops_comp_product_add_tags;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `operations_competitor_product`
SET `title_en_snapshot` = `title_snapshot`
WHERE `title_en_snapshot` IS NULL
  AND NULLIF(TRIM(`title_snapshot`), '') IS NOT NULL;
