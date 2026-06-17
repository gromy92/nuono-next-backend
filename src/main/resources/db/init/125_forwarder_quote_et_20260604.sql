-- ET forwarder quote 2026-06-04.
-- Source: ET易通天下物流报价-0604.xlsx. Scope: main air/sea warehouse-to-warehouse lanes,
-- small parcels, warehouse processing fees, last-mile fees, and prohibited item baseline.

DELETE FROM `forwarder_quote_numeric_adjustment_log`
WHERE `quote_version_id` IN (
    SELECT `id` FROM `forwarder_quote_version` WHERE `version_no` = 'ET-20260414'
);

DELETE FROM `forwarder_quote_numeric_adjustment`
WHERE `quote_version_id` IN (
    SELECT `id` FROM `forwarder_quote_version` WHERE `version_no` = 'ET-20260414'
);

DELETE FROM `forwarder_quote_prohibited_item` WHERE `quote_version_code` = 'ET-20260414';
DELETE FROM `forwarder_warehouse_processing_fee` WHERE `quote_version_code` = 'ET-20260414';
DELETE FROM `forwarder_quote_billing_rule` WHERE `quote_version_code` = 'ET-20260414';
DELETE FROM `forwarder_quote_transport_fee` WHERE `quote_version_code` = 'ET-20260414';
DELETE FROM `forwarder_quote_base_price` WHERE `quote_version_code` = 'ET-20260414';
DELETE FROM `forwarder_quote_cargo_category` WHERE `quote_version_code` = 'ET-20260414';
DELETE FROM `forwarder_quote_service_line` WHERE `quote_version_code` = 'ET-20260414';
DELETE FROM `forwarder_quote_version` WHERE `version_no` = 'ET-20260414';
DELETE FROM `quote_source_note` WHERE `bundle_id` = 901003;
DELETE FROM `quote_source_file` WHERE `bundle_id` = 901003;
DELETE FROM `quote_source_bundle` WHERE `id` = 901003;

INSERT INTO `forwarder_quote_reference_value` (`id`, `domain`, `code`, `display_name`, `description`, `sort_no`, `created_by`, `updated_by`)
VALUES
    (980208, 'billing_unit', 'TENTH_KG', '每0.1公斤', 'Small parcel billing per 0.1 KG.', 80, 1, 1),
    (980209, 'billing_unit', 'HALF_KG', '每0.5公斤', 'Small parcel billing per 0.5 KG.', 90, 1, 1),
    (980210, 'billing_unit', 'FIRST_5KG', '首重5公斤', 'First 5 KG tier billing unit.', 100, 1, 1),
    (980211, 'billing_unit', 'ADDITIONAL_KG', '续重公斤', 'Additional KG tier billing unit.', 110, 1, 1),
    (980212, 'billing_unit', 'PALLET', '托盘', 'Pallet billing unit.', 120, 1, 1),
    (980213, 'billing_unit', 'VEHICLE', '车', 'Dedicated vehicle billing unit.', 130, 1, 1),
    (980214, 'billing_unit', 'CBM_DAY', '立方/日', 'Cubic meter per day billing unit.', 140, 1, 1),
    (980215, 'billing_unit', 'TIMES', '次', 'Per operation or pickup time billing unit.', 150, 1, 1)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `description` = VALUES(`description`),
    `sort_no` = VALUES(`sort_no`),
    `active` = b'1',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder` (`id`, `name`, `alias`, `company_name`, `status`, `notes`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`)
