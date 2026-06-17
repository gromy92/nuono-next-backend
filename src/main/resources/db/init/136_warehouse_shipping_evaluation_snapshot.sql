-- Warehouse shipping evaluation snapshots.
-- Scope: explicit target-forwarder evaluation fields for freight-plan drafts.

SET NAMES utf8mb4;

SET @add_shipping_source_product_length_cm = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_batch_source' AND column_name = 'product_length_cm'),
    'SELECT ''shipping_source_product_length_cm_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_batch_source` ADD COLUMN `product_length_cm` DECIMAL(12, 3) DEFAULT NULL AFTER `spec_status`'
);
PREPARE add_shipping_source_product_length_cm_stmt FROM @add_shipping_source_product_length_cm;
EXECUTE add_shipping_source_product_length_cm_stmt;
DEALLOCATE PREPARE add_shipping_source_product_length_cm_stmt;

SET @add_shipping_source_product_width_cm = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_batch_source' AND column_name = 'product_width_cm'),
    'SELECT ''shipping_source_product_width_cm_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_batch_source` ADD COLUMN `product_width_cm` DECIMAL(12, 3) DEFAULT NULL AFTER `product_length_cm`'
);
PREPARE add_shipping_source_product_width_cm_stmt FROM @add_shipping_source_product_width_cm;
EXECUTE add_shipping_source_product_width_cm_stmt;
DEALLOCATE PREPARE add_shipping_source_product_width_cm_stmt;

SET @add_shipping_source_product_height_cm = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_batch_source' AND column_name = 'product_height_cm'),
    'SELECT ''shipping_source_product_height_cm_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_batch_source` ADD COLUMN `product_height_cm` DECIMAL(12, 3) DEFAULT NULL AFTER `product_width_cm`'
);
PREPARE add_shipping_source_product_height_cm_stmt FROM @add_shipping_source_product_height_cm;
EXECUTE add_shipping_source_product_height_cm_stmt;
DEALLOCATE PREPARE add_shipping_source_product_height_cm_stmt;

SET @add_shipping_source_product_weight_g = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_batch_source' AND column_name = 'product_weight_g'),
    'SELECT ''shipping_source_product_weight_g_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_batch_source` ADD COLUMN `product_weight_g` DECIMAL(12, 3) DEFAULT NULL AFTER `product_height_cm`'
);
PREPARE add_shipping_source_product_weight_g_stmt FROM @add_shipping_source_product_weight_g;
EXECUTE add_shipping_source_product_weight_g_stmt;
DEALLOCATE PREPARE add_shipping_source_product_weight_g_stmt;

SET @add_shipping_source_profile_status = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_batch_source' AND column_name = 'logistics_profile_status'),
    'SELECT ''shipping_source_profile_status_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_batch_source` ADD COLUMN `logistics_profile_status` VARCHAR(40) DEFAULT NULL AFTER `product_weight_g`'
);
PREPARE add_shipping_source_profile_status_stmt FROM @add_shipping_source_profile_status;
EXECUTE add_shipping_source_profile_status_stmt;
DEALLOCATE PREPARE add_shipping_source_profile_status_stmt;

SET @add_shipping_source_sensitive_flag = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_batch_source' AND column_name = 'sensitive_flag'),
    'SELECT ''shipping_source_sensitive_flag_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_batch_source` ADD COLUMN `sensitive_flag` BIT(1) NOT NULL DEFAULT b''0'' AFTER `logistics_profile_status`'
);
PREPARE add_shipping_source_sensitive_flag_stmt FROM @add_shipping_source_sensitive_flag;
EXECUTE add_shipping_source_sensitive_flag_stmt;
DEALLOCATE PREPARE add_shipping_source_sensitive_flag_stmt;

SET @add_shipping_source_sensitive_reason = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_batch_source' AND column_name = 'sensitive_reason_json'),
    'SELECT ''shipping_source_sensitive_reason_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_batch_source` ADD COLUMN `sensitive_reason_json` TEXT DEFAULT NULL AFTER `sensitive_flag`'
);
PREPARE add_shipping_source_sensitive_reason_stmt FROM @add_shipping_source_sensitive_reason;
EXECUTE add_shipping_source_sensitive_reason_stmt;
DEALLOCATE PREPARE add_shipping_source_sensitive_reason_stmt;

