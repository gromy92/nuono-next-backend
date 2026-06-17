-- Product logistics-sensitive profile.
-- Scope: structured product attributes used by forwarder eligibility and logistics-plan recommendation.

CREATE TABLE IF NOT EXISTS `product_variant_logistics_profile` (
    `id` BIGINT NOT NULL,
    `variant_id` BIGINT NOT NULL,
    `effective_source_id` BIGINT DEFAULT NULL,
    `effective_source_type` VARCHAR(40) DEFAULT NULL,
    `profile_status` VARCHAR(40) NOT NULL DEFAULT 'needs_review',
    `battery_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `magnetic_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `liquid_powder_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `electric_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `plug_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `voltage_compatible_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `made_in_china_label_status` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `msds_status` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `sea_transport_report_status` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `brand_risk_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `food_contact_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `medical_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `cosmetic_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `wireless_camera_gps_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `laser_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `blade_weapon_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `cultural_restriction_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `wooden_material_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `sensitive_tags_json` LONGTEXT DEFAULT NULL,
    `prohibited_tags_json` LONGTEXT DEFAULT NULL,
    `manual_confirm_required` BIT(1) NOT NULL DEFAULT b'0',
    `confirmed_at` DATETIME DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `notes` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pvlp_variant` (`variant_id`),
    KEY `idx_pvlp_effective_source` (`effective_source_id`, `effective_source_type`),
    KEY `idx_pvlp_core_sensitive` (`battery_type`, `magnetic_type`, `liquid_powder_type`),
    KEY `idx_pvlp_electric_docs` (`electric_type`, `plug_type`, `made_in_china_label_status`),
    KEY `idx_pvlp_category_risk` (`brand_risk_type`, `food_contact_type`, `medical_type`, `cosmetic_type`),
    KEY `idx_pvlp_manual_confirm` (`manual_confirm_required`, `profile_status`),
    KEY `idx_pvlp_updated` (`gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_variant_logistics_profile_source` (
    `id` BIGINT NOT NULL,
    `variant_id` BIGINT NOT NULL,
    `source_type` VARCHAR(40) NOT NULL,
    `source_recorded_at` DATETIME DEFAULT NULL,
    `confidence_score` DECIMAL(5,2) DEFAULT NULL,
    `source_payload_json` LONGTEXT DEFAULT NULL,
    `battery_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `magnetic_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `liquid_powder_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `electric_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `plug_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `voltage_compatible_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `made_in_china_label_status` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `msds_status` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `sea_transport_report_status` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `brand_risk_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `food_contact_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `medical_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `cosmetic_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `wireless_camera_gps_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `laser_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `blade_weapon_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `cultural_restriction_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `wooden_material_type` VARCHAR(40) NOT NULL DEFAULT 'unknown',
    `sensitive_tags_json` LONGTEXT DEFAULT NULL,
    `prohibited_tags_json` LONGTEXT DEFAULT NULL,
    `manual_confirm_required` BIT(1) NOT NULL DEFAULT b'0',
    `confirmed_at` DATETIME DEFAULT NULL,
    `confirmed_by` BIGINT DEFAULT NULL,
    `notes` VARCHAR(1000) DEFAULT NULL,
    `is_deleted` BIT(1) NOT NULL DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pvlp_source_variant_type` (`variant_id`, `source_type`),
    KEY `idx_pvlp_source_variant` (`variant_id`),
    KEY `idx_pvlp_source_type` (`source_type`, `source_recorded_at`),
    KEY `idx_pvlp_source_sensitive` (`battery_type`, `magnetic_type`, `liquid_powder_type`),
    KEY `idx_pvlp_source_updated` (`gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_variant_logistics_profile', GREATEST(COALESCE(MAX(`id`), 130000), 130000), NOW(), NOW()
FROM `product_variant_logistics_profile`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO `product_management_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
SELECT 'product_variant_logistics_profile_source', GREATEST(COALESCE(MAX(`id`), 140000), 140000), NOW(), NOW()
FROM `product_variant_logistics_profile_source`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(`next_id`, VALUES(`next_id`)),
    `gmt_updated` = NOW();

INSERT INTO `product_variant_logistics_profile_source` (
    `id`, `variant_id`, `source_type`, `source_recorded_at`, `confidence_score`, `source_payload_json`,
    `battery_type`, `magnetic_type`, `liquid_powder_type`, `electric_type`,
    `plug_type`, `voltage_compatible_type`, `made_in_china_label_status`,
    `msds_status`, `sea_transport_report_status`,
    `brand_risk_type`, `food_contact_type`, `medical_type`, `cosmetic_type`,
    `wireless_camera_gps_type`, `laser_type`, `blade_weapon_type`, `cultural_restriction_type`,
    `wooden_material_type`, `sensitive_tags_json`, `prohibited_tags_json`,
    `manual_confirm_required`, `confirmed_at`, `confirmed_by`, `notes`,
    `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
    140000 + pvs.id,
    pvs.variant_id,
    'legacy_spec',
    COALESCE(pvs.confirmed_at, pvs.gmt_updated, pvs.gmt_create, NOW()),
    NULL,
    CONCAT(
        '{"legacyProductVariantSpecId":', pvs.id,
        ',"batteryMagneticType":"', COALESCE(pvs.battery_magnetic_type, 'unknown'),
        '","liquidPowderType":"', COALESCE(pvs.liquid_powder_type, 'unknown'),
        '"}'
    ),
    CASE
        WHEN pvs.battery_magnetic_type IN ('battery', 'battery_and_magnetic') THEN 'battery_equipment'
        WHEN pvs.battery_magnetic_type = 'none' THEN 'none'
        ELSE 'unknown'
    END,
    CASE
        WHEN pvs.battery_magnetic_type IN ('magnetic', 'battery_and_magnetic') THEN 'magnetic'
        WHEN pvs.battery_magnetic_type IN ('none', 'battery') THEN 'none'
        ELSE 'unknown'
    END,
    COALESCE(pvs.liquid_powder_type, 'unknown'),
    CASE
        WHEN pvs.battery_magnetic_type IN ('battery', 'battery_and_magnetic') THEN 'battery_equipment'
        WHEN pvs.battery_magnetic_type = 'none' THEN 'none'
        ELSE 'unknown'
    END,
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    CASE
        WHEN pvs.battery_magnetic_type IN ('battery', 'magnetic', 'battery_and_magnetic')
          OR pvs.liquid_powder_type NOT IN ('none', 'unknown') THEN
            CONCAT(
                '[',
                CASE
                    WHEN pvs.battery_magnetic_type IN ('battery', 'battery_and_magnetic') THEN '"battery"'
                    ELSE ''
                END,
                CASE
                    WHEN pvs.battery_magnetic_type = 'battery_and_magnetic' THEN ',"magnetic"'
                    WHEN pvs.battery_magnetic_type = 'magnetic' THEN '"magnetic"'
                    ELSE ''
                END,
                CASE
                    WHEN pvs.liquid_powder_type NOT IN ('none', 'unknown')
                     AND pvs.battery_magnetic_type IN ('battery', 'magnetic', 'battery_and_magnetic') THEN CONCAT(',"', pvs.liquid_powder_type, '"')
                    WHEN pvs.liquid_powder_type NOT IN ('none', 'unknown') THEN CONCAT('"', pvs.liquid_powder_type, '"')
                    ELSE ''
                END,
                ']'
            )
        ELSE NULL
    END,
    NULL,
    CASE
        WHEN pvs.battery_magnetic_type IS NULL
          OR pvs.battery_magnetic_type = 'unknown'
          OR pvs.liquid_powder_type IS NULL
          OR pvs.liquid_powder_type = 'unknown'
          OR pvs.battery_magnetic_type IN ('battery', 'magnetic', 'battery_and_magnetic')
          OR pvs.liquid_powder_type NOT IN ('none', 'unknown') THEN b'1'
        ELSE b'0'
    END,
    pvs.confirmed_at,
    pvs.confirmed_by,
    '由旧商品规格敏感字段迁移，仅覆盖带电/带磁/液粉基础信息，品牌、文件、标签等仍需补全。',
    b'0',
    pvs.created_by,
    pvs.updated_by,
    COALESCE(pvs.gmt_create, NOW()),
    COALESCE(pvs.gmt_updated, NOW())
FROM product_variant_spec pvs
WHERE pvs.is_deleted = 0
ON DUPLICATE KEY UPDATE
    `source_recorded_at` = VALUES(`source_recorded_at`),
    `confidence_score` = VALUES(`confidence_score`),
    `source_payload_json` = VALUES(`source_payload_json`),
    `battery_type` = VALUES(`battery_type`),
    `magnetic_type` = VALUES(`magnetic_type`),
    `liquid_powder_type` = VALUES(`liquid_powder_type`),
    `electric_type` = VALUES(`electric_type`),
    `plug_type` = VALUES(`plug_type`),
    `voltage_compatible_type` = VALUES(`voltage_compatible_type`),
    `made_in_china_label_status` = VALUES(`made_in_china_label_status`),
    `msds_status` = VALUES(`msds_status`),
    `sea_transport_report_status` = VALUES(`sea_transport_report_status`),
    `brand_risk_type` = VALUES(`brand_risk_type`),
    `food_contact_type` = VALUES(`food_contact_type`),
    `medical_type` = VALUES(`medical_type`),
    `cosmetic_type` = VALUES(`cosmetic_type`),
    `wireless_camera_gps_type` = VALUES(`wireless_camera_gps_type`),
    `laser_type` = VALUES(`laser_type`),
    `blade_weapon_type` = VALUES(`blade_weapon_type`),
    `cultural_restriction_type` = VALUES(`cultural_restriction_type`),
    `wooden_material_type` = VALUES(`wooden_material_type`),
    `sensitive_tags_json` = VALUES(`sensitive_tags_json`),
    `prohibited_tags_json` = VALUES(`prohibited_tags_json`),
    `manual_confirm_required` = VALUES(`manual_confirm_required`),
    `confirmed_at` = VALUES(`confirmed_at`),
    `confirmed_by` = VALUES(`confirmed_by`),
    `notes` = VALUES(`notes`),
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `product_variant_logistics_profile` (
    `id`, `variant_id`, `effective_source_id`, `effective_source_type`, `profile_status`,
    `battery_type`, `magnetic_type`, `liquid_powder_type`, `electric_type`,
    `plug_type`, `voltage_compatible_type`, `made_in_china_label_status`,
    `msds_status`, `sea_transport_report_status`,
    `brand_risk_type`, `food_contact_type`, `medical_type`, `cosmetic_type`,
    `wireless_camera_gps_type`, `laser_type`, `blade_weapon_type`, `cultural_restriction_type`,
    `wooden_material_type`, `sensitive_tags_json`, `prohibited_tags_json`,
    `manual_confirm_required`, `confirmed_at`, `confirmed_by`, `notes`,
    `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
    130000 + pvs.id,
    pvs.variant_id,
    source_row.id,
    'legacy_spec',
    'needs_review',
    source_row.battery_type,
    source_row.magnetic_type,
    source_row.liquid_powder_type,
    source_row.electric_type,
    source_row.plug_type,
    source_row.voltage_compatible_type,
    source_row.made_in_china_label_status,
    source_row.msds_status,
    source_row.sea_transport_report_status,
    source_row.brand_risk_type,
    source_row.food_contact_type,
    source_row.medical_type,
    source_row.cosmetic_type,
    source_row.wireless_camera_gps_type,
    source_row.laser_type,
    source_row.blade_weapon_type,
    source_row.cultural_restriction_type,
    source_row.wooden_material_type,
    source_row.sensitive_tags_json,
    source_row.prohibited_tags_json,
    source_row.manual_confirm_required,
    source_row.confirmed_at,
    source_row.confirmed_by,
    source_row.notes,
    b'0',
    pvs.created_by,
    pvs.updated_by,
    COALESCE(pvs.gmt_create, NOW()),
    NOW()
FROM product_variant_spec pvs
JOIN product_variant_logistics_profile_source source_row
  ON source_row.variant_id = pvs.variant_id
 AND source_row.source_type = 'legacy_spec'
 AND source_row.is_deleted = 0
WHERE pvs.is_deleted = 0
ON DUPLICATE KEY UPDATE
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `product_variant_logistics_profile_source` (
    `id`, `variant_id`, `source_type`, `source_recorded_at`, `confidence_score`, `source_payload_json`,
    `battery_type`, `magnetic_type`, `liquid_powder_type`, `electric_type`,
    `plug_type`, `voltage_compatible_type`, `made_in_china_label_status`,
    `msds_status`, `sea_transport_report_status`,
    `brand_risk_type`, `food_contact_type`, `medical_type`, `cosmetic_type`,
    `wireless_camera_gps_type`, `laser_type`, `blade_weapon_type`, `cultural_restriction_type`,
    `wooden_material_type`, `sensitive_tags_json`, `prohibited_tags_json`,
    `manual_confirm_required`, `confirmed_at`, `confirmed_by`, `notes`,
    `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
    10000000 + pv.id,
    pv.id,
    'catalog_default',
    NOW(),
    NULL,
    '{"reason":"missing_product_variant_spec"}',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    'unknown',
    NULL,
    NULL,
    b'1',
    NULL,
    NULL,
    '商品暂无规格敏感字段，先建立默认物流敏感档案，生成物流计划时必须提示待补。',
    b'0',
    pv.created_by,
    pv.updated_by,
    COALESCE(pv.gmt_create, NOW()),
    NOW()
FROM product_variant pv
LEFT JOIN product_variant_logistics_profile existing_profile
  ON existing_profile.variant_id = pv.id
 AND existing_profile.is_deleted = b'0'
WHERE (pv.is_deleted IS NULL OR pv.is_deleted = b'0')
  AND existing_profile.id IS NULL
ON DUPLICATE KEY UPDATE
    `source_recorded_at` = VALUES(`source_recorded_at`),
    `source_payload_json` = VALUES(`source_payload_json`),
    `manual_confirm_required` = b'1',
    `notes` = VALUES(`notes`),
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

INSERT INTO `product_variant_logistics_profile` (
    `id`, `variant_id`, `effective_source_id`, `effective_source_type`, `profile_status`,
    `battery_type`, `magnetic_type`, `liquid_powder_type`, `electric_type`,
    `plug_type`, `voltage_compatible_type`, `made_in_china_label_status`,
    `msds_status`, `sea_transport_report_status`,
    `brand_risk_type`, `food_contact_type`, `medical_type`, `cosmetic_type`,
    `wireless_camera_gps_type`, `laser_type`, `blade_weapon_type`, `cultural_restriction_type`,
    `wooden_material_type`, `sensitive_tags_json`, `prohibited_tags_json`,
    `manual_confirm_required`, `confirmed_at`, `confirmed_by`, `notes`,
    `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
SELECT
    11000000 + pv.id,
    pv.id,
    source_row.id,
    'catalog_default',
    'needs_review',
    source_row.battery_type,
    source_row.magnetic_type,
    source_row.liquid_powder_type,
    source_row.electric_type,
    source_row.plug_type,
    source_row.voltage_compatible_type,
    source_row.made_in_china_label_status,
    source_row.msds_status,
    source_row.sea_transport_report_status,
    source_row.brand_risk_type,
    source_row.food_contact_type,
    source_row.medical_type,
    source_row.cosmetic_type,
    source_row.wireless_camera_gps_type,
    source_row.laser_type,
    source_row.blade_weapon_type,
    source_row.cultural_restriction_type,
    source_row.wooden_material_type,
    source_row.sensitive_tags_json,
    source_row.prohibited_tags_json,
    source_row.manual_confirm_required,
    source_row.confirmed_at,
    source_row.confirmed_by,
    source_row.notes,
    b'0',
    pv.created_by,
    pv.updated_by,
    COALESCE(pv.gmt_create, NOW()),
    NOW()
FROM product_variant pv
JOIN product_variant_logistics_profile_source source_row
  ON source_row.variant_id = pv.id
 AND source_row.source_type = 'catalog_default'
 AND source_row.is_deleted = b'0'
LEFT JOIN product_variant_logistics_profile existing_profile
  ON existing_profile.variant_id = pv.id
 AND existing_profile.is_deleted = b'0'
WHERE (pv.is_deleted IS NULL OR pv.is_deleted = b'0')
  AND existing_profile.id IS NULL
ON DUPLICATE KEY UPDATE
    `is_deleted` = b'0',
    `updated_by` = VALUES(`updated_by`),
    `gmt_updated` = NOW();

UPDATE `product_management_id_sequence` seq
JOIN (
    SELECT GREATEST(COALESCE(MAX(`id`) + 1, 130000), 130000) AS next_id
    FROM `product_variant_logistics_profile`
) refreshed_profile_sequence
SET seq.`next_id` = GREATEST(seq.`next_id`, refreshed_profile_sequence.next_id),
    seq.`gmt_updated` = NOW()
WHERE seq.`sequence_name` = 'product_variant_logistics_profile';

UPDATE `product_management_id_sequence` seq
JOIN (
    SELECT GREATEST(COALESCE(MAX(`id`) + 1, 140000), 140000) AS next_id
    FROM `product_variant_logistics_profile_source`
) refreshed_profile_source_sequence
SET seq.`next_id` = GREATEST(seq.`next_id`, refreshed_profile_source_sequence.next_id),
    seq.`gmt_updated` = NOW()
WHERE seq.`sequence_name` = 'product_variant_logistics_profile_source';
