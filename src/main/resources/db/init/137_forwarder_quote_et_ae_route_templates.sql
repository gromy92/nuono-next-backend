-- Add ET UAE route templates so warehouse freight planning can evaluate AE lanes.
-- Source: ET易通天下物流报价-0604.xlsx / 阿联酋空海运双清.

INSERT INTO `forwarder_quote_route_template` (
    `id`, `route_code`, `quote_version_id`, `quote_version_code`, `forwarder_code`, `route_name`,
    `country`, `site_code`, `transport_mode`, `target_platform`, `delivery_city`, `destination_node`,
    `route_scope`, `active_for_purchase_order`, `source_file_name`, `source_sheet_or_page`,
    `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
)
VALUES
    (983006, 'ET-AE-AIR-WH-20260604', 904004, 'ET-20260604', 'ET',
     '易通阿联酋空运仓到仓 20260604', '阿联酋', 'AE', 'AIR', 'WAREHOUSE',
     '阿联酋仓', 'ET阿联酋仓', '头程到ET阿联酋仓', b'1',
     'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清',
     'AE air R6-R9', 'file',
     '采购单/货运计划评估用仓到仓路线；空运按KG计费重，最低10KG。', 1, 1),
    (983007, 'ET-AE-SEA-WH-20260604', 904004, 'ET-20260604', 'ET',
     '易通阿联酋海运仓到仓 20260604', '阿联酋', 'AE', 'SEA', 'WAREHOUSE',
     '阿联酋仓', 'ET阿联酋仓', '头程到ET阿联酋仓', b'1',
     'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清',
     'AE sea R12-R14', 'file',
     '采购单/货运计划评估用仓到仓路线；海运按CBM计费，最低0.2CBM。', 1, 1)
ON DUPLICATE KEY UPDATE
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `route_name` = VALUES(`route_name`),
    `country` = VALUES(`country`),
    `site_code` = VALUES(`site_code`),
    `transport_mode` = VALUES(`transport_mode`),
    `target_platform` = VALUES(`target_platform`),
    `delivery_city` = VALUES(`delivery_city`),
    `destination_node` = VALUES(`destination_node`),
    `route_scope` = VALUES(`route_scope`),
    `active_for_purchase_order` = VALUES(`active_for_purchase_order`),
    `source_file_name` = VALUES(`source_file_name`),
    `source_sheet_or_page` = VALUES(`source_sheet_or_page`),
    `source_row_or_locator` = VALUES(`source_row_or_locator`),
    `source_type` = VALUES(`source_type`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_route_template_segment` (
    `id`, `route_code`, `segment_no`, `segment_role`, `service_code`, `cost_policy`,
    `required`, `display_name`, `remark`, `created_by`, `updated_by`
)
VALUES
    (983110, 'ET-AE-AIR-WH-20260604', 1, 'HEADHAUL', 'ET-AE-AIR-WH-20260604',
     'ESTIMATE', b'1', '阿联酋空运仓到仓', '按KG计费重计算，体积重除6000。', 1, 1),
    (983111, 'ET-AE-SEA-WH-20260604', 1, 'HEADHAUL', 'ET-AE-SEA-WH-20260604',
     'ESTIMATE', b'1', '阿联酋海运仓到仓', '按CBM计费，海运最低0.2CBM。', 1, 1)
ON DUPLICATE KEY UPDATE
    `segment_role` = VALUES(`segment_role`),
    `service_code` = VALUES(`service_code`),
    `cost_policy` = VALUES(`cost_policy`),
    `required` = VALUES(`required`),
    `display_name` = VALUES(`display_name`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();
