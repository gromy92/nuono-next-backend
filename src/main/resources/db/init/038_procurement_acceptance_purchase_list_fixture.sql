-- Procurement requirement-confirmation acceptance fixture
-- Purpose:
-- Keep the five original purchase source links visible at the top of the
-- local procurement-confirmation list, even after smoke tests create newer
-- auto-selection procurement demands.

UPDATE `procurement_order`
SET
    `title` = '香氛/焚香工具首批 5 款采购筛选单',
    `status` = 'SCREENING',
    `target_market` = 'SA',
    `priority` = 'HIGH',
    `source_type` = 'LINK_LIST',
    `item_count` = 5,
    `updated_by` = 10003,
    `gmt_updated` = '2026-05-13 10:00:00'
WHERE `id` = 40001
  AND `is_deleted` = b'0';

UPDATE `procurement_demand_item`
SET
    `source_platform` = 'amazon',
    `source_url` = 'https://www.amazon.sa/-/en/Rechargeable-Bakhoor-Incense-Speaker-Control/dp/B0DVH1NFP3/',
    `source_title` = '可充电古兰经音箱焚香炉遥控礼盒款',
    `target_price_min` = 18.00,
    `target_price_max` = 28.00,
    `target_quantity` = 200,
    `target_site` = 'SA',
    `special_requirement` = '便携礼品感强，优先电池款，支持家居香氛场景',
    `target_material` = 'ABS+电镀',
    `target_power_mode` = '充电款',
    `target_size_text` = '手持便携',
    `target_package_type` = '礼盒装',
    `delivery_expectation` = '10天内',
    `status` = 'SCREENING',
    `updated_by` = 10003,
    `gmt_updated` = '2026-05-13 10:05:00'
WHERE `id` = 41001
  AND `is_deleted` = b'0';

UPDATE `procurement_demand_item`
SET
    `source_platform` = 'noon',
    `source_url` = 'https://www.noon.com/saudi-en/usb-rechargeable-hair-electric-bakhoor-luxury-incense-burner/ZF4844D91A33B64771288Z/p/?o=zf4844d91a33b64771288z-1&shareId=41406d99-c74c-456b-bf7c-c47923de5136',
    `source_title` = 'USB 充电轻奢焚香炉便携款',
    `target_price_min` = 12.00,
    `target_price_max` = 20.00,
    `target_quantity` = 300,
    `target_site` = 'SA',
    `special_requirement` = '轻小件优先，支持沙特站礼盒场景，发货稳定',
    `target_material` = 'ABS+陶瓷内胆',
    `target_power_mode` = '充电款',
    `target_size_text` = '便携小型',
    `target_package_type` = '礼盒装',
    `delivery_expectation` = '7天内',
    `status` = 'DECIDED',
    `updated_by` = 10003,
    `gmt_updated` = '2026-05-13 10:04:00'
WHERE `id` = 41002
  AND `is_deleted` = b'0';

UPDATE `procurement_demand_item`
SET
    `source_platform` = 'noon',
    `source_url` = 'https://www.noon.com/saudi-en/mini-elegant-arabic-oud-incense-burner-12-cm-height-for-home-and-office-bakhoor-holder-small-decore-and-fragrance/Z290A5BEA29DCD0DCB1D9Z/p/?o=z290a5bea29dcd0dcb1d9z-1&shareId=e53898ed-9999-4506-94f7-1f87a9e8448e',
    `source_title` = '阿拉伯风 12cm 迷你焚香炉摆件款',
    `target_price_min` = 4.00,
    `target_price_max` = 9.00,
    `target_quantity` = 500,
    `target_site` = 'SA',
    `special_requirement` = '小体积、装饰感强、适合作为入门款',
    `target_material` = '陶瓷',
    `target_power_mode` = '无电',
    `target_size_text` = '12cm',
    `target_package_type` = '礼盒装',
    `delivery_expectation` = '12天内',
    `status` = 'REVIEWING',
    `updated_by` = 10003,
    `gmt_updated` = '2026-05-13 10:03:00'
WHERE `id` = 41003
  AND `is_deleted` = b'0';

UPDATE `procurement_demand_item`
SET
    `source_platform` = 'amazon',
    `source_url` = 'https://www.amazon.sa/-/en/Electric-Porcelain-Incense-Burner-Bakhoor/dp/B0BNNR14Z5/',
    `source_title` = '陶瓷插电焚香炉家居款',
    `target_price_min` = 14.00,
    `target_price_max` = 24.00,
    `target_quantity` = 220,
    `target_site` = 'SA',
    `special_requirement` = '陶瓷感、家居化、适合中高客单',
    `target_material` = '陶瓷',
    `target_power_mode` = '插电款',
    `target_size_text` = '桌面款',
    `target_package_type` = '彩盒装',
    `delivery_expectation` = '10天内',
    `status` = 'REVIEWING',
    `updated_by` = 10003,
    `gmt_updated` = '2026-05-13 10:02:00'
WHERE `id` = 41004
  AND `is_deleted` = b'0';

UPDATE `procurement_demand_item`
SET
    `source_platform` = 'noon',
    `source_url` = 'https://www.noon.com/saudi-en/2025-new-portable-electric-incense-burner-rechargeable-with-ceramic-chamber-for-use-with-incense-charcoal/Z0362D557E964BF395564Z/p/?o=ef8df8db8aaccfbb&shareId=b2d5dd56-8a45-4435-a975-e3a684380d1d',
    `source_title` = '2025 新款便携充电焚香炉陶瓷胆款',
    `target_price_min` = 11.00,
    `target_price_max` = 19.00,
    `target_quantity` = 300,
    `target_site` = 'SA',
    `special_requirement` = '便携充电款，优先看外观一致性和发货稳定性',
    `target_material` = 'ABS+陶瓷内胆',
    `target_power_mode` = '充电款',
    `target_size_text` = '便携小型',
    `target_package_type` = '彩盒装',
    `delivery_expectation` = '8天内',
    `status` = 'REVIEWING',
    `updated_by` = 10003,
    `gmt_updated` = '2026-05-13 10:01:00'
WHERE `id` = 41005
  AND `is_deleted` = b'0';

UPDATE `procurement_candidate_pool`
SET
    `gmt_updated` = '2026-05-13 10:02:00',
    `updated_by` = 10003
WHERE `demand_item_id` = 41004
  AND `is_deleted` = b'0';

UPDATE `procurement_candidate_pool`
SET
    `gmt_updated` = '2026-05-13 10:01:00',
    `updated_by` = 10003
WHERE `demand_item_id` = 41005
  AND `is_deleted` = b'0';
