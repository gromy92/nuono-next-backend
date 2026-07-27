-- Keep warehouse quote confirmations independent for every item/channel combination.
-- Purchase-order quote rows retain their original one-row-per-item rule.

SET @quote_drop_item_site_unique := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND INDEX_NAME = 'uk_po_logistics_quote_active_item_site'
        ),
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` DROP INDEX `uk_po_logistics_quote_active_item_site`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @quote_drop_item_site_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `procurement_purchase_order_logistics_quote_line`
    MODIFY COLUMN `active_item_site_slot` VARCHAR(512)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = b'0' AND `shipping_order_id` IS NULL
                    THEN CONCAT('PO:', CAST(`purchase_order_item_site_id` AS CHAR))
                WHEN `is_deleted` = b'0'
                    THEN CONCAT(
                        'SO:', CAST(`shipping_order_id` AS CHAR),
                        ':', CAST(`purchase_order_item_site_id` AS CHAR),
                        ':', UPPER(TRIM(COALESCE(NULLIF(`forwarder_code`, ''), '__UNASSIGNED__'))),
                        ':', UPPER(TRIM(COALESCE(NULLIF(`route_code`, ''), '__UNASSIGNED__'))),
                        ':', UPPER(TRIM(COALESCE(NULLIF(`service_code`, ''), '__UNASSIGNED__')))
                    )
                ELSE NULL
            END
        ) STORED;

SET @quote_add_item_channel_unique := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND INDEX_NAME = 'uk_po_logistics_quote_active_item_site'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD UNIQUE KEY `uk_po_logistics_quote_active_item_site` (`active_item_site_slot`)'
    )
);
PREPARE stmt FROM @quote_add_item_channel_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @quote_add_shipping_channel_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_purchase_order_logistics_quote_line'
              AND INDEX_NAME = 'idx_po_logistics_quote_shipping_channel'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_purchase_order_logistics_quote_line` ADD KEY `idx_po_logistics_quote_shipping_channel` (`shipping_order_id`, `purchase_order_item_site_id`, `forwarder_code`, `route_code`, `service_code`, `is_deleted`)'
    )
);
PREPARE stmt FROM @quote_add_shipping_channel_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Recover channel confirmations that were projected to the product quote ledger
-- before another channel reused and rewrote the former warehouse quote row.
SET @quote_channel_backfill_id := (
    SELECT GREATEST(
        COALESCE(MAX(`id`), 279999),
        COALESCE((
            SELECT `next_id` - 1
            FROM `product_management_id_sequence`
            WHERE `sequence_name` = 'procurement_purchase_order_logistics_quote_line'
        ), 279999)
    )
    FROM `procurement_purchase_order_logistics_quote_line`
);

INSERT INTO `procurement_purchase_order_logistics_quote_line` (
    `id`, `owner_user_id`, `logical_store_id`,
    `shipping_order_id`, `shipping_order_no`, `shipping_order_segment_id`, `shipping_order_line_id`,
    `purchase_order_id`, `purchase_order_no`, `purchase_order_title`,
    `purchase_order_item_id`, `purchase_order_item_site_id`, `product_master_id`, `product_variant_id`,
    `sku_parent`, `partner_sku`, `title_cache`, `site_code`, `psku_code`, `yite_material`,
    `planned_transport_mode`, `quantity`, `fulfillment_type`, `is_new_product`,
    `quote_status`, `shipping_submit_status`,
    `forwarder_code`, `forwarder_name`, `route_code`, `route_name`, `service_code`, `service_name`,
    `currency`, `unit_price`, `billing_unit`, `estimated_amount`, `remark`,
    `confirmed_at`, `confirmed_by`,
    `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
    (@quote_channel_backfill_id := @quote_channel_backfill_id + 1),
    sol.`owner_user_id`, sol.`logical_store_id`,
    sol.`shipping_order_id`, so.`shipping_order_no`, sol.`shipping_order_segment_id`, sol.`id`,
    sol.`purchase_order_id`, sol.`purchase_order_no`, sol.`purchase_order_title`,
    sol.`purchase_order_item_id`, sol.`purchase_order_item_site_id`, sol.`product_master_id`, sol.`product_variant_id`,
    sol.`sku_parent`, sol.`partner_sku`, sol.`title_cache`, sol.`site_code`, sol.`psku_code`, sol.`yite_material`,
    sol.`planned_transport_mode`, sol.`quantity`, sol.`fulfillment_type`, COALESCE(balance.`is_new_product`, b'0'),
    'CONFIRMED', 'NOT_SUBMITTED',
    pfcq.`forwarder_code`, pfcq.`forwarder_name`, pfcq.`route_code`, pfcq.`route_name`,
    pfcq.`service_code`, pfcq.`service_name`,
    pfcq.`currency`, pfcq.`unit_price`, pfcq.`billing_unit`, pfcq.`estimated_amount`,
    '由商品渠道报价记录恢复的仓库单渠道确认',
    COALESCE(pfcq.`confirmed_at`, pfcq.`gmt_updated`), pfcq.`confirmed_by`,
    b'0', pfcq.`confirmed_by`, pfcq.`confirmed_by`, NOW(), NOW()
FROM `product_forwarder_channel_quote` pfcq
JOIN `procurement_shipping_order_line` sol
  ON sol.`shipping_order_id` = pfcq.`source_shipping_order_id`
 AND sol.`id` = pfcq.`source_shipping_order_line_id`
 AND sol.`is_deleted` = b'0'
JOIN `procurement_shipping_order` so
  ON so.`id` = sol.`shipping_order_id`
 AND so.`is_deleted` = b'0'
LEFT JOIN `procurement_fulfillment_balance` balance
  ON balance.`purchase_order_item_site_id` = sol.`purchase_order_item_site_id`
 AND balance.`is_deleted` = b'0'
WHERE pfcq.`source_shipping_order_id` IS NOT NULL
  AND pfcq.`source_shipping_order_line_id` IS NOT NULL
  AND pfcq.`effective_status` = 'CURRENT'
  AND pfcq.`unit_price` IS NOT NULL
  AND pfcq.`unit_price` > 0
  AND pfcq.`is_deleted` = b'0'
  AND NOT EXISTS (
      SELECT 1
      FROM `procurement_purchase_order_logistics_quote_line` quote
      WHERE quote.`shipping_order_id` = sol.`shipping_order_id`
        AND quote.`purchase_order_item_site_id` = sol.`purchase_order_item_site_id`
        AND UPPER(COALESCE(quote.`forwarder_code`, '')) = UPPER(COALESCE(pfcq.`forwarder_code`, ''))
        AND UPPER(COALESCE(quote.`route_code`, '')) = UPPER(COALESCE(pfcq.`route_code`, ''))
        AND UPPER(COALESCE(quote.`service_code`, '')) = UPPER(COALESCE(pfcq.`service_code`, ''))
        AND quote.`is_deleted` = b'0'
  )
ORDER BY sol.`shipping_order_id`, sol.`id`, pfcq.`id`;

UPDATE `product_management_id_sequence`
SET `next_id` = GREATEST(`next_id`, @quote_channel_backfill_id + 1),
    `gmt_updated` = NOW()
WHERE `sequence_name` = 'procurement_purchase_order_logistics_quote_line';
