SET @add_in_transit_package_volume_weight = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'volume_weight_kg'
    ),
    'SELECT ''in_transit_package_volume_weight_kg_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `volume_weight_kg` DECIMAL(18,6) DEFAULT NULL AFTER `volume_cbm`'
);
PREPARE add_in_transit_package_volume_weight_stmt FROM @add_in_transit_package_volume_weight;
EXECUTE add_in_transit_package_volume_weight_stmt;
DEALLOCATE PREPARE add_in_transit_package_volume_weight_stmt;

SET @add_in_transit_package_chargeable_weight = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'in_transit_package'
          AND column_name = 'chargeable_weight_kg'
    ),
    'SELECT ''in_transit_package_chargeable_weight_kg_exists'' AS stage',
    'ALTER TABLE `in_transit_package` ADD COLUMN `chargeable_weight_kg` DECIMAL(18,6) DEFAULT NULL AFTER `volume_weight_kg`'
);
PREPARE add_in_transit_package_chargeable_weight_stmt FROM @add_in_transit_package_chargeable_weight;
EXECUTE add_in_transit_package_chargeable_weight_stmt;
DEALLOCATE PREPARE add_in_transit_package_chargeable_weight_stmt;