SET @add_shipping_option_eval = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_suggestion_option' AND column_name = 'forwarder_plan_type'),
    'SELECT ''shipping_option_eval_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_suggestion_option` ADD COLUMN `forwarder_plan_type` VARCHAR(40) DEFAULT NULL AFTER `warning_count`, ADD COLUMN `auto_recommended` BIT(1) NOT NULL DEFAULT b''0'' AFTER `forwarder_plan_type`, ADD COLUMN `target_forwarder_codes_json` TEXT DEFAULT NULL AFTER `auto_recommended`, ADD COLUMN `target_forwarder_names_json` TEXT DEFAULT NULL AFTER `target_forwarder_codes_json`, ADD COLUMN `route_codes_json` TEXT DEFAULT NULL AFTER `target_forwarder_names_json`, ADD COLUMN `evaluation_status` VARCHAR(40) NOT NULL DEFAULT ''PENDING'' AFTER `route_codes_json`, ADD COLUMN `blocked_reasons_json` TEXT DEFAULT NULL AFTER `evaluation_status`, ADD COLUMN `actual_weight_kg` DECIMAL(12, 3) DEFAULT NULL AFTER `blocked_reasons_json`, ADD COLUMN `volume_cbm` DECIMAL(12, 4) DEFAULT NULL AFTER `actual_weight_kg`, ADD COLUMN `chargeable_weight_kg` DECIMAL(12, 3) DEFAULT NULL AFTER `volume_cbm`, ADD COLUMN `estimated_total_amount` DECIMAL(14, 4) DEFAULT NULL AFTER `chargeable_weight_kg`, ADD COLUMN `avg_unit_amount` DECIMAL(14, 4) DEFAULT NULL AFTER `estimated_total_amount`, ADD COLUMN `currency` VARCHAR(20) DEFAULT NULL AFTER `avg_unit_amount`, ADD COLUMN `cost_snapshot_json` LONGTEXT DEFAULT NULL AFTER `currency`'
);
PREPARE add_shipping_option_eval_stmt FROM @add_shipping_option_eval;
EXECUTE add_shipping_option_eval_stmt;
DEALLOCATE PREPARE add_shipping_option_eval_stmt;

SET @add_shipping_line_eval = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'warehouse_shipping_suggestion_line' AND column_name = 'target_forwarder_code'),
    'SELECT ''shipping_line_eval_exists'' AS stage',
    'ALTER TABLE `warehouse_shipping_suggestion_line` ADD COLUMN `target_forwarder_code` VARCHAR(50) DEFAULT NULL AFTER `spec_status`, ADD COLUMN `target_forwarder_name` VARCHAR(120) DEFAULT NULL AFTER `target_forwarder_code`, ADD COLUMN `route_code` VARCHAR(120) DEFAULT NULL AFTER `target_forwarder_name`, ADD COLUMN `route_name` VARCHAR(255) DEFAULT NULL AFTER `route_code`, ADD COLUMN `actual_weight_kg` DECIMAL(12, 3) DEFAULT NULL AFTER `route_name`, ADD COLUMN `volume_cbm` DECIMAL(12, 4) DEFAULT NULL AFTER `actual_weight_kg`, ADD COLUMN `chargeable_weight_kg` DECIMAL(12, 3) DEFAULT NULL AFTER `volume_cbm`, ADD COLUMN `estimated_amount` DECIMAL(14, 4) DEFAULT NULL AFTER `chargeable_weight_kg`, ADD COLUMN `currency` VARCHAR(20) DEFAULT NULL AFTER `estimated_amount`'
);
PREPARE add_shipping_line_eval_stmt FROM @add_shipping_line_eval;
EXECUTE add_shipping_line_eval_stmt;
DEALLOCATE PREPARE add_shipping_line_eval_stmt;

UPDATE `warehouse_shipping_batch_source` source
LEFT JOIN `product_variant_spec` spec
    ON spec.variant_id = source.product_variant_id
   AND spec.is_deleted = b'0'
LEFT JOIN `product_variant_spec_source` spec_source
    ON spec_source.id = spec.effective_source_id
   AND spec_source.is_deleted = b'0'
LEFT JOIN `product_variant_logistics_profile` profile
    ON profile.variant_id = source.product_variant_id
   AND profile.is_deleted = b'0'
SET source.product_length_cm = COALESCE(source.product_length_cm, spec_source.product_length_cm, spec.product_length_cm),
    source.product_width_cm = COALESCE(source.product_width_cm, spec_source.product_width_cm, spec.product_width_cm),
    source.product_height_cm = COALESCE(source.product_height_cm, spec_source.product_height_cm, spec.product_height_cm),
    source.product_weight_g = COALESCE(source.product_weight_g, spec_source.product_weight_g, spec.product_weight_g),
    source.logistics_profile_status = COALESCE(source.logistics_profile_status, profile.profile_status)
WHERE source.is_deleted = b'0'
  AND (
      source.product_length_cm IS NULL
      OR source.product_width_cm IS NULL
      OR source.product_height_cm IS NULL
      OR source.product_weight_g IS NULL
      OR source.logistics_profile_status IS NULL
  );
