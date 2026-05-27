-- Seed the system default business calendar with category-scoped 2026 factors.
-- Source: docs/运营日历类目因子建议-2026-05-27.md

SET NAMES utf8mb4;
SET SESSION group_concat_max_len = 1048576;

CREATE TEMPORARY TABLE IF NOT EXISTS `tmp_operation_calendar_default_items` (
    `item_order` INT NOT NULL,
    `group_name` VARCHAR(80) NOT NULL,
    `item_name` VARCHAR(160) NOT NULL,
    `value_type` VARCHAR(80) NOT NULL,
    `default_value` VARCHAR(120) NOT NULL,
    `result_shape` VARCHAR(180) NOT NULL,
    `note` VARCHAR(240) DEFAULT NULL,
    PRIMARY KEY (`item_order`)
) ENGINE=Memory DEFAULT CHARSET=utf8mb4;

TRUNCATE TABLE `tmp_operation_calendar_default_items`;

INSERT INTO `tmp_operation_calendar_default_items`
    (`item_order`, `group_name`, `item_name`, `value_type`, `default_value`, `result_shape`, `note`)
VALUES
    (1, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 0.85', 'all_products', '全品兜底：Ramadan 销量抑制'),
    (2, '业务日历', '开斋节 (Eid al-Fitr)', '日期范围/系数', '2026-03-19 ~ 2026-03-22 / 1.10', 'all_products', '全品兜底：开斋节保守提升'),
    (3, '业务日历', '古尔邦节 (Eid al-Adha)', '日期范围/系数', '2026-05-25 ~ 2026-05-31 / 1.08', 'all_products', '近一年销量暂未覆盖可比窗口，先用保守值'),
    (4, '业务日历', '黄色星期五', '日期范围/系数', '2026-11-20 ~ 2026-11-30 / 1.10', 'all_products', 'Noon 口径促销兜底，白色星期五不重复发布'),
    (5, '业务日历', '双十一 (11.11)', '日期范围/系数', '2026-11-10 ~ 2026-11-12 / 1.15', 'all_products', '双十一全品兜底'),
    (6, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 1.03', 'all_products', '开学季全品兜底'),
    (7, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 0.90', 'all_products', '夏季全品淡季兜底'),
    (8, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 1.20', 'category:stationery-envelopes_mailers_shipping_supplies', 'Ramadan 高置信类目：信封邮寄用品提升'),
    (9, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 1.15', 'category:kitchen_dining-dinnerware_serveware', 'Ramadan 高置信类目：餐厨服务用品提升'),
    (10, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 1.15', 'category:toys-pretend_play', 'Ramadan 高置信类目：角色扮演玩具提升'),
    (11, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 1.10', 'category:colour_cosmetics-make_up_tools_accessories', 'Ramadan 高置信类目：美妆工具不套用全品压低'),
    (12, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 1.05', 'category:home_decor-lighting', 'Ramadan 高置信类目：家居照明稳中上行'),
    (13, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 0.70', 'category:electronic_accessories-headphones', 'Ramadan 高置信类目：耳机明显弱化'),
    (14, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 0.75', 'category:stationery-writing_correction_supplies', 'Ramadan 高置信类目：书写纠正文具下降'),
    (15, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 0.75', 'category:sports_outdoor-sports_protective_gear', 'Ramadan 高置信类目：运动护具下降'),
    (16, '业务日历', '斋月 (Ramadan)', '日期范围/系数', '2026-02-18 ~ 2026-03-18 / 0.75', 'category:stationery-stationery', 'Ramadan 高置信类目：普通文具下降'),
    (17, '业务日历', '开斋节 (Eid al-Fitr)', '日期范围/系数', '2026-03-19 ~ 2026-03-22 / 1.35', 'category:hair_personal_care-hair_care', '开斋节高置信类目：头发护理强提升'),
    (18, '业务日历', '开斋节 (Eid al-Fitr)', '日期范围/系数', '2026-03-19 ~ 2026-03-22 / 1.35', 'category:colour_cosmetics-nails', '开斋节高置信类目：美甲强提升'),
    (19, '业务日历', '开斋节 (Eid al-Fitr)', '日期范围/系数', '2026-03-19 ~ 2026-03-22 / 1.35', 'category:colour_cosmetics-make_up_tools_accessories', '开斋节高置信类目：美妆工具强提升'),
    (20, '业务日历', '开斋节 (Eid al-Fitr)', '日期范围/系数', '2026-03-19 ~ 2026-03-22 / 1.35', 'category:baby_product-feeding_training_accessories', '开斋节高置信类目：婴儿喂养训练提升'),
    (21, '业务日历', '开斋节 (Eid al-Fitr)', '日期范围/系数', '2026-03-19 ~ 2026-03-22 / 1.35', 'category:stationery-gift_wrapping_supplies', '开斋节高置信类目：礼品包装提升'),
    (22, '业务日历', '开斋节 (Eid al-Fitr)', '日期范围/系数', '2026-03-19 ~ 2026-03-22 / 1.30', 'category:toys-arts_crafts', '开斋节高置信类目：手工玩具提升'),
    (23, '业务日历', '开斋节 (Eid al-Fitr)', '日期范围/系数', '2026-03-19 ~ 2026-03-22 / 1.25', 'category:electronic_accessories-phone_accessories', '开斋节高置信类目：手机配件提升'),
    (24, '业务日历', '黄色星期五', '日期范围/系数', '2026-11-20 ~ 2026-11-30 / 1.25', 'category:automotive-interior_accessories', '黄色星期五高置信类目：汽车内饰促销敏感'),
    (25, '业务日历', '黄色星期五', '日期范围/系数', '2026-11-20 ~ 2026-11-30 / 1.20', 'category:baby_product-hygiene_product', '黄色星期五高置信类目：婴儿卫生用品提升'),
    (26, '业务日历', '黄色星期五', '日期范围/系数', '2026-11-20 ~ 2026-11-30 / 0.90', 'category:stationery-gift_wrapping_supplies', '黄色星期五高置信类目：礼品包装不跟随提升'),
    (27, '业务日历', '黄色星期五', '日期范围/系数', '2026-11-20 ~ 2026-11-30 / 0.95', 'category:health_nutrition-medical_supplies_equipment', '黄色星期五高置信类目：医疗用品偏弱'),
    (28, '业务日历', '黄色星期五', '日期范围/系数', '2026-11-20 ~ 2026-11-30 / 0.95', 'category:home_improvement-electrical_solar', '黄色星期五高置信类目：电气太阳能偏弱'),
    (29, '业务日历', '黄色星期五', '日期范围/系数', '2026-11-20 ~ 2026-11-30 / 1.00', 'category:electronic_accessories-phone_accessories', '黄色星期五高置信类目：手机配件基本不提升'),
    (30, '业务日历', '黄色星期五', '日期范围/系数', '2026-11-20 ~ 2026-11-30 / 1.00', 'category:colour_cosmetics-make_up_tools_accessories', '黄色星期五高置信类目：美妆工具基本不提升'),
    (31, '业务日历', '双十一 (11.11)', '日期范围/系数', '2026-11-10 ~ 2026-11-12 / 1.00', 'category:baby_product-feeding_training_accessories', '双十一高置信类目：婴儿喂养训练不明显提升'),
    (32, '业务日历', '双十一 (11.11)', '日期范围/系数', '2026-11-10 ~ 2026-11-12 / 1.05', 'category:colour_cosmetics-make_up_tools_accessories', '双十一高置信类目：美妆工具低于全品默认'),
    (33, '业务日历', '双十一 (11.11)', '日期范围/系数', '2026-11-10 ~ 2026-11-12 / 1.05', 'category:colour_cosmetics-nails', '双十一高置信类目：美甲低于全品默认'),
    (34, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 1.35', 'category:stationery-stationery', '开学季高置信类目：普通文具核心提升'),
    (35, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 1.30', 'category:bags_luggage-other_bags', '开学季高置信类目：包袋提升'),
    (36, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 1.25', 'category:stationery-desk_accessories_workspace_organizers', '开学季高置信类目：桌面收纳提升'),
    (37, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 1.20', 'category:electronic_accessories-accessories', '开学季高置信类目：电子配件提升'),
    (38, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 1.20', 'category:stationery-office_electronics', '开学季高置信类目：办公电子提升'),
    (39, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 1.15', 'category:stationery-writing_correction_supplies', '开学季高置信类目：书写纠正文具提升'),
    (40, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 0.85', 'category:colour_cosmetics-nails', '开学季高置信类目：美甲不套用开学季提升'),
    (41, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 0.90', 'category:baby_product-feeding_training_accessories', '开学季高置信类目：婴儿喂养下降'),
    (42, '业务日历', '开学季模式', '日期范围/系数', '2026-08-17 ~ 2026-09-07 / 0.90', 'category:electronic_accessories-phone_accessories', '开学季高置信类目：手机配件下降'),
    (43, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 1.25', 'category:electronic_accessories-accessories', '夏季高置信类目：电子配件上行'),
    (44, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 1.25', 'category:video_games-accessories', '夏季高置信类目：游戏配件上行'),
    (45, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 1.15', 'category:home_appliances-small_appliances', '夏季高置信类目：小家电上行'),
    (46, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 1.10', 'category:toys-arts_crafts', '夏季高置信类目：手工玩具上行'),
    (47, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 1.10', 'category:sports_outdoor-accessories', '夏季高置信类目：户外运动配件上行'),
    (48, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 1.10', 'category:baby_product-baby_safety_equipment', '夏季高置信类目：婴儿安全设备上行'),
    (49, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 1.10', 'category:baby_product-baby_transport', '夏季高置信类目：婴儿出行上行'),
    (50, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 0.75', 'category:stationery-office_electronics', '夏季高置信类目：办公电子弱化'),
    (51, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 0.75', 'category:toys-learning_education', '夏季高置信类目：教育玩具弱化'),
    (52, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 0.75', 'category:home_improvement-laundry_care', '夏季高置信类目：洗护家清弱化'),
    (53, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 0.80', 'category:toys-novelty_toys', '夏季高置信类目：新奇玩具弱化'),
    (54, '业务日历', '夏季模式', '日期范围/系数', '2026-06-01 ~ 2026-08-31 / 0.80', 'category:stationery-desk_accessories_workspace_organizers', '夏季高置信类目：桌面收纳弱化');

SET @default_calendar_content_json := (
    SELECT CONCAT(
        '[',
        GROUP_CONCAT(
            JSON_OBJECT(
                'groupName', `group_name`,
                'itemName', `item_name`,
                'cadence', NULL,
                'valueType', `value_type`,
                'defaultValue', `default_value`,
                'resultShape', `result_shape`,
                'note', `note`
            )
            ORDER BY `item_order`
            SEPARATOR ','
        ),
        ']'
    )
    FROM `tmp_operation_calendar_default_items`
);
SET @default_calendar_item_count := (SELECT COUNT(*) FROM `tmp_operation_calendar_default_items`);
SET @default_calendar_version_id := (
    SELECT `id`
    FROM `operation_config_typed_version`
    WHERE `version_no` = 'DEFAULT_CALENDAR_CONFIG'
    LIMIT 1
);
SET @default_calendar_version_id := COALESCE(
    @default_calendar_version_id,
    (
        SELECT GREATEST(
            COALESCE(MAX(`id`) + 1, 88000),
            COALESCE((
                SELECT `next_id`
                FROM `version_publish_id_sequence`
                WHERE `sequence_name` = 'operation_config_typed_version'
                LIMIT 1
            ), 88000)
        )
        FROM `operation_config_typed_version`
    )
);

INSERT INTO `version_publish_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES ('operation_config_typed_version', @default_calendar_version_id + 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, @default_calendar_version_id + 1),
    `gmt_updated` = VALUES(`gmt_updated`);

INSERT INTO `operation_config_typed_version` (
    `id`,
    `version_no`,
    `display_name`,
    `config_type`,
    `status`,
    `source_version_no`,
    `source_label`,
    `summary`,
    `item_count`,
    `scope_summary`,
    `content_json`,
    `audit_json`,
    `created_by`,
    `updated_by`,
    `gmt_create`,
    `gmt_updated`
) VALUES (
    @default_calendar_version_id,
    'DEFAULT_CALENDAR_CONFIG',
    '默认日历配置',
    'BUSINESS_CALENDAR',
    'SYSTEM_DEFAULT',
    NULL,
    '系统默认',
    CONCAT(@default_calendar_item_count, ' 条日历配置'),
    @default_calendar_item_count,
    '全局默认',
    @default_calendar_content_json,
    '[]',
    0,
    0,
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `config_type` = VALUES(`config_type`),
    `status` = VALUES(`status`),
    `source_version_no` = VALUES(`source_version_no`),
    `source_label` = VALUES(`source_label`),
    `summary` = VALUES(`summary`),
    `item_count` = VALUES(`item_count`),
    `scope_summary` = VALUES(`scope_summary`),
    `content_json` = VALUES(`content_json`),
    `audit_json` = VALUES(`audit_json`),
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = VALUES(`gmt_updated`);

DROP TEMPORARY TABLE IF EXISTS `tmp_operation_calendar_default_items`;
