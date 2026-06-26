-- Split product logistics core attributes into first-class fields.
-- The legacy columns remain as compatibility projections for older imports and queries.

SET @add_pvlp_battery_electric_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant_logistics_profile'
              AND COLUMN_NAME = 'battery_electric_type'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant_logistics_profile` ADD COLUMN `battery_electric_type` VARCHAR(40) NOT NULL DEFAULT ''unknown'' AFTER `profile_status`'
    )
);
PREPARE stmt FROM @add_pvlp_battery_electric_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_pvlp_liquid_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant_logistics_profile'
              AND COLUMN_NAME = 'liquid_type'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant_logistics_profile` ADD COLUMN `liquid_type` VARCHAR(40) NOT NULL DEFAULT ''unknown'' AFTER `magnetic_type`'
    )
);
PREPARE stmt FROM @add_pvlp_liquid_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_pvlp_powder_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant_logistics_profile'
              AND COLUMN_NAME = 'powder_type'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant_logistics_profile` ADD COLUMN `powder_type` VARCHAR(40) NOT NULL DEFAULT ''unknown'' AFTER `liquid_type`'
    )
);
PREPARE stmt FROM @add_pvlp_powder_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_pvlps_battery_electric_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant_logistics_profile_source'
              AND COLUMN_NAME = 'battery_electric_type'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant_logistics_profile_source` ADD COLUMN `battery_electric_type` VARCHAR(40) NOT NULL DEFAULT ''unknown'' AFTER `source_payload_json`'
    )
);
PREPARE stmt FROM @add_pvlps_battery_electric_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_pvlps_liquid_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant_logistics_profile_source'
              AND COLUMN_NAME = 'liquid_type'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant_logistics_profile_source` ADD COLUMN `liquid_type` VARCHAR(40) NOT NULL DEFAULT ''unknown'' AFTER `magnetic_type`'
    )
);
PREPARE stmt FROM @add_pvlps_liquid_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_pvlps_powder_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant_logistics_profile_source'
              AND COLUMN_NAME = 'powder_type'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant_logistics_profile_source` ADD COLUMN `powder_type` VARCHAR(40) NOT NULL DEFAULT ''unknown'' AFTER `liquid_type`'
    )
);
PREPARE stmt FROM @add_pvlps_powder_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_variant_logistics_profile`
SET
    `battery_electric_type` = CASE
        WHEN `battery_type` = 'battery_equipment'
          OR `electric_type` IN ('battery_equipment', 'electric_equipment_review') THEN 'battery_or_electric'
        WHEN `battery_type` = 'none'
         AND `electric_type` = 'none' THEN 'none'
        ELSE 'unknown'
    END,
    `liquid_type` = CASE
        WHEN `liquid_powder_type` IN ('liquid', 'liquid_and_powder') THEN 'liquid'
        WHEN `liquid_powder_type` IN ('none', 'powder') THEN 'none'
        ELSE 'unknown'
    END,
    `powder_type` = CASE
        WHEN `liquid_powder_type` IN ('powder', 'liquid_and_powder') THEN 'powder'
        WHEN `liquid_powder_type` IN ('none', 'liquid') THEN 'none'
        ELSE 'unknown'
    END
WHERE `is_deleted` = b'0'
  AND (
      `battery_electric_type` = 'unknown'
      OR `liquid_type` = 'unknown'
      OR `powder_type` = 'unknown'
  );

UPDATE `product_variant_logistics_profile_source`
SET
    `battery_electric_type` = CASE
        WHEN `battery_type` = 'battery_equipment'
          OR `electric_type` IN ('battery_equipment', 'electric_equipment_review') THEN 'battery_or_electric'
        WHEN `battery_type` = 'none'
         AND `electric_type` = 'none' THEN 'none'
        ELSE 'unknown'
    END,
    `liquid_type` = CASE
        WHEN `liquid_powder_type` IN ('liquid', 'liquid_and_powder') THEN 'liquid'
        WHEN `liquid_powder_type` IN ('none', 'powder') THEN 'none'
        ELSE 'unknown'
    END,
    `powder_type` = CASE
        WHEN `liquid_powder_type` IN ('powder', 'liquid_and_powder') THEN 'powder'
        WHEN `liquid_powder_type` IN ('none', 'liquid') THEN 'none'
        ELSE 'unknown'
    END
WHERE `is_deleted` = b'0'
  AND (
      `battery_electric_type` = 'unknown'
      OR `liquid_type` = 'unknown'
      OR `powder_type` = 'unknown'
  );

SET @add_pvlp_sensitive_v2_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant_logistics_profile'
              AND INDEX_NAME = 'idx_pvlp_core_sensitive_v2'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant_logistics_profile` ADD KEY `idx_pvlp_core_sensitive_v2` (`battery_electric_type`, `magnetic_type`, `liquid_type`, `powder_type`)'
    )
);
PREPARE stmt FROM @add_pvlp_sensitive_v2_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_pvlps_sensitive_v2_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product_variant_logistics_profile_source'
              AND INDEX_NAME = 'idx_pvlp_source_sensitive_v2'
        ),
        'SELECT 1',
        'ALTER TABLE `product_variant_logistics_profile_source` ADD KEY `idx_pvlp_source_sensitive_v2` (`battery_electric_type`, `magnetic_type`, `liquid_type`, `powder_type`)'
    )
);
PREPARE stmt FROM @add_pvlps_sensitive_v2_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
