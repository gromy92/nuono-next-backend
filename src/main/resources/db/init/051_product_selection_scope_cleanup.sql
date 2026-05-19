-- Remove deferred product-selection surfaces from existing local databases.
-- Keep product_selection_source_collection for manual selection source records.

SET NAMES utf8mb4;

UPDATE `user_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `menu_id` = 9101
  AND `is_deleted` = b'0';

UPDATE `role_menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `menu_id` = 9101
  AND `is_deleted` = b'0';

UPDATE `menu`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE (`id` = 9101 OR `url_path` = '/product-selection' OR `name` = '自动选品')
  AND `is_deleted` = b'0';

DELETE FROM `product_management_id_sequence`
WHERE `sequence_name` IN (
  'product_selection_strategy',
  'product_selection_run',
  'product_selection_candidate',
  'product_selection_candidate_evidence',
  'product_selection_operation_log',
  'product_selection_pricing_action',
  'product_selection_ali1688_collection_task',
  'product_selection_ali1688_candidate',
  'product_selection_ali1688_candidate_ai_assessment'
);

DROP TABLE IF EXISTS `product_selection_ali1688_candidate_ai_assessment`;
DROP TABLE IF EXISTS `product_selection_ali1688_candidate`;
DROP TABLE IF EXISTS `product_selection_ali1688_collection_task`;
DROP TABLE IF EXISTS `product_selection_pricing_action`;
DROP TABLE IF EXISTS `product_selection_operation_log`;
DROP TABLE IF EXISTS `product_selection_candidate_evidence`;
DROP TABLE IF EXISTS `product_selection_candidate`;
DROP TABLE IF EXISTS `product_selection_run`;
DROP TABLE IF EXISTS `product_selection_strategy`;