VALUES
    (900003, '易通物流', 'ET', '广州易通天下物流', 'ACTIVE', '2026-06-04 新版报价，来源为 ET 易通天下物流 Excel，覆盖阿联酋、沙特国际物流、小包、海外仓和末端派送。', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `alias` = VALUES(`alias`),
    `company_name` = VALUES(`company_name`),
    `status` = VALUES(`status`),
    `notes` = VALUES(`notes`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `quote_source_bundle` (`id`, `forwarder_id`, `bundle_name`, `analysis_status`, `analysis_summary`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`)
VALUES
    (901004, 900003, '易通天下物流 2026-06-04 新版报价', 'STANDARDIZED', '已标准化新版国际物流、小包、海外仓、末端派送和禁发清单；旧 ET-20260414 数据删除，不保留旧报价版本。', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `forwarder_id` = VALUES(`forwarder_id`),
    `bundle_name` = VALUES(`bundle_name`),
    `analysis_status` = VALUES(`analysis_status`),
    `analysis_summary` = VALUES(`analysis_summary`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `quote_source_file` (`id`, `bundle_id`, `file_name`, `file_type`, `file_path`, `file_hash`, `page_count`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`)
VALUES
    (902004, 901004, 'ET易通天下物流报价-0604.xlsx', 'xlsx', '/Users/gromy/Documents/历史文件/ET易通天下物流报价-0604.xlsx', NULL, NULL, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `bundle_id` = VALUES(`bundle_id`),
    `file_name` = VALUES(`file_name`),
    `file_type` = VALUES(`file_type`),
    `file_path` = VALUES(`file_path`),
    `file_hash` = VALUES(`file_hash`),
    `page_count` = VALUES(`page_count`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `quote_source_note` (`id`, `bundle_id`, `note_type`, `source_channel`, `content`, `author_name`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`)
VALUES
    (903004, 901004, 'standardization_note', 'xlsx_extract', 'Excel 包含 Sheet1 变更摘要、自发货小包、海外仓收费标准、海外仓末端派送费、沙特空海运双清、阿联酋空海运双清、禁发货品清单。沙特和阿联酋专线报价自 2026/6/4 起生效，以货物到达 ET 国内仓时间为准。海外仓收费标准表注明自 2026/02/01 起生效。', 'Codex', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `bundle_id` = VALUES(`bundle_id`),
    `note_type` = VALUES(`note_type`),
    `source_channel` = VALUES(`source_channel`),
    `content` = VALUES(`content`),
    `author_name` = VALUES(`author_name`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_version` (`id`, `forwarder_id`, `bundle_id`, `version_no`, `effective_from`, `status`, `summary`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`)
VALUES
    (904004, 900003, 901004, 'ET-20260604', '2026-06-04', 'PUBLISHED', '易通天下 2026-06-04 新版报价：阿联酋/沙特仓到仓空海运、小包、海外仓、末端派送和禁发清单。', 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `forwarder_id` = VALUES(`forwarder_id`),
    `bundle_id` = VALUES(`bundle_id`),
    `version_no` = VALUES(`version_no`),
    `effective_from` = VALUES(`effective_from`),
    `status` = VALUES(`status`),
    `summary` = VALUES(`summary`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_service_line` (
    `id`, `quote_version_id`, `quote_version_code`, `forwarder_code`, `service_code`, `service_name`, `country`, `target_platform`, `delivery_city`, `destination_node`, `transport_mode`, `business_type`, `delivery_scope`, `origin_warehouse`, `departure_frequency`, `transit_time_text`, `transit_days_min`, `transit_days_max`, `active_for_mvp`, `source_file_name`, `source_sheet_or_page`, `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
) VALUES
    (910010, 904004, 'ET-20260604', 'ET', 'ET-AE-AIR-WH-20260604', '易通阿联酋空运仓到仓 20260604', '阿联酋', 'WAREHOUSE', '阿联酋仓', 'ET阿联酋仓', 'AIR', 'B2B大货', '仓到仓', '佛山仓/义乌仓', '周一和周四', '普货8-15天；敏货8-16天', 8, 16, b'1', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R6-R9', 'file', '可混装；带电产品内置电池不超100WH，中性包装。', 1, 1),
    (910011, 904004, 'ET-20260604', 'ET', 'ET-AE-SEA-WH-20260604', '易通阿联酋海运仓到仓 20260604', '阿联酋', 'WAREHOUSE', '阿联酋仓', 'ET阿联酋仓', 'SEA', 'B2B大货', '仓到仓', '佛山仓/义乌仓', 'A类一周一次；B/C类一个月2-3次', '35-50天', 35, 50, b'1', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R12-R14', 'file', '交义乌仓+100/CBM；C类特殊敏货1880起逐个确认。', 1, 1),
    (910012, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', '易通沙特快递仓到仓 20260604', '沙特', 'WAREHOUSE', '沙特仓', 'ET沙特仓', 'EXPRESS', '快递', '仓到仓', '佛山仓/义乌仓', '周一到周五', '7-12天', 7, 12, b'0', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R6-R16', 'file', '沙特快递档仅作报价台账，不作为采购物流空运/海运MVP推荐候选；混装，体积重L*W*H/5000；最低起运量10KG。', 1, 1),
    (910014, 904004, 'ET-20260604', 'ET', 'ET-SAU-SEA-WH-20260604', '易通沙特海运仓到仓 20260604', '沙特', 'WAREHOUSE', '沙特仓', 'ET沙特仓', 'SEA', 'B2B大货', '仓到仓', '佛山仓/义乌仓', 'A-D类周一到周五；E/F类每周一次；G类每月2-3次', '45-60天', 45, 60, b'1', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R22-R28', 'file', '沙特海运A-F按用户确认在0604基础上每CBM下调100；G类KG价不调整。交义乌仓+100/CBM。', 1, 1),
    (910015, 904004, 'ET-20260604', 'ET', 'ET-AE-SMALL-PARCEL-CN-20260604', '易通中国到阿联酋自发货小包 20260604', '阿联酋', 'WAREHOUSE', '阿联酋', '阿联酋本地小包网络', 'EXPRESS', '自发货小包', '中国到阿联酋小包', 'ET国内仓', NULL, '5-7天', 5, 7, b'1', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R3', 'file', '一票一件；申报价值不高于USD273/票；0<W≤30KG。', 1, 1),
    (910016, 904004, 'ET-20260604', 'ET', 'ET-AE-SMALL-PARCEL-LOCAL-20260604', '易通阿联酋起步本地小包 20260604', '阿联酋', 'WAREHOUSE', '阿联酋', '阿联酋本地小包网络', 'EXPRESS', '本地小包', '阿联酋本地派送', 'ET阿联酋仓', NULL, '5-7天', 5, 7, b'1', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R5', 'file', '首重5KG，续重1KG。', 1, 1),
    (910017, 904004, 'ET-20260604', 'ET', 'ET-SAU-SMALL-PARCEL-CN-20260604', '易通中国到沙特自发货小包 20260604', '沙特', 'WAREHOUSE', '沙特', '沙特本地小包网络', 'EXPRESS', '自发货小包', '中国到沙特小包', 'ET国内仓', NULL, '5-9天', 5, 9, b'1', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R9', 'file', '沙特国际小包申报价值不高于USD265/票；个人物品快件清关模式。', 1, 1),
    (910018, 904004, 'ET-20260604', 'ET', 'ET-SAU-SMALL-PARCEL-DXB-RUH-20260604', '易通迪拜到沙特利雅得小包 20260604', '沙特', 'WAREHOUSE', '利雅得/RUH', '沙特本地小包网络', 'EXPRESS', '跨境小包', '迪拜到沙特利雅得小包', 'ET迪拜仓', NULL, '5-9天', 5, 9, b'1', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R10', 'file', '按每0.5KG计费，另收操作费。', 1, 1),
    (910019, 904004, 'ET-20260604', 'ET', 'ET-SAU-SMALL-PARCEL-LOCAL-20260604', '易通沙特起步本地自发货小包 20260604', '沙特', 'WAREHOUSE', '沙特', '沙特本地小包网络', 'EXPRESS', '本地小包', '沙特本地派送', 'ET沙特仓', NULL, '5-9天', 5, 9, b'1', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R12', 'file', '首重5KG，续重1KG。', 1, 1),
    (910020, 904004, 'ET-20260604', 'ET', 'ET-WH-PROCESS-20260604', '易通沙特/迪拜海外仓处理服务 20260604', '沙特/阿联酋', 'WAREHOUSE', '沙特/迪拜仓', 'ET沙特/迪拜仓', 'WAREHOUSE', '海外仓', '仓储、上架、拣货、退货和操作服务', 'ET海外仓', NULL, NULL, NULL, NULL, b'1', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R5-R22', 'file', '海外仓收费标准表注明2026/02/01起生效；新版摘要调整大件/特大件规格和费用。', 1, 1),
    (910021, 904004, 'ET-20260604', 'ET', 'ET-LAST-MILE-20260604', '易通海外仓末端派送服务 20260604', '沙特/阿联酋', 'WAREHOUSE', '沙特/阿联酋', 'ET末端派送网络', 'EXPRESS', '末端派送', '平台仓送仓、2B末派、B2C末派', 'ET海外仓', NULL, '2B部分3-5天左右', 3, 5, b'1', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R3-R20', 'file', '末派只包含到门，不包含上楼和卸货费；偏远地址可能只能派送就近物流点。', 1, 1)
ON DUPLICATE KEY UPDATE
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `forwarder_code` = VALUES(`forwarder_code`),
    `service_code` = VALUES(`service_code`),
    `service_name` = VALUES(`service_name`),
    `country` = VALUES(`country`),
    `target_platform` = VALUES(`target_platform`),
    `delivery_city` = VALUES(`delivery_city`),
    `destination_node` = VALUES(`destination_node`),
    `transport_mode` = VALUES(`transport_mode`),
    `business_type` = VALUES(`business_type`),
    `delivery_scope` = VALUES(`delivery_scope`),
    `origin_warehouse` = VALUES(`origin_warehouse`),
    `departure_frequency` = VALUES(`departure_frequency`),
    `transit_time_text` = VALUES(`transit_time_text`),
    `transit_days_min` = VALUES(`transit_days_min`),
    `transit_days_max` = VALUES(`transit_days_max`),
    `active_for_mvp` = VALUES(`active_for_mvp`),
    `source_file_name` = VALUES(`source_file_name`),
    `source_sheet_or_page` = VALUES(`source_sheet_or_page`),
    `source_row_or_locator` = VALUES(`source_row_or_locator`),
    `source_type` = VALUES(`source_type`),
    `remark` = VALUES(`remark`),
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_warehouse_processing_fee` (
    `id`, `processing_fee_code`, `quote_version_id`, `quote_version_code`, `service_code`, `warehouse_country`, `warehouse_city`, `fee_name`, `fee_type`, `processing_scope`, `pricing_model`, `currency`, `amount`, `billing_unit`, `condition_text`, `min_charge`, `free_condition`, `target_platform`, `source_file_name`, `source_sheet_or_page`, `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
) VALUES
    (915013, 'ET-20260604-WH-STORAGE-NORMAL', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '产品储存仓储费', 'STORAGE', '产品储存（除破损仓以外）', 'PER_CBM_DAY', 'RMB', 8.0000, 'CBM_DAY', '入仓当日起按日计算；散件仓按SKU体积， 大货仓按箱体积。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R5', 'file', '报价有效期：2026/02/01号开始生效。', 1, 1),
    (915014, 'ET-20260604-WH-STORAGE-DAMAGED', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', 'ETRUH04Damaged/ETDXB04Damaged', '破损仓仓租费', 'STORAGE', '破损仓仓储', 'PER_CBM_DAY', 'RMB', 12.0000, 'CBM_DAY', '入仓当日起按日计算；散件仓按SKU体积， 大货仓按箱体积。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R6', 'file', NULL, 1, 1),
    (915015, 'ET-20260604-WH-INBOUND-PIECE-SMALL', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '散件仓按件上架-小件', 'INBOUND', '散件仓按件上架', 'PER_PIECE', 'RMB', 0.3000, 'PIECE', '小件：最长边≤50cm，次长边≤30cm，最短边≤20cm，或重量≤10kg。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R7', 'file', '散件仓不接收大件超大件。', 1, 1),
    (915016, 'ET-20260604-WH-INBOUND-PIECE-MEDIUM', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '散件仓按件上架-中件', 'INBOUND', '散件仓按件上架', 'PER_PIECE', 'RMB', 4.0000, 'PIECE', '中件：50cm≤最长边≤100cm，30cm≤次长边≤60cm，20cm≤最短边≤40cm，或10kg≤重量≤30kg。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R7', 'file', '散件仓不接收大件超大件。', 1, 1),
    (915017, 'ET-20260604-WH-INBOUND-BOX-SMALL-MEDIUM', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '整箱仓按箱上架-小件中件', 'INBOUND', '整箱仓按箱上架', 'PER_PIECE', 'RMB', 4.0000, 'BOX', '小件&中件。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R8', 'file', NULL, 1, 1),
    (915018, 'ET-20260604-WH-INBOUND-BOX-LARGE', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '整箱仓按箱上架-大件', 'INBOUND', '整箱仓按箱上架', 'PER_PIECE', 'RMB', 30.0000, 'BOX', '大件：100cm≤最长边≤200cm，60cm≤次长边≤100cm，40cm≤最短边≤60cm，或30kg≤重量≤100kg。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R8', 'file', NULL, 1, 1),
    (915019, 'ET-20260604-WH-INBOUND-PALLET-OVERSIZE', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '整箱仓上架-特大件', 'INBOUND', '整箱仓按托上架', 'PER_PIECE', 'RMB', 50.0000, 'PALLET', '特大件：200cm≤最长边，100cm≤次长边，60cm≤最短边，或100kg≤重量≤2500kg。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R8', 'file', NULL, 1, 1),
    (915020, 'ET-20260604-WH-PICK-PIECE-SMALL', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '按件拣货-小件', 'PICKING', 'FBA/FBN/SB2B按件拣货', 'PER_PIECE', 'RMB', 0.6000, 'PIECE', '每票拣货费用最低8元/次，超过最低消费标准时按实拣数量收。', 8.0000, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R9', 'file', NULL, 1, 1),
    (915021, 'ET-20260604-WH-PICK-PIECE-MEDIUM', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '按件拣货-中件', 'PICKING', 'FBA/FBN/SB2B按件拣货', 'PER_PIECE', 'RMB', 3.0000, 'PIECE', '每票拣货费用最低8元/次，Noon DirectShip 和 Amazon EasyShip 也按3元/件。', 8.0000, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R9', 'file', NULL, 1, 1),
    (915022, 'ET-20260604-WH-PICK-BOX-SMALL-MEDIUM', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '按箱拣货-小件中件', 'PICKING', '按箱拣货', 'PER_PIECE', 'RMB', 3.0000, 'BOX', '小件&中件。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R10', 'file', NULL, 1, 1),
    (915023, 'ET-20260604-WH-PICK-BOX-LARGE', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '按箱拣货-大件', 'PICKING', '按箱拣货', 'PER_PIECE', 'RMB', 30.0000, 'BOX', '大件。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R10', 'file', NULL, 1, 1),
    (915024, 'ET-20260604-WH-PICK-PALLET-OVERSIZE', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '按托拣货-特大件', 'PICKING', '按托拣货', 'PER_PIECE', 'RMB', 50.0000, 'PALLET', '特大件。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R10', 'file', NULL, 1, 1),
    (915025, 'ET-20260604-WH-RETURN-INBOUND-SMALL', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '平台退货按件上架-小件', 'RETURN', '平台退货按件上架', 'PER_PIECE', 'RMB', 0.3000, 'PIECE', '包括初检、数量盘点、测量体积、测量重量、上架。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R11', 'file', NULL, 1, 1),
    (915026, 'ET-20260604-WH-RETURN-INBOUND-MEDIUM', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '平台退货按件上架-中件', 'RETURN', '平台退货按件上架', 'PER_PIECE', 'RMB', 4.0000, 'PIECE', '包括初检、数量盘点、测量体积、测量重量、上架。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R11', 'file', NULL, 1, 1),
    (915027, 'ET-20260604-WH-RETURN-INBOUND-LARGE', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '平台退货按件上架-大件', 'RETURN', '平台退货按件上架', 'PER_PIECE', 'RMB', 30.0000, 'PIECE', '包括初检、数量盘点、测量体积、测量重量、上架。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R11', 'file', NULL, 1, 1),
    (915028, 'ET-20260604-WH-RETURN-PROCESS', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '平台退货处理', 'RETURN', '平台退货处理', 'PER_PIECE', 'RMB', 1.0000, 'PIECE', '包括平台退货外观初检、良品与破损品区分、盘点统计；上架、换条码、换包装另计。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R12', 'file', NULL, 1, 1),
    (915029, 'ET-20260604-WH-RETURN-PICKUP', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '退货提货服务', 'RETURN_PICKUP', '平台仓退货提货', 'PER_KG', 'RMB', 3.0000, 'KG', '平台仓提货费用，其他仓库另询。', 100.0000, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R13', 'file', '最低消费100元/次。', 1, 1),
    (915030, 'ET-20260604-WH-LABEL', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '贴标/换条码', 'LABEL', '产品核对、条码打印与粘贴', 'PER_PIECE', 'RMB', 1.0000, 'PIECE', '贴标/换条码。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R14', 'file', NULL, 1, 1),
    (915031, 'ET-20260604-WH-STOCKTAKE', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '盘点', 'CHECK', '盘点', 'PER_PIECE', 'RMB', 0.6000, 'PIECE', '件（pcs）。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R15', 'file', NULL, 1, 1),
    (915032, 'ET-20260604-WH-ASSEMBLY', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '组装费', 'ASSEMBLY', '组装', 'PER_PIECE', 'RMB', 2.0000, 'PIECE', '件（pcs）。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R16', 'file', NULL, 1, 1),
    (915033, 'ET-20260604-WH-PHOTO-VIDEO', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '拍照费和录视频', 'CHECK', '拍照和录视频', 'PER_PIECE', 'RMB', 5.0000, 'PIECE', '5元/pcs。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R17', 'file', NULL, 1, 1),
    (915034, 'ET-20260604-WH-CHECK', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '核查产品情况', 'CHECK', '检查颜色、货物情况等', 'PER_PIECE', 'RMB', 5.0000, 'PIECE', '包括但不限于检查产品颜色、货物情况。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R18', 'file', NULL, 1, 1),
    (915035, 'ET-20260604-WH-REPACK-WITH-CARTON', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '换包装含纸箱', 'PACKAGING', '换包装含纸箱', 'PER_PIECE', 'RMB', 20.0000, 'BOX', '纸箱尺寸60*40*40CM，含包装材料和人工。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R19', 'file', NULL, 1, 1),
    (915036, 'ET-20260604-WH-REPACK-WITHOUT-CARTON', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '换包装不含纸箱', 'PACKAGING', '换包装不含纸箱', 'PER_PIECE', 'RMB', 5.0000, 'BOX', '不含包装。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R20', 'file', NULL, 1, 1),
    (915037, 'ET-20260604-WH-PALLET', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', '沙特/阿联酋', '沙特/迪拜仓', '托盘费', 'PALLET', '打托人工、包装膜、透明胶带、托盘材料费', 'PER_PIECE', 'RMB', 80.0000, 'PALLET', '含打托人工费、包装膜、透明胶带、托盘材料费等。', NULL, NULL, 'WAREHOUSE', 'ET易通天下物流报价-0604.xlsx', '海外仓收费标准', 'R21', 'file', NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `processing_fee_code` = VALUES(`processing_fee_code`),
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `service_code` = VALUES(`service_code`),
    `warehouse_country` = VALUES(`warehouse_country`),
    `warehouse_city` = VALUES(`warehouse_city`),
    `fee_name` = VALUES(`fee_name`),
    `fee_type` = VALUES(`fee_type`),
    `processing_scope` = VALUES(`processing_scope`),
    `pricing_model` = VALUES(`pricing_model`),
    `currency` = VALUES(`currency`),
    `amount` = VALUES(`amount`),
    `billing_unit` = VALUES(`billing_unit`),
    `condition_text` = VALUES(`condition_text`),
    `min_charge` = VALUES(`min_charge`),
    `free_condition` = VALUES(`free_condition`),
    `target_platform` = VALUES(`target_platform`),
    `source_file_name` = VALUES(`source_file_name`),
    `source_sheet_or_page` = VALUES(`source_sheet_or_page`),
    `source_row_or_locator` = VALUES(`source_row_or_locator`),
    `source_type` = VALUES(`source_type`),
    `remark` = VALUES(`remark`),
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_billing_rule` (
    `id`, `rule_code`, `quote_version_id`, `quote_version_code`, `service_code`, `cargo_category_code`, `rule_type`, `severity`, `condition_text`, `structured_field`, `operator`, `value`, `unit`, `action`, `volume_divisor`, `sea_weight_ratio`, `rounding_rule`, `max_length_cm`, `max_weight_kg`, `source_file_name`, `source_sheet_or_page`, `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
) VALUES
    (914048, 'ET-20260604-RULE-AE-AIR-BILLING', 904004, 'ET-20260604', 'ET-AE-AIR-WH-20260604', NULL, 'BILLING', 'SOFT', '阿联酋空运可混装，体积重L*W*H/6000，最低起运量10KG，不足10KG按10KG计费。', 'volume_divisor', '=', '6000', 'CM/KG', '按规则计费', 6000.0000, NULL, '不足10KG按10KG；不足1KG向上取整', NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R6C5', 'file', NULL, 1, 1),
    (914049, 'ET-20260604-RULE-AE-SEA-BILLING', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', NULL, 'BILLING', 'SOFT', '阿联酋海运混装，8KG≤单箱重量≤30KG；最小起运量0.2CBM；交义乌仓+100/CBM。', 'min_billable_unit', '=', '0.2', 'CBM', '按规则计费', NULL, '1CBM=300KG', '保留2位小数', NULL, 30.0000, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R12C5', 'file', NULL, 1, 1),
    (914050, 'ET-20260604-RULE-SAU-EXPRESS-BILLING', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', NULL, 'BILLING', 'SOFT', '沙特快递混装，体积重L*W*H/5000，最低起运量10KG，不足10KG按10KG。', 'volume_divisor', '=', '5000', 'CM/KG', '按规则计费', 5000.0000, NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R6C5', 'file', NULL, 1, 1),
    (914053, 'ET-20260604-RULE-SAU-SEA-MIX', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', NULL, 'PACKING', 'HARD', '沙特海运每箱3个品名以内，超过按混装类别；不同类别不能混装，跨类别混装按高价类别；各类别体积分别汇总计算。', 'category_mix', '=', 'not_allowed', NULL, '拒收或按高价类别', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R22C5', 'file', NULL, 1, 1),
    (914054, 'ET-20260604-RULE-SAU-SEA-BILLING', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', NULL, 'BILLING', 'SOFT', '沙特海运8KG≤单箱重量≤30KG；最小起运量0.2CBM；交义乌仓+100/CBM。', 'min_billable_unit', '=', '0.2', 'CBM', '按规则计费', NULL, '1CBM=300KG', '保留2位小数', NULL, 30.0000, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R22C5', 'file', NULL, 1, 1),
    (914055, 'ET-20260604-RULE-SEA-CUSTOMS', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', NULL, 'DECLARATION', 'SOFT', '海运3CBM起接受单独报关：报关费RMB450/票+续页RMB50/页。', 'declaration_min_cbm', '>=', '3', 'CBM', '加费', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R15', 'file', '适用于国际物流海运。', 1, 1),
    (914056, 'ET-20260604-RULE-SAU-MADE-IN-CHINA', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', NULL, 'LABEL', 'HARD', '沙特所有进口货物必须有 Made in China 标志；服装、包包需要车缝水洗标；手表与眼镜必须刻印 Made in China。', 'label_required', '=', 'Made in China', NULL, '拒收或整改', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '自发货小包/阿联酋空海运双清', '自发货小包R13；阿联酋R15', 'file', NULL, 1, 1),
    (914057, 'ET-20260604-RULE-SAU-PLUG-VOLTAGE', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', NULL, 'COMPLIANCE', 'HARD', '沙特进口电器插头必须是英式三脚插头，输入电压必须包括127V或220V/380V，频率60HZ。', 'plug_type', '=', 'UK_3_PIN', NULL, '拒收或整改', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R15', 'file', NULL, 1, 1),
    (914058, 'ET-20260604-RULE-SENSITIVE-DOCS', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', NULL, 'COMPLIANCE', 'INQUIRY', '带电池产品、液体、粉末等必须提供海运鉴定报告、MSDS报告；空运带电池产品按航司要求贴电池标签。', 'sensitive_docs_required', '=', 'MSDS_OR_SEA_TRANSPORT_REPORT', NULL, '人工确认', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R15', 'file', NULL, 1, 1),
    (914059, 'ET-20260604-RULE-SMALL-PARCEL-LIMIT', 904004, 'ET-20260604', 'ET-AE-SMALL-PARCEL-CN-20260604', NULL, 'SIZE_WEIGHT', 'HARD', '小包一票一件；计费重量限重0<W≤30KG；纸箱尺寸限制120CM*80CM*80CM，单边长不超过120CM。', 'max_weight_kg', '<=', '30', 'KG', '拒收或整改', NULL, NULL, NULL, 120.0000, 30.0000, 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R13-R14', 'file', '阿联酋小包体积重/6000；沙特小包体积重/5000。', 1, 1),
    (914060, 'ET-20260604-RULE-SMALL-PARCEL-DECLARED-VALUE', 904004, 'ET-20260604', 'ET-AE-SMALL-PARCEL-CN-20260604', NULL, 'DECLARATION', 'HARD', '阿联酋直发小包不接受申报价值高于USD273/票；沙特国际小包不接受申报价值高于USD265/票。', 'declared_value_usd', '<=', '273', 'USD', '超出改走大货Cargo或拒收', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R13', 'file', NULL, 1, 1),
    (914061, 'ET-20260604-RULE-WH-SIZE-STANDARD', 904004, 'ET-20260604', 'ET-WH-PROCESS-20260604', NULL, 'SIZE_WEIGHT', 'INFO', '海外仓尺寸标准：小件最长边≤50cm/重量≤10kg；中件最长边50-100cm或10-30kg；大件最长边100-200cm或30-100kg；特大件最长边≥200cm或100-2500kg。', 'warehouse_size_standard', '=', '20260604', NULL, '提示', NULL, NULL, NULL, 200.0000, 2500.0000, 'ET易通天下物流报价-0604.xlsx', 'Sheet1/海外仓收费标准', 'Sheet1 R5; 仓储R7', 'file', NULL, 1, 1),
    (914062, 'ET-20260604-RULE-LAST-MILE-FBA-PALLET', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'PACKING', 'SOFT', 'FBA送仓单箱重量不超过30KG；需打托盘，托盘尺寸120X100X160CM约2CBM，每托重量不超过750KG；托盘数量按体积/1.92向上取整。', 'pallet_required', '=', 'true', NULL, '按规则处理', NULL, NULL, '体积/1.92向上取整', NULL, 750.0000, 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R3C4', 'file', NULL, 1, 1),
    (914063, 'ET-20260604-RULE-LAST-MILE-2B', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'DELIVERY', 'INFO', '线下2B末派只包含到门，不包含上楼和卸货费；偏远地址只能派送就近物流点，客户需要上门自提。', 'delivery_scope', '=', 'door_only', NULL, '提示', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R20', 'file', NULL, 1, 1),
    (914064, 'ET-20260604-RULE-WAR-SURCHARGE', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', NULL, 'SURCHARGE', 'INFO', '受战争不可抗力影响，物流时效不做承诺；船公司可能加收GRI/WRS等附加费用，ET将相应加收。', 'war_surcharge_possible', '=', 'true', NULL, '提示', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清/沙特空海运双清', 'AE R16; SA R29', 'file', NULL, 1, 1),
    (914065, 'ET-20260604-RULE-SETTLEMENT', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', NULL, 'SETTLEMENT', 'SOFT', '未签署合同的国际物流费用按票结算：货到ET海外仓后、派送前支付，最长期限5天；第6天起按3‰/日收取违约金和仓租。仓储费和服务费默认按月结算，每月10号前支付。', 'settlement_grace_days', '=', '5', 'DAY', '加费', NULL, NULL, NULL, NULL, NULL, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清/海外仓收费标准', 'AE R15; WH R22', 'file', NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `rule_code` = VALUES(`rule_code`),
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `service_code` = VALUES(`service_code`),
    `cargo_category_code` = VALUES(`cargo_category_code`),
    `rule_type` = VALUES(`rule_type`),
    `severity` = VALUES(`severity`),
    `condition_text` = VALUES(`condition_text`),
    `structured_field` = VALUES(`structured_field`),
    `operator` = VALUES(`operator`),
    `value` = VALUES(`value`),
    `unit` = VALUES(`unit`),
    `action` = VALUES(`action`),
    `volume_divisor` = VALUES(`volume_divisor`),
    `sea_weight_ratio` = VALUES(`sea_weight_ratio`),
    `rounding_rule` = VALUES(`rounding_rule`),
    `max_length_cm` = VALUES(`max_length_cm`),
    `max_weight_kg` = VALUES(`max_weight_kg`),
    `source_file_name` = VALUES(`source_file_name`),
    `source_sheet_or_page` = VALUES(`source_sheet_or_page`),
    `source_row_or_locator` = VALUES(`source_row_or_locator`),
    `source_type` = VALUES(`source_type`),
    `remark` = VALUES(`remark`),
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_prohibited_item` (
    `id`, `restriction_code`, `quote_version_id`, `quote_version_code`, `country`, `service_code`, `item_category`, `item_name_cn`, `item_name_en`, `severity`, `handling_action`, `source_file_name`, `source_sheet_or_page`, `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
) VALUES
    (916234, 'ET-20260604-PROHIBITED-001', 904004, 'ET-20260604', '中东', NULL, '成人/军事/敏感文化', '色情用品、成人用品、军火器械及仿制品、军装等', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R3', 'file', '含性玩具、毒药、男性增强补品、过于暴露或透明镂空服装、带沙特徽章盾徽物品等。', 1, 1),
    (916235, 'ET-20260604-PROHIBITED-002', 904004, 'ET-20260604', '中东', NULL, '监视/窃听/定位', '隐蔽摄像录音、GPS定位器、无人机、带摄像头运动眼镜等', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R4', 'file', '可被用于监视窃听、隐蔽拍摄、定位指示及其他间谍活动的物品。', 1, 1),
    (916236, 'ET-20260604-PROHIBITED-003', 904004, 'ET-20260604', '中东', NULL, '无线通讯终端', '对讲机、可视电话、信号增强器等无线通讯终端设备', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R8', 'file', NULL, 1, 1),
    (916237, 'ET-20260604-PROHIBITED-004', 904004, 'ET-20260604', '中东', NULL, '武器配件', '攻击用途物品，如电蚊拍、飞镖等', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R9', 'file', NULL, 1, 1),
    (916238, 'ET-20260604-PROHIBITED-005', 904004, 'ET-20260604', '中东', NULL, '探测/激光/电击', '雷达探测器、金属探测器、激光产品、电击棒、纹身机', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R10', 'file', NULL, 1, 1),
    (916239, 'ET-20260604-PROHIBITED-006', 904004, 'ET-20260604', '中东', NULL, '玩具遥控', '玩具遥控汽车或飞机', NULL, 'INQUIRY', 'ASK_QUOTE', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R11', 'file', '原文标注另询。', 1, 1),
    (916240, 'ET-20260604-PROHIBITED-007', 904004, 'ET-20260604', '中东', NULL, '赌博用品', '赌博用品、棋牌、扑克牌', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R12', 'file', NULL, 1, 1),
    (916241, 'ET-20260604-PROHIBITED-008', 904004, 'ET-20260604', '中东', NULL, '以色列来源/图案', '原产地或生产商为以色列、带六角星图案商品', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R13', 'file', NULL, 1, 1),
    (916242, 'ET-20260604-PROHIBITED-009', 904004, 'ET-20260604', '中东', NULL, '以色列相关', '以色列产、部分以色列产或从以色列中转的商品', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R14', 'file', NULL, 1, 1),
    (916243, 'ET-20260604-PROHIBITED-010', 904004, 'ET-20260604', '中东', NULL, '数据存储/智能卡', '带内容的优盘及其他闪存、智能卡', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R15', 'file', NULL, 1, 1),
    (916244, 'ET-20260604-PROHIBITED-011', 904004, 'ET-20260604', '中东', NULL, '仿牌', '仿牌商品（仿冒品）', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R16', 'file', NULL, 1, 1),
    (916245, 'ET-20260604-PROHIBITED-012', 904004, 'ET-20260604', '中东', NULL, '管制刀具', '菜刀、餐刀、水果刀、叉等管制刀具', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R17', 'file', NULL, 1, 1),
    (916246, 'ET-20260604-PROHIBITED-013', 904004, 'ET-20260604', '中东', NULL, '名人肖像/姓名', '带名人照片或名字的商品', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R18', 'file', NULL, 1, 1),
    (916247, 'ET-20260604-PROHIBITED-014', 904004, 'ET-20260604', '中东', NULL, '激光眼镜', '带孔的激光眼镜', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R19', 'file', NULL, 1, 1),
    (916248, 'ET-20260604-PROHIBITED-015', 904004, 'ET-20260604', '中东', NULL, '烟草/电子烟', '电子烟、打火机、烟具、烟草广告', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R20', 'file', NULL, 1, 1),
    (916249, 'ET-20260604-PROHIBITED-016', 904004, 'ET-20260604', '中东', NULL, '光盘/电子游戏内容', '光盘、电子游戏类内容、视频游戏相关商品', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R21', 'file', NULL, 1, 1),
    (916250, 'ET-20260604-PROHIBITED-017', 904004, 'ET-20260604', '中东', NULL, '平衡车/面具', '平衡车、全遮面或半遮面面具', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R22', 'file', NULL, 1, 1),
    (916251, 'ET-20260604-PROHIBITED-018', 904004, 'ET-20260604', '中东', NULL, '敏感图案/文字', '骷髅、暴露血腥、宗教、猪、麦加麦地那或沙特皇家图片等敏感内容', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R23', 'file', '包括抵触穆斯林或沙特文化的图案、文字、日历等。', 1, 1),
    (916252, 'ET-20260604-PROHIBITED-019', 904004, 'ET-20260604', '中东', NULL, '草药/酒精/药品/食品', '草药、花草茶、香料、含酒精饮料、药品、食品、毒品', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R24', 'file', NULL, 1, 1),
    (916253, 'ET-20260604-PROHIBITED-020', 904004, 'ET-20260604', '中东', NULL, '变压器', '变压器', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R25', 'file', NULL, 1, 1),
    (916254, 'ET-20260604-PROHIBITED-021', 904004, 'ET-20260604', '中东', NULL, '高风险材质/贵重物', '古董、石棉、皮草动物羽毛、珠宝、贵重金属或石头、土壤样本', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R26', 'file', NULL, 1, 1),
    (916255, 'ET-20260604-PROHIBITED-022', 904004, 'ET-20260604', '中东', NULL, '智能手环', '带摄像头或SIM卡的智能手环', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R27', 'file', NULL, 1, 1),
    (916256, 'ET-20260604-PROHIBITED-023', 904004, 'ET-20260604', '中东', NULL, '假货/未授权品牌', '假货、仿牌、未获品牌持有人或海关出口授权的品牌商品', NULL, 'PROHIBITED', 'REJECT', 'ET易通天下物流报价-0604.xlsx', '禁发货品清单', 'R28', 'file', '产品上有品牌logo视为品牌。', 1, 1)
ON DUPLICATE KEY UPDATE
    `restriction_code` = VALUES(`restriction_code`),
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `country` = VALUES(`country`),
    `service_code` = VALUES(`service_code`),
    `item_category` = VALUES(`item_category`),
    `item_name_cn` = VALUES(`item_name_cn`),
    `item_name_en` = VALUES(`item_name_en`),
    `severity` = VALUES(`severity`),
    `handling_action` = VALUES(`handling_action`),
    `source_file_name` = VALUES(`source_file_name`),
    `source_sheet_or_page` = VALUES(`source_sheet_or_page`),
    `source_row_or_locator` = VALUES(`source_row_or_locator`),
    `source_type` = VALUES(`source_type`),
    `remark` = VALUES(`remark`),
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_cargo_category` (
    `id`, `quote_version_id`, `quote_version_code`, `forwarder_code`, `service_code`, `cargo_category_code`, `cargo_category_name`, `source_category_name`, `category_level_1`, `category_level_2`, `product_examples`, `product_keywords`, `electric_type`, `sensitive_tags`, `packing_policy`, `manual_confirm_required`, `match_priority`, `source_file_name`, `source_sheet_or_page`, `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
) VALUES
    (911052, 904004, 'ET-20260604', 'ET', 'ET-AE-AIR-WH-20260604', 'ET-AE-AIR-WH-20260604-CAT-A', '阿联酋空运A类', 'A类 无牌无电无磁无液体无带木普货', '空运分类', '普货', '纺织品、家具家电、手机配件、厨具、灯具、汽车用品配件、无牌无油五金配件等', '无牌，无电，无磁，无液体，无带木，普货', '无电', '普货', '可混装', b'0', 1, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R6', 'file', NULL, 1, 1),
    (911053, 904004, 'ET-20260604', 'ET', 'ET-AE-AIR-WH-20260604', 'ET-AE-AIR-WH-20260604-CAT-B', '阿联酋空运B类', 'B类 无牌带电带木带磁', '空运分类', '无牌带电带木带磁', '耳机、音箱、手机配件、跑步机、小型带电机家电、无牌机器、小型器械、带电灯具、按摩仪、圆珠笔水性笔等', '无牌，带电，带木，带磁', '带电', '带木，带磁', '可混装', b'1', 2, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R7', 'file', '内置电池不超100WH，中性包装。', 1, 1),
    (911054, 904004, 'ET-20260604', 'ET', 'ET-AE-AIR-WH-20260604', 'ET-AE-AIR-WH-20260604-CAT-C', '阿联酋空运C类', 'C类 品牌无电/品牌带电', '空运分类', '品牌类', '不带液体粉末牌子产品、不超功率带牌带电产品、美容仪器、监控摄像头类', '品牌，带牌，美容仪器，监控摄像头', '不确定', '品牌，监控', '可混装', b'1', 3, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R8', 'file', NULL, 1, 1),
    (911055, 904004, 'ET-20260604', 'ET', 'ET-AE-AIR-WH-20260604', 'ET-AE-AIR-WH-20260604-CAT-D', '阿联酋空运D类', 'D类 液体/粉末/品牌化妆品/护肤品/膏体/移动电源/食品/医疗用品', '空运分类', '敏感货', '液体、粉末、品牌化妆品、护肤品、膏体、移动电源、食品、医疗用品配件等', '液体，粉末，化妆品，护肤品，膏体，移动电源，食品，医疗用品', '不确定', '液体，粉末，化妆品，食品，医疗用品', '可混装', b'1', 4, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R9', 'file', NULL, 1, 1),
    (911056, 904004, 'ET-20260604', 'ET', 'ET-AE-SEA-WH-20260604', 'ET-AE-SEA-WH-20260604-CAT-A', '阿联酋海运A类', 'A类 混装普货非品牌无电', '海运分类', '普货非品牌无电', '纺织品、家具家电、手机配件、厨具、灯具、汽车用品配件、无牌按摩仪器类', '混装，普货，非品牌，无电', '无电', '普货', '混装；8KG≤单箱重量≤30KG', b'0', 1, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R12', 'file', NULL, 1, 1),
    (911057, 904004, 'ET-20260604', 'ET', 'ET-AE-SEA-WH-20260604', 'ET-AE-SEA-WH-20260604-CAT-B', '阿联酋海运B类', 'B类 敏货', '海运分类', '敏货', '大功率电器、大型机械、护肤品、彩妆、化工用品、液体/膏体、普通牌子、内置电池家电灯具、医疗用品、监控、医疗器械', '敏货，大功率电器，大型机械，护肤品，彩妆，化工，液体，膏体，品牌，电池，医疗，监控', '带电', '液体，膏体，品牌，医疗，监控', '混装；8KG≤单箱重量≤30KG', b'1', 2, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R13', 'file', NULL, 1, 1),
    (911058, 904004, 'ET-20260604', 'ET', 'ET-AE-SEA-WH-20260604', 'ET-AE-SEA-WH-20260604-CAT-C', '阿联酋海运C类', 'C类 特殊敏货/粉末/食品/大牌/香水/充电宝纯电池', '海运分类', '特殊敏货', '特殊敏货、粉末、食品、大牌、香水、充电宝、纯电池等', '特殊敏货，粉末，食品，大牌，香水，充电宝，纯电池', '电池', '粉末，食品，大牌，香水，纯电池', '混装；8KG≤单箱重量≤30KG', b'1', 3, 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R14', 'file', '逐个确认；1880起。', 1, 1),
    (911059, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-A', '沙特快递A类', 'A类 混装普货类', '快递分类', '混装普货类', '无牌筋膜枪、牙线、舌苔清洁器、婴儿奶瓶、安抚奶嘴等', '混装，普货，筋膜枪，牙线，婴儿奶瓶，安抚奶嘴', '不确定', '普货', '混装', b'1', 1, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R6', 'file', '除禁发限发产品外，发之前先与ET业务人员确认。', 1, 1),
    (911060, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-BC', '沙特快递BC类', 'BC类 普通品牌', '快递分类', '普通品牌', 'WiFi、固体香薰、除螨仪、除草机、普通牌子', '普通品牌，WiFi，香薰，除螨仪，除草机', '不确定', '品牌', '混装', b'1', 2, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R7', 'file', NULL, 1, 1),
    (911061, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-D', '沙特快递D类', 'D类 凝胶/膏体/液体/化妆品/隐形眼镜/粉末', '快递分类', '凝胶膏体液体粉末', '凝胶、膏体、液体、婴儿湿巾、粉底液/霜、睫毛膏、眼影、口红、唇膏、面霜、面膜、隐形眼镜、粉末等', '凝胶，膏体，液体，湿巾，粉底，睫毛膏，眼影，口红，面霜，面膜，隐形眼镜，粉末', '不确定', '液体，粉末，化妆品，隐形眼镜', '混装', b'1', 3, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R8', 'file', NULL, 1, 1),
    (911062, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-E', '沙特快递E类', 'E类 医疗用品+无牌眼镜', '快递分类', '医疗用品无牌眼镜', '医疗用品、无牌眼镜', '医疗用品，无牌眼镜', '不确定', '医疗用品，眼镜', '混装', b'1', 4, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R9', 'file', '逐个确认。', 1, 1),
    (911063, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-GF', '沙特快递GF类', 'GF类 大牌+食品', '快递分类', '大牌食品', '耐克、阿迪达斯、兰蔻等大牌，食品', '大牌，食品，耐克，阿迪达斯，兰蔻', '不确定', '大牌，食品', '混装', b'1', 5, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R10', 'file', '99+，按起步价记录。', 1, 1),
    (911064, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-H', '沙特快递H类', 'H类 扫码枪+电池', '快递分类', '扫码枪电池', '扫码枪、电池', '扫码枪，电池', '带电', '电池', '混装', b'1', 6, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R11', 'file', NULL, 1, 1),
    (911065, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-K', '沙特快递K类', 'K类 药品类', '快递分类', '药品类', '药品类', '药品', '不确定', '药品', '混装', b'1', 7, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R12', 'file', NULL, 1, 1),
    (911066, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-Q', '沙特快递Q类', 'Q类 胶囊类', '快递分类', '胶囊类', '胶囊类', '胶囊', '不确定', '胶囊', '混装', b'1', 8, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R13', 'file', NULL, 1, 1),
    (911067, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-W', '沙特快递W类', 'W类 奢侈品大牌手表饰品眼镜', '快递分类', '奢侈品大牌', 'LV、香奈儿等奢侈品大牌手表、饰品、眼镜', '奢侈品，大牌，手表，饰品，眼镜，LV，香奈儿', '不确定', '奢侈品，大牌，手表，眼镜', '混装', b'1', 9, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R14', 'file', NULL, 1, 1),
    (911068, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-L', '沙特快递L类', 'L类 奢侈品爱马仕劳力士品牌', '快递分类', '奢侈品品牌', '爱马仕、劳力士品牌', '奢侈品，爱马仕，劳力士', '不确定', '奢侈品，大牌', '混装', b'1', 10, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R15', 'file', NULL, 1, 1),
    (911069, 904004, 'ET-20260604', 'ET', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-PHONE', '沙特快递手机/平板', '手机/平板', '快递分类', '手机平板', '手机、平板', '手机，平板', '带电', '手机，平板', '按台', b'1', 11, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R16', 'file', '按台计价。', 1, 1),
    (911072, 904004, 'ET-20260604', 'ET', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-A', '沙特海运A类', 'A类', '海运分类', '纺织鞋包家居包材类', '纺织品、鞋子、箱包、手机配件、家装家居、贴纸、挂钟、摆件、包材等', '纺织品，鞋子，箱包，手机配件，家居，包材', '无电', '普货', '每箱3个品名以内；不同类别不能混装', b'0', 1, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R22', 'file', '纺织品必须带 Made in China 水洗唛。', 1, 1),
    (911073, 904004, 'ET-20260604', 'ET', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-B', '沙特海运B类', 'B类', '海运分类', '厨具工具玩具3C饰品汽配类', '厨具用具、手动工具、运动器材、乐器、文具、无电无插座玩具、3C配件、饰品、汽车配类等', '厨具，手动工具，运动器材，乐器，文具，玩具，3C，饰品，汽配', '无电', '食品接触类，饰品', '每箱3个品名以内；不同类别不能混装', b'0', 2, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R23', 'file', NULL, 1, 1),
    (911074, 904004, 'ET-20260604', 'ET', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-C', '沙特海运C类', 'C类', '海运分类', '英规三插小家电内置电玩具卫浴清洁类', '吹风筒、加湿器、空气炸锅、咖啡机、内置电玩具、卫浴清洁类等', '英规三插，小家电，内置电，玩具，卫浴，清洁', '带电/插电', '小家电，内置电，清洁液', '每箱3个品名以内；不同类别不能混装', b'1', 3, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R24', 'file', NULL, 1, 1),
    (911075, 904004, 'ET-20260604', 'ET', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-D', '沙特海运D类', 'D类', '海运分类', '灯具大家电办公器材防护类', '灯具、大家电、办公器材、防护类产品等', '灯具，大家电，办公器材，防护', '不确定', '灯具，大家电', '每箱3个品名以内；不同类别不能混装', b'0', 4, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R25', 'file', NULL, 1, 1),
    (911076, 904004, 'ET-20260604', 'ET', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-E', '沙特海运E混装类', 'E混装 A/B/C/D混装', '海运分类', 'A/B/C/D混装', 'A/B/C/D混装，发之前先与ET业务人员确认', '混装，A类，B类，C类，D类', '不确定', '混装', '跨类别混装按高价类别', b'1', 5, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R26', 'file', NULL, 1, 1),
    (911077, 904004, 'ET-20260604', 'ET', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-F', '沙特海运F类', 'F类 大型机械/建材/按摩椅/开关插座', '海运分类', '大型机械建材按摩椅开关插座类', '大型机械、建材类、按摩椅、开关、插座等', '大型机械，建材，按摩椅，开关，插座', '不确定', '大型机械，建材，按摩椅', '每箱3个品名以内；不同类别不能混装', b'1', 6, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R27', 'file', '1900+起，需提供产品铭牌。', 1, 1),
    (911078, 904004, 'ET-20260604', 'ET', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-G', '沙特海运G类食品化妆品', 'G类 食品/化妆品', '海运分类', '食品化妆品类', '调料、饮料、干货、粮油米面；无酒精漱口水、牙膏、粉底、睫毛膏、眼影、口红、唇膏、面霜、面膜、肥皂、沐浴露、洗发水等', '食品，化妆品，调料，饮料，干货，粮油，牙膏，口红，面膜，洗发水', '不确定', '食品，化妆品，液体，膏体', '食品类起运量3CBM', b'1', 7, 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R28', 'file', '猪肉制品和酒精不允许；需确认成分和包装。', 1, 1),
    (911079, 904004, 'ET-20260604', 'ET', 'ET-AE-SMALL-PARCEL-CN-20260604', 'ET-AE-SMALL-PARCEL-CN-20260604-CAT-STD', '阿联酋自发货小包', '中国到阿联酋自发货小包', '小包分类', '阿联酋小包', '一票一件，阿联酋直发小包', '小包，阿联酋，一票一件', '不确定', '小包', '一票一件', b'0', 1, 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R3', 'file', NULL, 1, 1),
    (911080, 904004, 'ET-20260604', 'ET', 'ET-SAU-SMALL-PARCEL-CN-20260604', 'ET-SAU-SMALL-PARCEL-CN-20260604-CAT-STD', '沙特自发货小包', '中国到沙特自发货小包', '小包分类', '沙特小包', '一票一件，沙特国际小包', '小包，沙特，一票一件', '不确定', '小包', '一票一件', b'0', 1, 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R9', 'file', NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `forwarder_code` = VALUES(`forwarder_code`),
    `service_code` = VALUES(`service_code`),
    `cargo_category_code` = VALUES(`cargo_category_code`),
    `cargo_category_name` = VALUES(`cargo_category_name`),
    `source_category_name` = VALUES(`source_category_name`),
    `category_level_1` = VALUES(`category_level_1`),
    `category_level_2` = VALUES(`category_level_2`),
    `product_examples` = VALUES(`product_examples`),
    `product_keywords` = VALUES(`product_keywords`),
    `electric_type` = VALUES(`electric_type`),
    `sensitive_tags` = VALUES(`sensitive_tags`),
    `packing_policy` = VALUES(`packing_policy`),
    `manual_confirm_required` = VALUES(`manual_confirm_required`),
    `match_priority` = VALUES(`match_priority`),
    `source_file_name` = VALUES(`source_file_name`),
    `source_sheet_or_page` = VALUES(`source_sheet_or_page`),
    `source_row_or_locator` = VALUES(`source_row_or_locator`),
    `source_type` = VALUES(`source_type`),
    `remark` = VALUES(`remark`),
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_base_price` (
    `id`, `price_rule_code`, `quote_version_id`, `quote_version_code`, `service_code`, `cargo_category_code`, `cargo_category_name`, `pricing_model`, `currency`, `unit_price`, `billing_unit`, `billing_basis`, `volume_divisor`, `sea_weight_ratio`, `min_billable_unit`, `min_billable_unit_type`, `min_charge`, `rounding_rule`, `target_platform`, `delivery_city`, `price_status`, `source_file_name`, `source_sheet_or_page`, `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
) VALUES
    (912052, 'ET-20260604-AE-AIR-A-KG', 904004, 'ET-20260604', 'ET-AE-AIR-WH-20260604', 'ET-AE-AIR-WH-20260604-CAT-A', '阿联酋空运A类', 'PER_KG', 'RMB', 44.0000, 'KG', '整票实重与体积重取大，体积重L*W*H/6000', 6000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足1KG向上取整', 'WAREHOUSE', '阿联酋仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R6C3', 'file', NULL, 1, 1),
    (912053, 'ET-20260604-AE-AIR-B-KG', 904004, 'ET-20260604', 'ET-AE-AIR-WH-20260604', 'ET-AE-AIR-WH-20260604-CAT-B', '阿联酋空运B类', 'PER_KG', 'RMB', 46.0000, 'KG', '整票实重与体积重取大，体积重L*W*H/6000', 6000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足1KG向上取整', 'WAREHOUSE', '阿联酋仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R7C3', 'file', NULL, 1, 1),
    (912054, 'ET-20260604-AE-AIR-C-KG', 904004, 'ET-20260604', 'ET-AE-AIR-WH-20260604', 'ET-AE-AIR-WH-20260604-CAT-C', '阿联酋空运C类', 'PER_KG', 'RMB', 49.0000, 'KG', '整票实重与体积重取大，体积重L*W*H/6000', 6000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足1KG向上取整', 'WAREHOUSE', '阿联酋仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R8C3', 'file', NULL, 1, 1),
    (912055, 'ET-20260604-AE-AIR-D-KG', 904004, 'ET-20260604', 'ET-AE-AIR-WH-20260604', 'ET-AE-AIR-WH-20260604-CAT-D', '阿联酋空运D类', 'PER_KG', 'RMB', 56.0000, 'KG', '整票实重与体积重取大，体积重L*W*H/6000', 6000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足1KG向上取整', 'WAREHOUSE', '阿联酋仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R9C3', 'file', NULL, 1, 1),
    (912056, 'ET-20260604-AE-SEA-A-CBM', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', 'ET-AE-SEA-WH-20260604-CAT-A', '阿联酋海运A类', 'PER_CBM', 'RMB', 1350.0000, 'CBM', '同类别整票实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '阿联酋仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R12C3', 'file', '交义乌仓+100/CBM另计。', 1, 1),
    (912057, 'ET-20260604-AE-SEA-B-CBM', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', 'ET-AE-SEA-WH-20260604-CAT-B', '阿联酋海运B类', 'PER_CBM', 'RMB', 1660.0000, 'CBM', '同类别整票实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '阿联酋仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R13C3', 'file', '交义乌仓+100/CBM另计。', 1, 1),
    (912058, 'ET-20260604-AE-SEA-C-CBM', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', 'ET-AE-SEA-WH-20260604-CAT-C', '阿联酋海运C类', 'PER_CBM', 'RMB', 1880.0000, 'CBM', '同类别整票实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '阿联酋仓', 'STARTING_PRICE', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R14C3', 'file', '1880起，逐个确认。', 1, 1),
    (912059, 'ET-20260604-SAU-EXPRESS-A-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-A', '沙特快递A类', 'PER_KG', 'RMB', 67.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R6C3', 'file', '发之前先与ET确认。', 1, 1),
    (912060, 'ET-20260604-SAU-EXPRESS-BC-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-BC', '沙特快递BC类', 'PER_KG', 'RMB', 77.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R7C3', 'file', NULL, 1, 1),
    (912061, 'ET-20260604-SAU-EXPRESS-D-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-D', '沙特快递D类', 'PER_KG', 'RMB', 85.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R8C3', 'file', NULL, 1, 1),
    (912062, 'ET-20260604-SAU-EXPRESS-E-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-E', '沙特快递E类', 'PER_KG', 'RMB', 92.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R9C3', 'file', '逐个确认。', 1, 1),
    (912063, 'ET-20260604-SAU-EXPRESS-GF-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-GF', '沙特快递GF类', 'PER_KG', 'RMB', 99.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'STARTING_PRICE', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R10C3', 'file', '99+，大牌+食品。', 1, 1),
    (912064, 'ET-20260604-SAU-EXPRESS-H-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-H', '沙特快递H类', 'PER_KG', 'RMB', 112.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R11C3', 'file', NULL, 1, 1),
    (912065, 'ET-20260604-SAU-EXPRESS-K-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-K', '沙特快递K类', 'PER_KG', 'RMB', 115.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R12C3', 'file', NULL, 1, 1),
    (912066, 'ET-20260604-SAU-EXPRESS-Q-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-Q', '沙特快递Q类', 'PER_KG', 'RMB', 132.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R13C3', 'file', NULL, 1, 1),
    (912067, 'ET-20260604-SAU-EXPRESS-W-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-W', '沙特快递W类', 'PER_KG', 'RMB', 147.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R14C3', 'file', NULL, 1, 1),
    (912068, 'ET-20260604-SAU-EXPRESS-L-KG', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-L', '沙特快递L类', 'PER_KG', 'RMB', 197.0000, 'KG', '单箱实重与体积重取大，体积重L*W*H/5000', 5000.0000, NULL, 10.0000, 'KG', NULL, '不足10KG按10KG；不足0.5KG按0.5KG，超过0.5KG按1KG', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R15C3', 'file', NULL, 1, 1),
    (912069, 'ET-20260604-SAU-EXPRESS-PHONE-PIECE', 904004, 'ET-20260604', 'ET-SAU-EXPRESS-WH-20260604', 'ET-SAU-EXPRESS-WH-20260604-CAT-PHONE', '沙特快递手机/平板', 'PER_PIECE', 'RMB', 197.0000, 'PIECE', '手机/平板按台计价', NULL, NULL, 1.0000, 'PIECE', NULL, '按台', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R16C3', 'file', NULL, 1, 1),
    (912072, 'ET-20260604-SAU-SEA-A-CBM', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-A', '沙特海运A类', 'PER_CBM', 'RMB', 1400.0000, 'CBM', '同类别体积分开汇总；实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R22C3', 'file', '按用户确认在0604基础上每方-100；交义乌仓+100/CBM另计。', 1, 1),
    (912073, 'ET-20260604-SAU-SEA-B-CBM', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-B', '沙特海运B类', 'PER_CBM', 'RMB', 1500.0000, 'CBM', '同类别体积分开汇总；实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R23C3', 'file', '按用户确认在0604基础上每方-100；交义乌仓+100/CBM另计。', 1, 1),
    (912074, 'ET-20260604-SAU-SEA-C-CBM', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-C', '沙特海运C类', 'PER_CBM', 'RMB', 1850.0000, 'CBM', '同类别体积分开汇总；实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R24C3', 'file', '按用户确认在0604基础上每方-100；交义乌仓+100/CBM另计。', 1, 1),
    (912075, 'ET-20260604-SAU-SEA-D-CBM', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-D', '沙特海运D类', 'PER_CBM', 'RMB', 1700.0000, 'CBM', '同类别体积分开汇总；实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R25C3', 'file', '按用户确认在0604基础上每方-100；交义乌仓+100/CBM另计。', 1, 1),
    (912076, 'ET-20260604-SAU-SEA-E-CBM', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-E', '沙特海运E混装类', 'PER_CBM', 'RMB', 1800.0000, 'CBM', '同类别体积分开汇总；实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R26C3', 'file', '按用户确认在0604基础上每方-100；A/B/C/D混装发之前先与ET业务人员确认。', 1, 1),
    (912077, 'ET-20260604-SAU-SEA-F-CBM', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-F', '沙特海运F类', 'PER_CBM', 'RMB', 1900.0000, 'CBM', '同类别体积分开汇总；实重/300与实际体积取大', NULL, '1CBM=300KG', 0.2000, 'CBM', NULL, '不足0.2CBM按0.2CBM；保留2位小数', 'WAREHOUSE', '沙特仓', 'STARTING_PRICE', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R27C3', 'file', '按用户确认在0604基础上每方-100；1900+起；大型设备需提供产品铭牌。', 1, 1),
    (912078, 'ET-20260604-SAU-SEA-G-KG', 904004, 'ET-20260604', 'ET-SAU-SEA-WH-20260604', 'ET-SAU-SEA-WH-20260604-CAT-G', '沙特海运G类食品化妆品', 'PER_KG', 'RMB', 32.0000, 'KG', '食品化妆品海运按KG报价；食品类起运量3CBM', NULL, '1CBM=300KG', 3.0000, 'CBM', NULL, '食品类起运量3CBM；保留2位小数', 'WAREHOUSE', '沙特仓', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '沙特空海运双清', 'R28C3', 'file', '猪肉制品和酒精不允许；需确认成分和包装。', 1, 1),
    (912079, 'ET-20260604-AE-SMALL-CN-TENTH-KG', 904004, 'ET-20260604', 'ET-AE-SMALL-PARCEL-CN-20260604', 'ET-AE-SMALL-PARCEL-CN-20260604-CAT-STD', '阿联酋自发货小包', 'PER_KG', 'RMB', 6.0000, 'TENTH_KG', '中国到阿联酋自发货小包每0.1KG，最低0.1KG', 6000.0000, NULL, 0.1000, 'KG', NULL, '不足0.1KG按0.1KG；0<W≤30KG', 'WAREHOUSE', '阿联酋', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R3C2', 'file', '运费成本=小包运费+操作费。', 1, 1),
    (912080, 'ET-20260604-AE-SMALL-LOCAL-FIRST5', 904004, 'ET-20260604', 'ET-AE-SMALL-PARCEL-LOCAL-20260604', NULL, '阿联酋起步本地小包首重', 'PER_SHIPMENT', 'RMB', 25.0000, 'FIRST_5KG', '首重5KG', NULL, NULL, 5.0000, 'KG', NULL, '首重5KG', 'WAREHOUSE', '阿联酋', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R5C2', 'file', NULL, 1, 1),
    (912081, 'ET-20260604-AE-SMALL-LOCAL-ADD-KG', 904004, 'ET-20260604', 'ET-AE-SMALL-PARCEL-LOCAL-20260604', NULL, '阿联酋起步本地小包续重', 'PER_KG', 'RMB', 5.0000, 'ADDITIONAL_KG', '续重1KG', NULL, NULL, 1.0000, 'KG', NULL, '按续重1KG计费', 'WAREHOUSE', '阿联酋', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R5C3', 'file', NULL, 1, 1),
    (912082, 'ET-20260604-SAU-SMALL-CN-HALF-KG', 904004, 'ET-20260604', 'ET-SAU-SMALL-PARCEL-CN-20260604', 'ET-SAU-SMALL-PARCEL-CN-20260604-CAT-STD', '沙特自发货小包', 'PER_KG', 'RMB', 45.0000, 'HALF_KG', '中国到沙特自发货小包每0.5KG，最低0.5KG', 5000.0000, NULL, 0.5000, 'KG', NULL, '不足0.5KG按0.5KG', 'WAREHOUSE', '沙特', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R9C2', 'file', '运费成本=小包运费+操作费。', 1, 1),
    (912083, 'ET-20260604-SAU-SMALL-DXB-RUH-HALF-KG', 904004, 'ET-20260604', 'ET-SAU-SMALL-PARCEL-DXB-RUH-20260604', NULL, '迪拜到沙特利雅得小包', 'PER_KG', 'RMB', 30.0000, 'HALF_KG', '迪拜到沙特利雅得小包每0.5KG', 5000.0000, NULL, 0.5000, 'KG', NULL, '不足0.5KG按0.5KG', 'WAREHOUSE', '利雅得/RUH', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R10C2', 'file', NULL, 1, 1),
    (912084, 'ET-20260604-SAU-SMALL-LOCAL-FIRST5', 904004, 'ET-20260604', 'ET-SAU-SMALL-PARCEL-LOCAL-20260604', NULL, '沙特起步本地自发货小包首重', 'PER_SHIPMENT', 'RMB', 40.0000, 'FIRST_5KG', '首重5KG', NULL, NULL, 5.0000, 'KG', NULL, '首重5KG', 'WAREHOUSE', '沙特', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R12C2', 'file', NULL, 1, 1),
    (912085, 'ET-20260604-SAU-SMALL-LOCAL-ADD-KG', 904004, 'ET-20260604', 'ET-SAU-SMALL-PARCEL-LOCAL-20260604', NULL, '沙特起步本地自发货小包续重', 'PER_KG', 'RMB', 5.0000, 'ADDITIONAL_KG', '续重1KG', NULL, NULL, 1.0000, 'KG', NULL, '按续重1KG计费', 'WAREHOUSE', '沙特', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R12C3', 'file', NULL, 1, 1),
    (912086, 'ET-20260604-LAST-MILE-RUH-CBM', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, '平台仓送仓利雅得', 'PER_CBM', 'RMB', 150.0000, 'CBM', '亚马逊/NOON送仓费利雅得', NULL, NULL, NULL, NULL, NULL, '按CBM', 'WAREHOUSE', '利雅得/RUH', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R3C3', 'file', NULL, 1, 1),
    (912087, 'ET-20260604-LAST-MILE-JED-CBM', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, '平台仓送仓吉达', 'PER_CBM', 'RMB', 250.0000, 'CBM', '亚马逊/NOON送仓费吉达', NULL, NULL, NULL, NULL, NULL, '按CBM', 'WAREHOUSE', '吉达/JED', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R3C3', 'file', NULL, 1, 1),
    (912088, 'ET-20260604-LAST-MILE-KSA-BOX1', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'KSA线下2B按箱计费1', 'PER_PIECE', 'RMB', 25.0000, 'BOX', '25RMB/箱，200RMB起收', NULL, NULL, NULL, NULL, 200.0000, '200RMB起收', 'WAREHOUSE', '沙特', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R4C3', 'file', NULL, 1, 1),
    (912089, 'ET-20260604-LAST-MILE-KSA-KG1', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'KSA线下2B按kg计费1', 'PER_KG', 'RMB', 3.0000, 'KG', '实重/体积除5000，3元/kg，200RMB起收', 5000.0000, NULL, NULL, NULL, 200.0000, '200RMB起收', 'WAREHOUSE', '沙特', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R6C3', 'file', '超出服务城市额外加收偏远派送费750SAR/票。', 1, 1),
    (912090, 'ET-20260604-LAST-MILE-UAE-BOX', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'UAE线下2B按箱计费', 'PER_PIECE', 'RMB', 30.0000, 'BOX', '30元/箱，100元起收', NULL, NULL, NULL, NULL, 100.0000, '100元起收', 'WAREHOUSE', '阿联酋', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R17C3', 'file', '每箱限重25kg，最长边不超60cm。', 1, 1),
    (912091, 'ET-20260604-LAST-MILE-KSA-VEHICLE-RUH', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'KSA专车Riyadh', 'PER_SHIPMENT', 'RMB', 600.0000, 'VEHICLE', '专车渠道Riyadh，10CBM内小于3ton', NULL, NULL, NULL, NULL, NULL, '超过10CBM按倍数加收；超过35CBM单询', 'WAREHOUSE', 'Riyadh', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R7C3', 'file', NULL, 1, 1),
    (912092, 'ET-20260604-LAST-MILE-KSA-VEHICLE-MID', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'KSA专车中距离城市', 'PER_SHIPMENT', 'RMB', 2000.0000, 'VEHICLE', 'Dammam/wadi dwasir/Buraydah/Unayzah/Al-khobar/Qassim/Jubail', NULL, NULL, NULL, NULL, NULL, '超过10CBM按倍数加收；超过35CBM单询', 'WAREHOUSE', '沙特', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R9C3', 'file', '不同城市具体价格600-4200RMB/车，详见规则。', 1, 1),
    (912093, 'ET-20260604-LAST-MILE-UAE-VEHICLE-DXB', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'UAE专车迪拜', 'PER_SHIPMENT', 'AED', 250.0000, 'VEHICLE', '迪拜专车，10CBM内小于3ton', NULL, NULL, NULL, NULL, NULL, '超过10CBM按倍数加收；超过35CBM单询', 'WAREHOUSE', '迪拜', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R18C3', 'file', NULL, 1, 1),
    (912094, 'ET-20260604-LAST-MILE-UAE-VEHICLE-OTHER', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', NULL, 'UAE专车其他城市', 'PER_SHIPMENT', 'AED', 500.0000, 'VEHICLE', '阿布扎比/富集拉/阿莱因/沙加/阿基曼/乌木盖万/拉斯海马', NULL, NULL, NULL, NULL, NULL, '超过10CBM按倍数加收；超过35CBM单询', 'WAREHOUSE', '阿联酋', 'NORMAL', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R19C3', 'file', NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `price_rule_code` = VALUES(`price_rule_code`),
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `service_code` = VALUES(`service_code`),
    `cargo_category_code` = VALUES(`cargo_category_code`),
    `cargo_category_name` = VALUES(`cargo_category_name`),
    `pricing_model` = VALUES(`pricing_model`),
    `currency` = VALUES(`currency`),
    `unit_price` = VALUES(`unit_price`),
    `billing_unit` = VALUES(`billing_unit`),
    `billing_basis` = VALUES(`billing_basis`),
    `volume_divisor` = VALUES(`volume_divisor`),
    `sea_weight_ratio` = VALUES(`sea_weight_ratio`),
    `min_billable_unit` = VALUES(`min_billable_unit`),
    `min_billable_unit_type` = VALUES(`min_billable_unit_type`),
    `min_charge` = VALUES(`min_charge`),
    `rounding_rule` = VALUES(`rounding_rule`),
    `target_platform` = VALUES(`target_platform`),
    `delivery_city` = VALUES(`delivery_city`),
    `price_status` = VALUES(`price_status`),
    `source_file_name` = VALUES(`source_file_name`),
    `source_sheet_or_page` = VALUES(`source_sheet_or_page`),
    `source_row_or_locator` = VALUES(`source_row_or_locator`),
    `source_type` = VALUES(`source_type`),
    `remark` = VALUES(`remark`),
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `forwarder_quote_transport_fee` (
    `id`, `fee_rule_code`, `quote_version_id`, `quote_version_code`, `service_code`, `fee_name`, `fee_type`, `target_platform`, `delivery_city`, `trigger_condition`, `pricing_model`, `currency`, `amount`, `rate`, `billing_unit`, `billing_basis`, `min_charge`, `min_billable_unit`, `rounding_rule`, `included_in_base_price`, `source_file_name`, `source_sheet_or_page`, `source_row_or_locator`, `source_type`, `remark`, `created_by`, `updated_by`
) VALUES
    (913009, 'ET-20260604-SMALL-AE-CN-OPERATION', 904004, 'ET-20260604', 'ET-AE-SMALL-PARCEL-CN-20260604', '阿联酋自发货小包操作费', 'OPERATION', 'WAREHOUSE', '阿联酋', '中国到阿联酋自发货小包', 'PER_SHIPMENT', 'RMB', 20.0000, NULL, 'SHIPMENT', '操作费/票', NULL, NULL, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R3C3', 'file', NULL, 1, 1),
    (913010, 'ET-20260604-SMALL-SAU-CN-OPERATION', 904004, 'ET-20260604', 'ET-SAU-SMALL-PARCEL-CN-20260604', '沙特自发货小包操作费', 'OPERATION', 'WAREHOUSE', '沙特', '中国到沙特自发货小包', 'PER_SHIPMENT', 'RMB', 30.0000, NULL, 'SHIPMENT', '操作费/票', NULL, NULL, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R9C3', 'file', NULL, 1, 1),
    (913011, 'ET-20260604-SMALL-SAU-DXB-RUH-OPERATION', 904004, 'ET-20260604', 'ET-SAU-SMALL-PARCEL-DXB-RUH-20260604', '迪拜到沙特利雅得小包操作费', 'OPERATION', 'WAREHOUSE', '利雅得/RUH', '迪拜到沙特利雅得小包', 'PER_SHIPMENT', 'RMB', 30.0000, NULL, 'SHIPMENT', '操作费/票', NULL, NULL, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R10C3', 'file', NULL, 1, 1),
    (913012, 'ET-20260604-SMALL-COD-FEE', 904004, 'ET-20260604', 'ET-AE-SMALL-PARCEL-CN-20260604', '小包COD服务费', 'COD', 'WAREHOUSE', NULL, 'COD代收款', 'PERCENTAGE', 'RMB', NULL, 0.030000, 'SHIPMENT', 'COD代收款付金额x3%，最低RMB15/票', 15.0000, NULL, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '自发货小包', 'R3-R12 C5', 'file', '阿联酋和沙特小包均适用。', 1, 1),
    (913013, 'ET-20260604-SEA-CUSTOMS-DECLARATION', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', '海运单独报关费', 'CUSTOMS_DECLARATION', 'WAREHOUSE', NULL, '海运3CBM起接受单独报关', 'PER_SHIPMENT', 'RMB', 450.0000, NULL, 'SHIPMENT', '按票', NULL, 3.0000, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R15', 'file', '续页费另计；适用于国际物流海运。', 1, 1),
    (913014, 'ET-20260604-SEA-CUSTOMS-DECLARATION-PAGE', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', '海运单独报关续页费', 'CUSTOMS_DECLARATION', 'WAREHOUSE', NULL, '报关资料续页', 'PER_PIECE', 'RMB', 50.0000, NULL, 'PAGE', '按页', NULL, NULL, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清', 'R15', 'file', 'RMB50/页。', 1, 1),
    (913015, 'ET-20260604-YIWU-SEA-SURCHARGE', 904004, 'ET-20260604', 'ET-AE-SEA-WH-20260604', '义乌仓海运加价', 'WAREHOUSE_SURCHARGE', 'WAREHOUSE', NULL, '交义乌仓海运货', 'PER_CBM', 'RMB', 100.0000, NULL, 'CBM', '交义乌仓+100/CBM', NULL, NULL, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '阿联酋空海运双清/沙特空海运双清', 'AE R12; SA R22', 'file', '阿联酋海运和沙特海运均注明交义乌仓+100/CBM。', 1, 1),
    (913019, 'ET-20260604-LAST-MILE-JED-RUH-BOX', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', '吉达/利雅得跨平台仓派送费', 'LAST_MILE_SURCHARGE', 'WAREHOUSE', '沙特', '吉达仓送平台利雅得仓或利雅得仓送平台吉达仓', 'PER_PIECE', 'RMB', 30.0000, NULL, 'BOX', '按箱加收', NULL, NULL, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R3C4', 'file', NULL, 1, 1),
    (913020, 'ET-20260604-LAST-MILE-FBA-PALLET', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', '亚马逊送仓托盘费', 'PALLET_SURCHARGE', 'WAREHOUSE', '沙特/阿联酋', 'FBA送仓需打托盘', 'PER_PIECE', 'RMB', 80.0000, NULL, 'PALLET', '托盘费按送仓体积/1.92*80元/托盘', NULL, NULL, '体积/1.92向上取整', b'0', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R3C4', 'file', '托盘尺寸120X100X160CM，约2CBM，每托重量不超过750KG。', 1, 1),
    (913021, 'ET-20260604-LAST-MILE-REMOTE-SAR', 904004, 'ET-20260604', 'ET-LAST-MILE-20260604', '沙特偏远派送费', 'REMOTE_AREA', 'WAREHOUSE', '沙特', '超出KSA按kg计费服务城市', 'PER_SHIPMENT', 'SAR', 750.0000, NULL, 'SHIPMENT', '偏远派送费750SAR/票', NULL, NULL, NULL, b'0', 'ET易通天下物流报价-0604.xlsx', '海外仓末端派送费', 'R6C4', 'file', NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    `fee_rule_code` = VALUES(`fee_rule_code`),
    `quote_version_id` = VALUES(`quote_version_id`),
    `quote_version_code` = VALUES(`quote_version_code`),
    `service_code` = VALUES(`service_code`),
    `fee_name` = VALUES(`fee_name`),
    `fee_type` = VALUES(`fee_type`),
    `target_platform` = VALUES(`target_platform`),
    `delivery_city` = VALUES(`delivery_city`),
    `trigger_condition` = VALUES(`trigger_condition`),
    `pricing_model` = VALUES(`pricing_model`),
    `currency` = VALUES(`currency`),
    `amount` = VALUES(`amount`),
    `rate` = VALUES(`rate`),
    `billing_unit` = VALUES(`billing_unit`),
    `billing_basis` = VALUES(`billing_basis`),
    `min_charge` = VALUES(`min_charge`),
    `min_billable_unit` = VALUES(`min_billable_unit`),
    `rounding_rule` = VALUES(`rounding_rule`),
    `included_in_base_price` = VALUES(`included_in_base_price`),
    `source_file_name` = VALUES(`source_file_name`),
    `source_sheet_or_page` = VALUES(`source_sheet_or_page`),
    `source_row_or_locator` = VALUES(`source_row_or_locator`),
    `source_type` = VALUES(`source_type`),
    `remark` = VALUES(`remark`),
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

-- Procurement planning currently treats ET Saudi air as a single tier.
-- Keep the source row from the quote workbook, but normalize it as an AIR service
-- and drop the unused extra express tiers until they are intentionally modeled.
DELETE FROM `forwarder_quote_base_price`
WHERE `service_code` = 'ET-SAU-EXPRESS-WH-20260604'
  AND `price_rule_code` <> 'ET-20260604-SAU-EXPRESS-A-KG';

DELETE FROM `forwarder_quote_cargo_category`
WHERE `service_code` = 'ET-SAU-EXPRESS-WH-20260604'
  AND `cargo_category_code` <> 'ET-SAU-EXPRESS-WH-20260604-CAT-A';

UPDATE `forwarder_quote_service_line`
SET `service_code` = 'ET-SAU-AIR-TIER1-WH-20260604',
    `service_name` = '易通沙特空运一档仓到仓 20260604',
    `transport_mode` = 'AIR',
    `business_type` = '空运一档',
    `active_for_mvp` = b'1',
    `source_row_or_locator` = 'R6',
    `remark` = '按用户确认，当前采购规划易通空运统一按一档计价；来源为报价单快递首档。后续新增档位时另建服务/分类。',
    `updated_by` = 1,
    `gmt_updated` = NOW()
WHERE `service_code` = 'ET-SAU-EXPRESS-WH-20260604';

UPDATE `forwarder_quote_cargo_category`
SET `service_code` = 'ET-SAU-AIR-TIER1-WH-20260604',
    `cargo_category_code` = 'ET-SAU-AIR-TIER1-WH-20260604-CAT-STD',
    `cargo_category_name` = '沙特空运一档',
    `source_category_name` = '空运一档',
    `category_level_1` = '空运分类',
    `category_level_2` = '一档',
    `product_examples` = '当前采购规划统一按一档计价。',
    `product_keywords` = '空运，一档，混装',
    `electric_type` = '不确定',
    `sensitive_tags` = '一档',
    `packing_policy` = '混装',
    `manual_confirm_required` = b'0',
    `match_priority` = 1,
    `source_row_or_locator` = 'R6',
    `remark` = '按用户确认，当前采购规划易通空运统一按一档计价；后续新增档位时另建服务/分类。',
    `updated_by` = 1,
    `gmt_updated` = NOW()
WHERE `cargo_category_code` = 'ET-SAU-EXPRESS-WH-20260604-CAT-A';

UPDATE `forwarder_quote_base_price`
SET `price_rule_code` = 'ET-20260604-SAU-AIR-TIER1-KG',
    `service_code` = 'ET-SAU-AIR-TIER1-WH-20260604',
    `cargo_category_code` = 'ET-SAU-AIR-TIER1-WH-20260604-CAT-STD',
    `cargo_category_name` = '沙特空运一档',
    `billing_basis` = '整票实重与体积重取大，体积重L*W*H/5000',
    `rounding_rule` = '不足10KG按10KG',
    `source_row_or_locator` = 'R6C3',
    `remark` = '当前采购规划统一按一档计价；后续新增档位时另建服务/分类。',
    `updated_by` = 1,
    `gmt_updated` = NOW()
WHERE `price_rule_code` = 'ET-20260604-SAU-EXPRESS-A-KG';

UPDATE `forwarder_quote_billing_rule`
SET `rule_code` = 'ET-20260604-RULE-SAU-AIR-TIER1-BILLING',
    `service_code` = 'ET-SAU-AIR-TIER1-WH-20260604',
    `condition_text` = '易通沙特空运一档混装，体积重L*W*H/5000，最低起运量10KG，不足10KG按10KG。',
    `rounding_rule` = '不足10KG按10KG',
    `source_row_or_locator` = 'R6C5',
    `remark` = '当前采购规划统一按一档计价；后续新增档位时另建服务/分类。',
    `updated_by` = 1,
    `gmt_updated` = NOW()
WHERE `rule_code` = 'ET-20260604-RULE-SAU-EXPRESS-BILLING';
