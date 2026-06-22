SET NAMES utf8mb4;

UPDATE `in_transit_goods_line`
SET `psku` = NULLIF(TRIM(`psku`), '')
WHERE `psku` IS NOT NULL;

UPDATE `in_transit_goods_line`
SET `psku` = NULLIF(TRIM(`sku`), '')
WHERE (`psku` IS NULL OR `psku` = '')
  AND `sku` IS NOT NULL
  AND NULLIF(TRIM(`sku`), '') IS NOT NULL;

UPDATE `in_transit_goods_line`
SET `psku` = CONCAT('LEGACY-MISSING-PSKU-', `id`)
WHERE `psku` IS NULL OR `psku` = '';

UPDATE `in_transit_goods_line`
SET `box_no` = NULLIF(TRIM(`box_no`), '')
WHERE `box_no` IS NOT NULL;

UPDATE `in_transit_goods_line`
SET `box_no` = CONCAT('LEGACY-NO-BOX-', `id`)
WHERE `box_no` IS NULL OR `box_no` = '';

SET @modify_line_box_no_required = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND column_name = 'box_no'
          AND is_nullable = 'YES'
    ),
    'ALTER TABLE `in_transit_goods_line` MODIFY COLUMN `box_no` VARCHAR(160) NOT NULL',
    'SELECT ''box_no_required_exists'' AS stage'
);
PREPARE modify_line_box_no_required_stmt FROM @modify_line_box_no_required;
EXECUTE modify_line_box_no_required_stmt;
DEALLOCATE PREPARE modify_line_box_no_required_stmt;

SET @modify_line_sku_nullable = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND column_name = 'sku'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `in_transit_goods_line` MODIFY COLUMN `sku` VARCHAR(160) DEFAULT NULL',
    'SELECT ''sku_nullable_exists'' AS stage'
);
PREPARE modify_line_sku_nullable_stmt FROM @modify_line_sku_nullable;
EXECUTE modify_line_sku_nullable_stmt;
DEALLOCATE PREPARE modify_line_sku_nullable_stmt;

SET @modify_line_psku_required = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND column_name = 'psku'
          AND is_nullable = 'YES'
    ),
    'ALTER TABLE `in_transit_goods_line` MODIFY COLUMN `psku` VARCHAR(160) NOT NULL',
    'SELECT ''psku_required_exists'' AS stage'
);
PREPARE modify_line_psku_required_stmt FROM @modify_line_psku_required;
EXECUTE modify_line_psku_required_stmt;
DEALLOCATE PREPARE modify_line_psku_required_stmt;

SET @add_line_active_unique_key = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND column_name = 'active_unique_key'
    ),
    'SELECT ''active_unique_key_exists'' AS stage',
    'ALTER TABLE `in_transit_goods_line` ADD COLUMN `active_unique_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `is_deleted` = b''0'' THEN 1 ELSE NULL END) STORED AFTER `is_deleted`'
);
PREPARE add_line_active_unique_key_stmt FROM @add_line_active_unique_key;
EXECUTE add_line_active_unique_key_stmt;
DEALLOCATE PREPARE add_line_active_unique_key_stmt;

SET @add_line_box_psku_unique = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND index_name = 'uk_in_transit_line_box_psku'
    ),
    'SELECT ''uk_in_transit_line_box_psku_exists'' AS stage',
    'ALTER TABLE `in_transit_goods_line` ADD UNIQUE KEY `uk_in_transit_line_box_psku` (`owner_user_id`, `batch_id`, `box_no`, `psku`, `active_unique_key`)'
);
PREPARE add_line_box_psku_unique_stmt FROM @add_line_box_psku_unique;
EXECUTE add_line_box_psku_unique_stmt;
DEALLOCATE PREPARE add_line_box_psku_unique_stmt;

SET @add_line_batch_psku_idx = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_goods_line'
          AND index_name = 'idx_in_transit_goods_line_batch_psku'
    ),
    'SELECT ''idx_in_transit_goods_line_batch_psku_exists'' AS stage',
    'ALTER TABLE `in_transit_goods_line` ADD KEY `idx_in_transit_goods_line_batch_psku` (`owner_user_id`, `batch_id`, `psku`, `is_deleted`)'
);
PREPARE add_line_batch_psku_idx_stmt FROM @add_line_batch_psku_idx;
EXECUTE add_line_batch_psku_idx_stmt;
DEALLOCATE PREPARE add_line_batch_psku_idx_stmt;
