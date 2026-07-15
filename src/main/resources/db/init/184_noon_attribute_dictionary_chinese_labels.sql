SET @add_noon_attribute_field_label_zh := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_attribute_field'
              AND COLUMN_NAME = 'label_zh'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_attribute_field` ADD COLUMN `label_zh` VARCHAR(255) DEFAULT NULL AFTER `label_ar`'
    )
);
PREPARE stmt FROM @add_noon_attribute_field_label_zh;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_noon_attribute_option_label_zh := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_attribute_option'
              AND COLUMN_NAME = 'label_zh'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_attribute_option` ADD COLUMN `label_zh` VARCHAR(255) DEFAULT NULL AFTER `label_ar`'
    )
);
PREPARE stmt FROM @add_noon_attribute_option_label_zh;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_noon_attribute_unit_option_label_zh := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_attribute_unit_option'
              AND COLUMN_NAME = 'label_zh'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_attribute_unit_option` ADD COLUMN `label_zh` VARCHAR(255) DEFAULT NULL AFTER `label_ar`'
    )
);
PREPARE stmt FROM @add_noon_attribute_unit_option_label_zh;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `noon_attribute_field`
SET `label_zh` = CASE `attribute_code`
    WHEN 'base_material' THEN '基础材质'
    WHEN 'care_instructions' THEN '护理说明'
    WHEN 'colour_family' THEN '颜色'
    WHEN 'colour_name' THEN '颜色名称'
    WHEN 'connection_type' THEN '连接类型'
    WHEN 'control_method' THEN '控制方式'
    WHEN 'country_of_origin' THEN '原产国'
    WHEN 'hs_code' THEN '海关编码'
    WHEN 'item_condition' THEN '商品成色'
    WHEN 'lighting_technology' THEN '照明技术'
    WHEN 'material_finish' THEN '表面工艺'
    WHEN 'model_name' THEN '型号名称'
    WHEN 'model_number' THEN '型号'
    WHEN 'occasion' THEN '适用场景'
    WHEN 'pattern' THEN '图案'
    WHEN 'product_height' THEN '商品高度'
    WHEN 'product_length' THEN '商品长度'
    WHEN 'product_weight' THEN '商品重量'
    WHEN 'product_width_depth' THEN '商品宽度/深度'
    WHEN 'secondary_material' THEN '辅材'
    WHEN 'set_includes' THEN '包含物'
    WHEN 'shape' THEN '形状'
    WHEN 'whats_in_the_box' THEN '包装清单'
    WHEN 'mpn' THEN '制造商零件号'
    WHEN 'shipping_height' THEN '包装高度'
    WHEN 'shipping_length' THEN '包装长度'
    WHEN 'shipping_weight' THEN '包装重量'
    WHEN 'shipping_width_depth' THEN '包装宽度/深度'
    WHEN 'vat_rate_ae' THEN '阿联酋 VAT 税率'
    WHEN 'vat_rate_eg' THEN '埃及 VAT 税率'
    WHEN 'vat_rate_sa' THEN '沙特 VAT 税率'
    ELSE `label_zh`
END
WHERE (`label_zh` IS NULL OR TRIM(`label_zh`) = '')
  AND `attribute_code` IN (
      'base_material', 'care_instructions', 'colour_family', 'colour_name',
      'connection_type', 'control_method', 'country_of_origin', 'hs_code',
      'item_condition', 'lighting_technology', 'material_finish', 'model_name',
      'model_number', 'occasion', 'pattern', 'product_height', 'product_length',
      'product_weight', 'product_width_depth', 'secondary_material', 'set_includes',
      'shape', 'whats_in_the_box', 'mpn', 'shipping_height', 'shipping_length',
      'shipping_weight', 'shipping_width_depth', 'vat_rate_ae', 'vat_rate_eg', 'vat_rate_sa'
  );

UPDATE `noon_attribute_option` opt
JOIN `noon_attribute_field` field ON field.`id` = opt.`field_id`
SET opt.`label_zh` = CASE
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'plastic' THEN '塑料'
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'abs' THEN 'ABS'
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'cotton' THEN '棉'
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'glass' THEN '玻璃'
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'fabric' THEN '布料'
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'pvc' THEN 'PVC'
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'metal' THEN '金属'
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'ceramic' THEN '陶瓷'
    WHEN field.`attribute_code` IN ('base_material', 'secondary_material') AND opt.`option_value` = 'wood' THEN '木材'
    WHEN field.`attribute_code` = 'care_instructions' AND opt.`option_value` = 'machine_wash' THEN '机洗'
    WHEN field.`attribute_code` = 'care_instructions' AND opt.`option_value` = 'hand_wash' THEN '手洗'
    WHEN field.`attribute_code` = 'care_instructions' AND opt.`option_value` = 'spot_clean' THEN '局部清洁'
    WHEN field.`attribute_code` = 'care_instructions' AND opt.`option_value` = 'wipe_clean' THEN '擦拭清洁'
    WHEN field.`attribute_code` = 'care_instructions' AND opt.`option_value` = 'dry_clean' THEN '干洗'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'black' THEN '黑色'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'white' THEN '白色'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'grey' THEN '灰色'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'blue' THEN '蓝色'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'green' THEN '绿色'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'pink' THEN '粉色'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'red' THEN '红色'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'yellow' THEN '黄色'
    WHEN field.`attribute_code` = 'colour_family' AND opt.`option_value` = 'multicolour' THEN '多色'
    WHEN field.`attribute_code` = 'material_finish' AND opt.`option_value` = 'matte' THEN '哑光'
    WHEN field.`attribute_code` = 'material_finish' AND opt.`option_value` = 'glossy' THEN '亮面'
    WHEN field.`attribute_code` = 'material_finish' AND opt.`option_value` = 'polished' THEN '抛光'
    WHEN field.`attribute_code` = 'material_finish' AND opt.`option_value` = 'painted' THEN '喷漆'
    ELSE opt.`label_zh`
END
WHERE (opt.`label_zh` IS NULL OR TRIM(opt.`label_zh`) = '')
  AND field.`attribute_code` IN ('base_material', 'secondary_material', 'care_instructions', 'colour_family', 'material_finish');
