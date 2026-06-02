CREATE TABLE IF NOT EXISTS `sales_data_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sales_data_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES
    ('sales_import_batch', 10000, NOW(), NOW()),
    ('sales_import_exception', 30000, NOW(), NOW()),
    ('daily_sales_fact', 100000, NOW(), NOW()),
    ('sales_sync_task', 20000, NOW(), NOW()),
    ('sales_activity_window', 40000, NOW(), NOW()),
    ('sales_forecast_run', 50000, NOW(), NOW()),
    ('sales_forecast_result', 60000, NOW(), NOW()),
    ('sales_forecast_follow_up', 61000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `next_id` = `next_id`,
    `gmt_updated` = VALUES(`gmt_updated`);

CREATE TABLE IF NOT EXISTS `sales_import_batch` (
    `id` BIGINT NOT NULL,
    `source_system` VARCHAR(80) NOT NULL,
    `source_filename` VARCHAR(512) DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `report_date_from` DATE DEFAULT NULL,
    `report_date_to` DATE DEFAULT NULL,
    `total_rows` INT NOT NULL DEFAULT 0,
    `success_rows` INT NOT NULL DEFAULT 0,
    `failure_rows` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(40) NOT NULL DEFAULT 'imported',
    `failure_summary_json` LONGTEXT DEFAULT NULL,
    `imported_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sales_import_batch_scope` (`owner_user_id`, `store_code`, `site_code`, `report_date_from`, `report_date_to`),
    KEY `idx_sales_import_batch_source` (`source_system`, `source_filename`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sales_activity_window` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `name` VARCHAR(160) NOT NULL,
    `activity_type` VARCHAR(40) NOT NULL,
    `category_scope` VARCHAR(160) DEFAULT NULL,
    `date_from` DATE NOT NULL,
    `date_to` DATE NOT NULL,
    `factor` DECIMAL(10,4) NOT NULL DEFAULT 1.0000,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `version_no` INT NOT NULL DEFAULT 1,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sales_activity_window_scope` (`owner_user_id`, `store_code`, `site_code`, `date_from`, `date_to`),
    KEY `idx_sales_activity_window_enabled` (`enabled`, `site_code`, `date_from`, `date_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sales_import_exception` (
    `id` BIGINT NOT NULL,
    `source_batch_id` BIGINT NOT NULL,
    `source_filename` VARCHAR(512) DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `row_number` INT NOT NULL,
    `exception_type` VARCHAR(80) NOT NULL,
    `field_name` VARCHAR(120) DEFAULT NULL,
    `source_value` VARCHAR(1000) DEFAULT NULL,
    `source_context` LONGTEXT DEFAULT NULL,
    `message` VARCHAR(1000) NOT NULL,
    `resolution_hint` VARCHAR(1000) DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sales_import_exception_batch` (`source_batch_id`),
    KEY `idx_sales_import_exception_scope` (`owner_user_id`, `store_code`, `site_code`, `exception_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `daily_sales_fact` (
    `id` BIGINT NOT NULL,
    `source_system` VARCHAR(80) NOT NULL,
    `source_batch_id` BIGINT DEFAULT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `fact_date` DATE NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) NOT NULL,
    `sku_config` VARCHAR(160) DEFAULT NULL,
    `country_code` VARCHAR(20) DEFAULT NULL,
    `currency_code` VARCHAR(20) DEFAULT NULL,
    `product_title` VARCHAR(1000) DEFAULT NULL,
    `your_visitors` INT DEFAULT NULL,
    `total_visitors` INT DEFAULT NULL,
    `gross_units` INT DEFAULT NULL,
    `shipped_units` INT DEFAULT NULL,
    `cancelled_units` INT DEFAULT NULL,
    `net_units` INT NOT NULL DEFAULT 0,
    `revenue_shipped` DECIMAL(18,6) DEFAULT NULL,
    `buy_box_visitor_percentage` DECIMAL(10,4) DEFAULT NULL,
    `conversion_visitors_percentage` DECIMAL(10,4) DEFAULT NULL,
    `asp_shipped_percentage` DECIMAL(18,6) DEFAULT NULL,
    `source_row_hash` VARCHAR(128) DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_daily_sales_fact_source_scope` (
        `source_system`,
        `owner_user_id`,
        `store_code`,
        `site_code`,
        `fact_date`,
        `partner_sku`,
        `sku`
    ),
    KEY `idx_daily_sales_fact_scope_date` (`owner_user_id`, `store_code`, `site_code`, `fact_date`),
    KEY `idx_daily_sales_fact_product` (`owner_user_id`, `partner_sku`, `sku`),
    KEY `idx_daily_sales_fact_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sales_forecast_run` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `source_data_date` DATE NOT NULL,
    `calculation_version` VARCHAR(80) NOT NULL,
    `config_version` VARCHAR(80) NOT NULL,
    `calendar_version_no` VARCHAR(120) DEFAULT NULL,
    `calendar_version_name` VARCHAR(160) DEFAULT NULL,
    `calendar_version_source_label` VARCHAR(120) DEFAULT NULL,
    `lifecycle_version_no` VARCHAR(120) DEFAULT NULL,
    `lifecycle_version_name` VARCHAR(160) DEFAULT NULL,
    `lifecycle_version_source_label` VARCHAR(120) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'succeeded',
    `result_count` INT NOT NULL DEFAULT 0,
    `calculated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sales_forecast_run_scope` (
        `owner_user_id`,
        `store_code`,
        `site_code`,
        `source_data_date`,
        `calculation_version`,
        `config_version`
    ),
    KEY `idx_sales_forecast_run_status` (`status`, `calculated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sales_forecast_result` (
    `id` BIGINT NOT NULL,
    `run_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) NOT NULL,
    `product_title` VARCHAR(1000) DEFAULT NULL,
    `latest_fact_date` DATE NOT NULL,
    `history_units_7` INT NOT NULL DEFAULT 0,
    `history_units_30` INT NOT NULL DEFAULT 0,
    `history_units_60` INT NOT NULL DEFAULT 0,
    `history_units_90` INT NOT NULL DEFAULT 0,
    `observed_days` INT NOT NULL DEFAULT 0,
    `current_stock` INT DEFAULT NULL,
    `stock_cover_days` DECIMAL(12,2) DEFAULT NULL,
    `forecast_units_30` INT NOT NULL,
    `forecast_units_60` INT NOT NULL DEFAULT 0,
    `forecast_units_90` INT NOT NULL DEFAULT 0,
    `lifecycle_code` VARCHAR(40) DEFAULT NULL,
    `lifecycle_label` VARCHAR(80) DEFAULT NULL,
    `calculation_version` VARCHAR(80) NOT NULL,
    `config_version` VARCHAR(80) NOT NULL,
    `base_daily_sales` DECIMAL(18,6) DEFAULT NULL,
    `recent_daily_trend_rate` DECIMAL(10,4) DEFAULT NULL,
    `trend_factor` DECIMAL(10,4) DEFAULT NULL,
    `lifecycle_factor` DECIMAL(10,4) DEFAULT NULL,
    `future_factor` DECIMAL(10,4) DEFAULT NULL,
    `lifecycle_explanation` VARCHAR(1000) DEFAULT NULL,
    `confidence_level` VARCHAR(40) DEFAULT NULL,
    `confidence_label` VARCHAR(80) DEFAULT NULL,
    `confidence_explanation` VARCHAR(1000) DEFAULT NULL,
    `warning_codes` VARCHAR(1000) DEFAULT NULL,
    `risk_codes` VARCHAR(1000) DEFAULT NULL,
    `activity_window_summary` VARCHAR(1000) DEFAULT NULL,
    `activity_explanation` VARCHAR(1000) DEFAULT NULL,
    `short_reason` VARCHAR(1000) DEFAULT NULL,
    `feature_snapshot_json` LONGTEXT DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sales_forecast_result_run_product` (`run_id`, `partner_sku`, `sku`),
    KEY `idx_sales_forecast_result_run` (`run_id`),
    KEY `idx_sales_forecast_result_scope` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sales_forecast_follow_up` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `partner_sku` VARCHAR(160) NOT NULL,
    `sku` VARCHAR(160) NOT NULL,
    `marked` TINYINT(1) NOT NULL DEFAULT 1,
    `marked_by` BIGINT DEFAULT NULL,
    `marked_at` DATETIME DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sales_forecast_follow_up_product` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `sku`),
    KEY `idx_sales_forecast_follow_up_scope` (`owner_user_id`, `store_code`, `site_code`, `marked`, `gmt_updated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @sales_forecast_run_add_calendar_version_no := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_run'
              AND COLUMN_NAME = 'calendar_version_no'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_run` ADD COLUMN `calendar_version_no` VARCHAR(120) DEFAULT NULL AFTER `config_version`'
    )
);
PREPARE stmt FROM @sales_forecast_run_add_calendar_version_no;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_run_add_calendar_version_name := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_run'
              AND COLUMN_NAME = 'calendar_version_name'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_run` ADD COLUMN `calendar_version_name` VARCHAR(160) DEFAULT NULL AFTER `calendar_version_no`'
    )
);
PREPARE stmt FROM @sales_forecast_run_add_calendar_version_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_run_add_calendar_version_source_label := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_run'
              AND COLUMN_NAME = 'calendar_version_source_label'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_run` ADD COLUMN `calendar_version_source_label` VARCHAR(120) DEFAULT NULL AFTER `calendar_version_name`'
    )
);
PREPARE stmt FROM @sales_forecast_run_add_calendar_version_source_label;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_run_add_lifecycle_version_no := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_run'
              AND COLUMN_NAME = 'lifecycle_version_no'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_run` ADD COLUMN `lifecycle_version_no` VARCHAR(120) DEFAULT NULL AFTER `calendar_version_source_label`'
    )
);
PREPARE stmt FROM @sales_forecast_run_add_lifecycle_version_no;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_run_add_lifecycle_version_name := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_run'
              AND COLUMN_NAME = 'lifecycle_version_name'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_run` ADD COLUMN `lifecycle_version_name` VARCHAR(160) DEFAULT NULL AFTER `lifecycle_version_no`'
    )
);
PREPARE stmt FROM @sales_forecast_run_add_lifecycle_version_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_run_add_lifecycle_version_source_label := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_run'
              AND COLUMN_NAME = 'lifecycle_version_source_label'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_run` ADD COLUMN `lifecycle_version_source_label` VARCHAR(120) DEFAULT NULL AFTER `lifecycle_version_name`'
    )
);
PREPARE stmt FROM @sales_forecast_run_add_lifecycle_version_source_label;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_history_units_60 := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'history_units_60'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `history_units_60` INT NOT NULL DEFAULT 0 AFTER `history_units_30`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_history_units_60;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_observed_days := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'observed_days'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `observed_days` INT NOT NULL DEFAULT 0 AFTER `history_units_90`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_observed_days;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_current_stock := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'current_stock'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `current_stock` INT DEFAULT NULL AFTER `observed_days`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_current_stock;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_stock_cover_days := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'stock_cover_days'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `stock_cover_days` DECIMAL(12,2) DEFAULT NULL AFTER `current_stock`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_stock_cover_days;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_forecast_units_60 := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'forecast_units_60'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `forecast_units_60` INT NOT NULL DEFAULT 0 AFTER `forecast_units_30`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_forecast_units_60;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_forecast_units_90 := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'forecast_units_90'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `forecast_units_90` INT NOT NULL DEFAULT 0 AFTER `forecast_units_60`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_forecast_units_90;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_base_daily_sales := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'base_daily_sales'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `base_daily_sales` DECIMAL(18,6) DEFAULT NULL AFTER `config_version`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_base_daily_sales;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_recent_daily_trend_rate := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'recent_daily_trend_rate'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `recent_daily_trend_rate` DECIMAL(10,4) DEFAULT NULL AFTER `base_daily_sales`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_recent_daily_trend_rate;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_trend_factor := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'trend_factor'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `trend_factor` DECIMAL(10,4) DEFAULT NULL AFTER `recent_daily_trend_rate`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_trend_factor;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_lifecycle_factor := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'lifecycle_factor'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `lifecycle_factor` DECIMAL(10,4) DEFAULT NULL AFTER `trend_factor`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_lifecycle_factor;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_future_factor := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'future_factor'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `future_factor` DECIMAL(10,4) DEFAULT NULL AFTER `lifecycle_factor`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_future_factor;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_lifecycle_explanation := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'lifecycle_explanation'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `lifecycle_explanation` VARCHAR(1000) DEFAULT NULL AFTER `future_factor`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_lifecycle_explanation;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_confidence_level := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'confidence_level'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `confidence_level` VARCHAR(40) DEFAULT NULL AFTER `lifecycle_explanation`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_confidence_level;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_confidence_label := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'confidence_label'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `confidence_label` VARCHAR(80) DEFAULT NULL AFTER `confidence_level`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_confidence_label;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_confidence_explanation := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'confidence_explanation'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `confidence_explanation` VARCHAR(1000) DEFAULT NULL AFTER `confidence_label`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_confidence_explanation;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_warning_codes := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'warning_codes'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `warning_codes` VARCHAR(1000) DEFAULT NULL AFTER `confidence_explanation`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_warning_codes;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_risk_codes := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'risk_codes'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `risk_codes` VARCHAR(1000) DEFAULT NULL AFTER `warning_codes`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_risk_codes;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_activity_window_summary := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'activity_window_summary'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `activity_window_summary` VARCHAR(1000) DEFAULT NULL AFTER `risk_codes`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_activity_window_summary;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sales_forecast_result_add_activity_explanation := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sales_forecast_result'
              AND COLUMN_NAME = 'activity_explanation'
        ),
        'SELECT 1',
        'ALTER TABLE `sales_forecast_result` ADD COLUMN `activity_explanation` VARCHAR(1000) DEFAULT NULL AFTER `activity_window_summary`'
    )
);
PREPARE stmt FROM @sales_forecast_result_add_activity_explanation;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `sales_sync_task` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `logical_store_id` BIGINT DEFAULT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `date_from` DATE NOT NULL,
    `date_to` DATE NOT NULL,
    `requested_by` BIGINT DEFAULT NULL,
    `trigger_type` VARCHAR(40) DEFAULT NULL,
    `status` VARCHAR(40) NOT NULL DEFAULT 'queued',
    `source_batch_id` BIGINT DEFAULT NULL,
    `total_rows` INT DEFAULT NULL,
    `success_rows` INT DEFAULT NULL,
    `failure_rows` INT DEFAULT NULL,
    `latest_fact_date` DATE DEFAULT NULL,
    `export_code` VARCHAR(120) DEFAULT NULL,
    `export_status` VARCHAR(40) DEFAULT NULL,
    `export_download_url` VARCHAR(1000) DEFAULT NULL,
    `failure_reason` VARCHAR(1000) DEFAULT NULL,
    `queued_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sales_sync_task_scope` (`owner_user_id`, `store_code`, `site_code`, `date_from`, `date_to`),
    KEY `idx_sales_sync_task_status` (`status`, `queued_at`),
    KEY `idx_sales_sync_task_export` (`export_code`),
    KEY `idx_sales_sync_task_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
