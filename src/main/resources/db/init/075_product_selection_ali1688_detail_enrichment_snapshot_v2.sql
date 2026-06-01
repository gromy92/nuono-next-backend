-- DETAIL_ENRICHMENT v2：把 window.context.result.data 抽出的结构化详情提升为专用列。
-- 仅 additive ALTER，保留 072 的 v0 列；幂等（重复执行不报错）。
-- 字段映射依据 .scratch/procurement-ali1688-ai-match-price-confirmation/evidence/issue-11-detail-extraction-v2-live-context-2026-05-29.md。
-- 页面价/物流价仅作 AI 线索，真实采购价仍以价格预览/未支付订单为准。

SET NAMES utf8mb4;

SET @ali1688_detail_v2_add_unit := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'unit'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `unit` VARCHAR(60) NULL AFTER `detail_title`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_unit;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_detail_v2_add_variant_image := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'variant_image_urls_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `variant_image_urls_json` MEDIUMTEXT NULL AFTER `image_urls_json`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_variant_image;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_detail_v2_add_attributes := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'attributes_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `attributes_json` MEDIUMTEXT NULL AFTER `sku_options_json`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_attributes;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_detail_v2_add_sku_combinations := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'sku_combinations_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `sku_combinations_json` MEDIUMTEXT NULL AFTER `attributes_json`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_sku_combinations;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_detail_v2_add_sku_count := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'sku_count'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `sku_count` INT NULL AFTER `sku_combinations_json`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_sku_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_detail_v2_add_page_price_hint := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'page_price_hint_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `page_price_hint_json` MEDIUMTEXT NULL AFTER `list_price_text`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_page_price_hint;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_detail_v2_add_supplier_profile := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'supplier_profile_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `supplier_profile_json` MEDIUMTEXT NULL AFTER `supplier_name`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_supplier_profile;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_detail_v2_add_shipping_snapshot := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'shipping_snapshot_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `shipping_snapshot_json` MEDIUMTEXT NULL AFTER `location_text`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_shipping_snapshot;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ali1688_detail_v2_add_video := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_selection_ali1688_detail_enrichment_snapshot'
              AND COLUMN_NAME = 'video_json'
        ),
        'SELECT 1',
        'ALTER TABLE `product_selection_ali1688_detail_enrichment_snapshot` ADD COLUMN `video_json` MEDIUMTEXT NULL AFTER `detail_image_urls_json`'
    )
);
PREPARE stmt FROM @ali1688_detail_v2_add_video;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
