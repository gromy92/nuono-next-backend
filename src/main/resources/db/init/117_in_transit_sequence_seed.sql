-- Ensure in-transit id sequences start after any seeded or migrated rows.

SET NAMES utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT sequence_rows.`sequence_name`, sequence_rows.`next_id`, NOW(), NOW()
FROM (
  SELECT 'in_transit_forwarder' AS `sequence_name`, GREATEST(51000, COALESCE((SELECT MAX(`id`) FROM `in_transit_forwarder`), 0)) AS `next_id`
  UNION ALL
  SELECT 'in_transit_forwarder_alias', GREATEST(52000, COALESCE((SELECT MAX(`id`) FROM `in_transit_forwarder_alias`), 0))
  UNION ALL
  SELECT 'in_transit_batch', GREATEST(53000, COALESCE((SELECT MAX(`id`) FROM `in_transit_batch`), 0))
  UNION ALL
  SELECT 'in_transit_goods_line', GREATEST(54000, COALESCE((SELECT MAX(`id`) FROM `in_transit_goods_line`), 0))
  UNION ALL
  SELECT 'in_transit_logistics_node', GREATEST(55000, COALESCE((SELECT MAX(`id`) FROM `in_transit_logistics_node`), 0))
  UNION ALL
  SELECT 'in_transit_import_batch', GREATEST(56000, COALESCE((SELECT MAX(`id`) FROM `in_transit_import_batch`), 0))
  UNION ALL
  SELECT 'in_transit_operation_audit', GREATEST(57000, COALESCE((SELECT MAX(`id`) FROM `in_transit_operation_audit`), 0))
  UNION ALL
  SELECT 'in_transit_package', GREATEST(58000, COALESCE((SELECT MAX(`id`) FROM `in_transit_package`), 0))
) sequence_rows
ON DUPLICATE KEY UPDATE
  `next_id` = GREATEST(`product_management_id_sequence`.`next_id`, VALUES(`next_id`)),
  `gmt_updated` = NOW();
