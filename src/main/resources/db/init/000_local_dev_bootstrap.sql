-- Nuono Next local startup bootstrap
-- Purpose:
-- 1. Create the first-batch core tables directly when no local legacy reference schema exists
-- 2. Seed a small but representative local sample dataset
-- 3. Make `local-db` startup possible on a clean machine

CREATE DATABASE IF NOT EXISTS nuono_new_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nuono_new_dev;

CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL COMMENT 'è§’è‰²åç§°',
    `code` VARCHAR(50) NOT NULL COMMENT 'è§’è‰²ç¼–ç ',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '角色说明',
    `is_system` BIT(1) DEFAULT b'0' COMMENT '系统预设角色不可删除',
    `parent_id` BIGINT DEFAULT 0 COMMENT '上级角色ID，0为顶级',
    `level` INT DEFAULT 0 COMMENT '层级深度(0-3)',
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²è¡¨';

CREATE TABLE IF NOT EXISTS `menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `parent_id` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `url_path` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '是否删除',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL COMMENT 'è§’è‰²ID',
    `menu_id` BIGINT NOT NULL COMMENT 'èœå•ID',
    `is_deleted` BIT(1) DEFAULT b'0',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²èœå•å…³ç³»è¡¨';

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `phone` VARCHAR(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `email` VARCHAR(125) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `account_no` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录账号',
    `password` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录密码',
    `token` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `role` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色 admin:管理员  user:普通用户',
    `role_id` BIGINT DEFAULT NULL COMMENT 'è§’è‰²ID',
    `account_type` VARCHAR(10) COLLATE utf8mb4_unicode_ci DEFAULT 'external' COMMENT '账号类型：internal/external',
    `real_name` VARCHAR(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '真实姓名',
    `company_name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '公司名称',
    `list_limit` INT DEFAULT '0' COMMENT '上架额度',
    `collect_limit` INT DEFAULT '0' COMMENT '采集额度',
    `wh_ap_limit` INT DEFAULT '0' COMMENT '约仓额度',
    `chatgpt_translate_limit` INT DEFAULT '0' COMMENT 'chatGpt图片翻译额度',
    `level` INT DEFAULT NULL COMMENT '用户等级  1-VIP  2-SVIP  3-超级VIP',
    `noon_partner_user` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon商家登录名',
    `noon_partner_project_user` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon商家project user',
    `noon_partner_pwd` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon商家登录密码',
    `noon_partner_encrypted_pwd` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon商家登录加密密码',
    `noon_partner_cookie` VARCHAR(5000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon商家登录cookie',
    `cookie_generate_time` DATETIME DEFAULT NULL COMMENT 'noon商家登录cookie生成时间',
    `noon_partner_id` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `noon_partner_user_code` VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `noon_partner_mail_auth_code` VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon商家登录邮箱授权码',
    `status` INT DEFAULT '1' COMMENT '1-启用 0-禁用',
    `effective_time` DATETIME DEFAULT NULL,
    `expired_time` DATETIME DEFAULT NULL COMMENT '过期时间',
    `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '是否删除',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

SET @user_expand_role := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user'
              AND COLUMN_NAME = 'role'
              AND CHARACTER_MAXIMUM_LENGTH < 30
        ),
        'ALTER TABLE `user` MODIFY COLUMN `role` VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''角色 admin:管理员  user:普通用户''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @user_expand_role;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `user_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT DEFAULT NULL,
    `menu_id` BIGINT DEFAULT NULL,
    `effective_time` DATETIME DEFAULT NULL,
    `expired_time` DATETIME DEFAULT NULL,
    `status` INT DEFAULT NULL COMMENT '1-启用  2-关闭',
    `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '是否删除',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_store` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `org_code` VARCHAR(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon店铺ID',
    `org_name` VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'noon店铺名称',
    `project_code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `project_name` VARCHAR(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `store_code` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `site` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '站点',
    `is_authorized` TINYINT(1) DEFAULT NULL COMMENT '是否授权',
    `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '是否删除',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `merchant_payment` (
    `id` BIGINT NOT NULL,
    `merchant_user_id` BIGINT NOT NULL,
    `amount` DECIMAL(12,2) NOT NULL,
    `payment_date` DATE NOT NULL,
    `remark` VARCHAR(255) DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_merchant_payment_user_id` (`merchant_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `store_initialization_snapshot` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(100) NOT NULL,
    `project_code` VARCHAR(100) DEFAULT NULL,
    `project_name` VARCHAR(100) DEFAULT NULL,
    `status` VARCHAR(30) DEFAULT NULL,
    `last_initialized_at` DATETIME DEFAULT NULL,
    `snapshot_json` LONGTEXT NOT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_store_initialization_snapshot_owner_store` (`owner_user_id`, `store_code`),
    KEY `idx_store_initialization_snapshot_store_code` (`store_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_order` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `order_no` VARCHAR(50) NOT NULL,
    `title` VARCHAR(200) DEFAULT NULL,
    `status` VARCHAR(30) DEFAULT 'SCREENING',
    `target_market` VARCHAR(20) DEFAULT NULL,
    `priority` VARCHAR(20) DEFAULT 'NORMAL',
    `source_type` VARCHAR(30) DEFAULT 'LINK_LIST',
    `item_count` INT DEFAULT 0,
    `selected_candidate_count` INT DEFAULT 0,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder` (
    `id` BIGINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `alias` VARCHAR(200) DEFAULT NULL,
    `company_name` VARCHAR(200) DEFAULT NULL,
    `status` VARCHAR(30) DEFAULT 'ACTIVE',
    `contact_info_json` LONGTEXT DEFAULT NULL,
    `notes` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_forwarder_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `quote_source_bundle` (
    `id` BIGINT NOT NULL,
    `forwarder_id` BIGINT NOT NULL,
    `bundle_name` VARCHAR(200) NOT NULL,
    `analysis_status` VARCHAR(30) DEFAULT 'DRAFT',
    `analysis_summary` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_quote_source_bundle_forwarder_id` (`forwarder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `quote_source_file` (
    `id` BIGINT NOT NULL,
    `bundle_id` BIGINT NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_type` VARCHAR(30) DEFAULT NULL,
    `file_path` VARCHAR(500) DEFAULT NULL,
    `file_hash` VARCHAR(100) DEFAULT NULL,
    `page_count` INT DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_quote_source_file_bundle_id` (`bundle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `quote_source_note` (
    `id` BIGINT NOT NULL,
    `bundle_id` BIGINT NOT NULL,
    `note_type` VARCHAR(30) DEFAULT NULL,
    `source_channel` VARCHAR(50) DEFAULT NULL,
    `content` LONGTEXT NOT NULL,
    `author_name` VARCHAR(100) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_quote_source_note_bundle_id` (`bundle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_quote_version` (
    `id` BIGINT NOT NULL,
    `forwarder_id` BIGINT NOT NULL,
    `bundle_id` BIGINT NOT NULL,
    `version_no` VARCHAR(50) NOT NULL,
    `effective_from` DATE DEFAULT NULL,
    `effective_to` DATE DEFAULT NULL,
    `status` VARCHAR(30) DEFAULT 'DRAFT',
    `summary` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_forwarder_quote_version_forwarder_id` (`forwarder_id`),
    KEY `idx_forwarder_quote_version_bundle_id` (`bundle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_service` (
    `id` BIGINT NOT NULL,
    `quote_version_id` BIGINT NOT NULL,
    `service_name` VARCHAR(200) NOT NULL,
    `country_code` VARCHAR(10) DEFAULT NULL,
    `route_code` VARCHAR(30) DEFAULT NULL,
    `transport_mode` VARCHAR(20) DEFAULT NULL,
    `business_type` VARCHAR(30) DEFAULT NULL,
    `service_scope` VARCHAR(50) DEFAULT NULL,
    `is_tax_included` BIT(1) DEFAULT NULL,
    `is_delivery_included` BIT(1) DEFAULT NULL,
    `transit_time_text` VARCHAR(100) DEFAULT NULL,
    `remarks` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_forwarder_service_quote_version_id` (`quote_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_quote_rule` (
    `id` BIGINT NOT NULL,
    `service_id` BIGINT NOT NULL,
    `rule_name` VARCHAR(200) NOT NULL,
    `rule_type` VARCHAR(50) DEFAULT NULL,
    `cargo_category_l1` VARCHAR(100) DEFAULT NULL,
    `cargo_category_l2` VARCHAR(100) DEFAULT NULL,
    `cargo_keywords_json` LONGTEXT DEFAULT NULL,
    `billing_unit` VARCHAR(30) DEFAULT NULL,
    `currency` VARCHAR(10) DEFAULT NULL,
    `unit_price` DECIMAL(10,2) DEFAULT NULL,
    `min_charge_qty` DECIMAL(10,2) DEFAULT NULL,
    `min_charge_amount` DECIMAL(10,2) DEFAULT NULL,
    `calc_basis` VARCHAR(50) DEFAULT NULL,
    `volume_divisor` DECIMAL(10,2) DEFAULT NULL,
    `weight_volume_ratio` VARCHAR(50) DEFAULT NULL,
    `rounding_mode` VARCHAR(30) DEFAULT NULL,
    `rounding_precision` DECIMAL(10,2) DEFAULT NULL,
    `start_qty` DECIMAL(10,2) DEFAULT NULL,
    `end_qty` DECIMAL(10,2) DEFAULT NULL,
    `priority` INT DEFAULT 0,
    `is_active` BIT(1) DEFAULT b'1',
    `remarks` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_forwarder_quote_rule_service_id` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_restriction_rule` (
    `id` BIGINT NOT NULL,
    `service_id` BIGINT NOT NULL,
    `restriction_type` VARCHAR(50) DEFAULT NULL,
    `restriction_operator` VARCHAR(20) DEFAULT NULL,
    `restriction_value` VARCHAR(100) DEFAULT NULL,
    `unit` VARCHAR(20) DEFAULT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `severity` VARCHAR(20) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_forwarder_restriction_rule_service_id` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `quote_evidence_ref` (
    `id` BIGINT NOT NULL,
    `quote_version_id` BIGINT NOT NULL,
    `target_type` VARCHAR(30) DEFAULT NULL,
    `target_id` BIGINT DEFAULT NULL,
    `source_type` VARCHAR(30) DEFAULT NULL,
    `source_id` BIGINT DEFAULT NULL,
    `locator` VARCHAR(100) DEFAULT NULL,
    `evidence_text` VARCHAR(500) DEFAULT NULL,
    `confidence_score` DECIMAL(5,2) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_quote_evidence_ref_quote_version_id` (`quote_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_reputation_snapshot` (
    `id` BIGINT NOT NULL,
    `forwarder_id` BIGINT NOT NULL,
    `bundle_id` BIGINT DEFAULT NULL,
    `snapshot_date` DATE DEFAULT NULL,
    `analysis_version` VARCHAR(50) DEFAULT NULL,
    `overall_score` INT DEFAULT NULL,
    `compliance_score` INT DEFAULT NULL,
    `timeliness_score` INT DEFAULT NULL,
    `price_transparency_score` INT DEFAULT NULL,
    `claims_score` INT DEFAULT NULL,
    `service_score` INT DEFAULT NULL,
    `source_confidence_score` DECIMAL(5,2) DEFAULT NULL,
    `major_red_flags_json` LONGTEXT DEFAULT NULL,
    `recent_risk_summary` VARCHAR(500) DEFAULT NULL,
    `positive_summary` VARCHAR(500) DEFAULT NULL,
    `negative_summary` VARCHAR(500) DEFAULT NULL,
    `recommendation_level` VARCHAR(10) DEFAULT NULL,
    `analysis_summary` VARCHAR(500) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_forwarder_reputation_snapshot_forwarder_id` (`forwarder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_reputation_signal` (
    `id` BIGINT NOT NULL,
    `snapshot_id` BIGINT NOT NULL,
    `forwarder_id` BIGINT NOT NULL,
    `signal_type` VARCHAR(30) DEFAULT NULL,
    `polarity` VARCHAR(20) DEFAULT NULL,
    `severity` VARCHAR(20) DEFAULT NULL,
    `source_type` VARCHAR(30) DEFAULT NULL,
    `source_channel` VARCHAR(50) DEFAULT NULL,
    `source_url_or_ref` VARCHAR(500) DEFAULT NULL,
    `event_date` DATE DEFAULT NULL,
    `captured_at` DATETIME DEFAULT NULL,
    `topic` VARCHAR(200) DEFAULT NULL,
    `evidence_text` VARCHAR(500) DEFAULT NULL,
    `confidence_score` DECIMAL(5,2) DEFAULT NULL,
    `time_decay_weight` DECIMAL(5,2) DEFAULT NULL,
    `source_weight` DECIMAL(5,2) DEFAULT NULL,
    `effective_score_impact` DECIMAL(10,2) DEFAULT NULL,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_forwarder_reputation_signal_snapshot_id` (`snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `forwarder_reputation_red_flag` (
    `id` BIGINT NOT NULL,
    `snapshot_id` BIGINT NOT NULL,
    `forwarder_id` BIGINT NOT NULL,
    `flag_type` VARCHAR(50) DEFAULT NULL,
    `severity` VARCHAR(20) DEFAULT NULL,
    `evidence_text` VARCHAR(500) DEFAULT NULL,
    `source_url_or_ref` VARCHAR(500) DEFAULT NULL,
    `event_date` DATE DEFAULT NULL,
    `is_active` BIT(1) DEFAULT b'1',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_forwarder_reputation_red_flag_snapshot_id` (`snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_demand_item` (
    `id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `line_no` INT DEFAULT 1,
    `source_platform` VARCHAR(30) DEFAULT NULL,
    `source_url` VARCHAR(800) DEFAULT NULL,
    `source_title` VARCHAR(255) DEFAULT NULL,
    `source_image_url` VARCHAR(800) DEFAULT NULL,
    `source_detail_image_url` VARCHAR(800) DEFAULT NULL,
    `source_package_image_url` VARCHAR(800) DEFAULT NULL,
    `target_price_min` DECIMAL(10,2) DEFAULT NULL,
    `target_price_max` DECIMAL(10,2) DEFAULT NULL,
    `target_quantity` INT DEFAULT NULL,
    `target_site` VARCHAR(20) DEFAULT NULL,
    `special_requirement` VARCHAR(255) DEFAULT NULL,
    `target_material` VARCHAR(1000) DEFAULT NULL,
    `target_power_mode` VARCHAR(100) DEFAULT NULL,
    `target_size_text` VARCHAR(100) DEFAULT NULL,
    `target_package_type` VARCHAR(100) DEFAULT NULL,
    `delivery_expectation` VARCHAR(100) DEFAULT NULL,
    `status` VARCHAR(30) DEFAULT 'SCREENING',
    `selected_candidate_id` BIGINT DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_demand_item_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @procurement_demand_item_add_source_detail_image_url := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'source_detail_image_url'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `source_detail_image_url` VARCHAR(800) DEFAULT NULL AFTER `source_image_url`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_source_detail_image_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_source_package_image_url := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'source_package_image_url'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `source_package_image_url` VARCHAR(800) DEFAULT NULL AFTER `source_detail_image_url`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_source_package_image_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_target_material := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'target_material'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `target_material` VARCHAR(1000) DEFAULT NULL AFTER `special_requirement`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_target_material;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_expand_target_material := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'target_material'
              AND CHARACTER_MAXIMUM_LENGTH < 1000
        ),
        'ALTER TABLE `procurement_demand_item` MODIFY COLUMN `target_material` VARCHAR(1000) DEFAULT NULL',
        'SELECT 1'
    )
);
PREPARE stmt FROM @procurement_demand_item_expand_target_material;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_target_power_mode := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'target_power_mode'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `target_power_mode` VARCHAR(100) DEFAULT NULL AFTER `target_material`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_target_power_mode;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_target_size_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'target_size_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `target_size_text` VARCHAR(100) DEFAULT NULL AFTER `target_power_mode`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_target_size_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_target_package_type := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'target_package_type'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `target_package_type` VARCHAR(100) DEFAULT NULL AFTER `target_size_text`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_target_package_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_demand_item_add_delivery_expectation := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_demand_item'
              AND COLUMN_NAME = 'delivery_expectation'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_demand_item` ADD COLUMN `delivery_expectation` VARCHAR(100) DEFAULT NULL AFTER `target_package_type`'
    )
);
PREPARE stmt FROM @procurement_demand_item_add_delivery_expectation;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `procurement_match_task` (
    `id` BIGINT NOT NULL,
    `demand_item_id` BIGINT NOT NULL,
    `status` VARCHAR(30) DEFAULT 'QUEUED',
    `progress_percent` INT DEFAULT 0,
    `search_mode` VARCHAR(30) DEFAULT NULL,
    `selected_image_count` INT DEFAULT 0,
    `search_path` VARCHAR(100) DEFAULT NULL,
    `result_count` INT DEFAULT 0,
    `recommended_count` INT DEFAULT 0,
    `message` VARCHAR(255) DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_match_task_item_id` (`demand_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_candidate` (
    `id` BIGINT NOT NULL,
    `demand_item_id` BIGINT NOT NULL,
    `task_id` BIGINT DEFAULT NULL,
    `rank_no` INT DEFAULT 1,
    `level` VARCHAR(20) DEFAULT 'review',
    `total_score` INT DEFAULT 0,
    `fit_score` INT DEFAULT 0,
    `spec_score` INT DEFAULT 0,
    `price_score` INT DEFAULT 0,
    `supplier_score` INT DEFAULT 0,
    `logistics_score` INT DEFAULT 0,
    `candidate_platform` VARCHAR(30) DEFAULT '1688',
    `candidate_url` VARCHAR(800) DEFAULT NULL,
    `title` VARCHAR(255) DEFAULT NULL,
    `supplier_name` VARCHAR(100) DEFAULT NULL,
    `price_text` VARCHAR(100) DEFAULT NULL,
    `moq_text` VARCHAR(100) DEFAULT NULL,
    `location_text` VARCHAR(100) DEFAULT NULL,
    `material_text` VARCHAR(100) DEFAULT NULL,
    `power_mode_text` VARCHAR(100) DEFAULT NULL,
    `size_text` VARCHAR(100) DEFAULT NULL,
    `package_text` VARCHAR(100) DEFAULT NULL,
    `delivery_timeline_text` VARCHAR(100) DEFAULT NULL,
    `result_card_text` VARCHAR(1000) DEFAULT NULL,
    `detail_highlight_text` VARCHAR(1000) DEFAULT NULL,
    `attribute_snapshot_text` VARCHAR(1000) DEFAULT NULL,
    `shipping_snapshot_text` VARCHAR(1000) DEFAULT NULL,
    `package_snapshot_text` VARCHAR(1000) DEFAULT NULL,
    `main_image_url` VARCHAR(800) DEFAULT NULL,
    `detail_image_url` VARCHAR(800) DEFAULT NULL,
    `delivery_image_url` VARCHAR(800) DEFAULT NULL,
    `manual_review_note` VARCHAR(1000) DEFAULT NULL,
    `inquiry_summary` VARCHAR(1000) DEFAULT NULL,
    `next_action` VARCHAR(40) DEFAULT NULL,
    `badges_text` VARCHAR(500) DEFAULT NULL,
    `reasons_text` VARCHAR(1000) DEFAULT NULL,
    `warnings_text` VARCHAR(1000) DEFAULT NULL,
    `is_selected` BIT(1) DEFAULT b'0',
    `decision_status` VARCHAR(30) DEFAULT 'PENDING',
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_candidate_item_id` (`demand_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_auto_inquiry_task` (
    `id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `demand_item_id` BIGINT NOT NULL,
    `candidate_id` BIGINT NOT NULL,
    `session_id` BIGINT DEFAULT NULL,
    `platform` VARCHAR(20) DEFAULT '1688',
    `status` VARCHAR(30) DEFAULT 'PENDING',
    `execution_stage` VARCHAR(40) DEFAULT 'CREATED',
    `attempt_no` INT DEFAULT 0,
    `max_attempts` INT DEFAULT 3,
    `target_offer_id` VARCHAR(100) DEFAULT NULL,
    `target_supplier_identity` VARCHAR(200) DEFAULT NULL,
    `target_entry_url` VARCHAR(800) DEFAULT NULL,
    `target_locator_text` VARCHAR(1000) DEFAULT NULL,
    `input_preview_text` VARCHAR(500) DEFAULT NULL,
    `input_payload_text` TEXT DEFAULT NULL,
    `input_payload_hash` VARCHAR(64) DEFAULT NULL,
    `input_locator` VARCHAR(300) DEFAULT NULL,
    `send_channel` VARCHAR(40) DEFAULT NULL,
    `send_evidence` VARCHAR(1000) DEFAULT NULL,
    `thread_checkpoint` VARCHAR(300) DEFAULT NULL,
    `last_message_digest` VARCHAR(128) DEFAULT NULL,
    `failure_code` VARCHAR(80) DEFAULT NULL,
    `failure_message` VARCHAR(500) DEFAULT NULL,
    `handoff_reason` VARCHAR(500) DEFAULT NULL,
    `message` VARCHAR(255) DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `sent_at` DATETIME DEFAULT NULL,
    `confirmed_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `last_event_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_auto_inquiry_task_item_id` (`demand_item_id`),
    KEY `idx_procurement_auto_inquiry_task_candidate_id` (`candidate_id`),
    KEY `idx_procurement_auto_inquiry_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @procurement_auto_inquiry_task_add_input_locator := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'input_locator'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `input_locator` VARCHAR(300) DEFAULT NULL AFTER `input_payload_hash`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_input_locator;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_send_channel := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'send_channel'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `send_channel` VARCHAR(40) DEFAULT NULL AFTER `input_locator`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_send_channel;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_send_evidence := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'send_evidence'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `send_evidence` VARCHAR(1000) DEFAULT NULL AFTER `send_channel`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_send_evidence;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_thread_checkpoint := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'thread_checkpoint'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `thread_checkpoint` VARCHAR(300) DEFAULT NULL AFTER `send_evidence`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_thread_checkpoint;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_last_message_digest := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'last_message_digest'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `last_message_digest` VARCHAR(128) DEFAULT NULL AFTER `thread_checkpoint`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_last_message_digest;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_sent_at := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'sent_at'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `sent_at` DATETIME DEFAULT NULL AFTER `started_at`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_sent_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_confirmed_at := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'confirmed_at'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `confirmed_at` DATETIME DEFAULT NULL AFTER `sent_at`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_confirmed_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `procurement_auto_inquiry_session` (
    `id` BIGINT NOT NULL,
    `platform` VARCHAR(20) DEFAULT '1688',
    `session_key` VARCHAR(120) NOT NULL,
    `account_label` VARCHAR(120) DEFAULT NULL,
    `status` VARCHAR(30) DEFAULT 'READY',
    `risk_code` VARCHAR(40) DEFAULT 'NORMAL',
    `leased_task_id` BIGINT DEFAULT NULL,
    `profile_path` VARCHAR(500) DEFAULT NULL,
    `browser_endpoint` VARCHAR(500) DEFAULT NULL,
    `note` VARCHAR(500) DEFAULT NULL,
    `last_checked_at` DATETIME DEFAULT NULL,
    `lease_updated_at` DATETIME DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_procurement_auto_inquiry_session_key` (`session_key`),
    KEY `idx_procurement_auto_inquiry_session_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `procurement_auto_inquiry_event` (
    `id` BIGINT NOT NULL,
    `task_id` BIGINT NOT NULL,
    `event_type` VARCHAR(60) DEFAULT NULL,
    `status_before` VARCHAR(30) DEFAULT NULL,
    `status_after` VARCHAR(30) DEFAULT NULL,
    `execution_stage` VARCHAR(40) DEFAULT NULL,
    `event_message` VARCHAR(500) DEFAULT NULL,
    `event_payload` TEXT DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_procurement_auto_inquiry_event_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @procurement_candidate_add_main_image_url := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'main_image_url'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `main_image_url` VARCHAR(800) DEFAULT NULL AFTER `location_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_main_image_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_detail_image_url := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'detail_image_url'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `detail_image_url` VARCHAR(800) DEFAULT NULL AFTER `main_image_url`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_detail_image_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_delivery_image_url := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'delivery_image_url'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `delivery_image_url` VARCHAR(800) DEFAULT NULL AFTER `detail_image_url`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_delivery_image_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_manual_review_note := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'manual_review_note'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `manual_review_note` VARCHAR(1000) DEFAULT NULL AFTER `delivery_image_url`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_manual_review_note;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_inquiry_summary := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'inquiry_summary'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `inquiry_summary` VARCHAR(1000) DEFAULT NULL AFTER `manual_review_note`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_inquiry_summary;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_next_action := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'next_action'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `next_action` VARCHAR(40) DEFAULT NULL AFTER `inquiry_summary`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_next_action;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_material_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'material_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `material_text` VARCHAR(100) DEFAULT NULL AFTER `location_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_material_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_power_mode_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'power_mode_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `power_mode_text` VARCHAR(100) DEFAULT NULL AFTER `material_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_power_mode_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_size_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'size_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `size_text` VARCHAR(100) DEFAULT NULL AFTER `power_mode_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_size_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_package_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'package_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `package_text` VARCHAR(100) DEFAULT NULL AFTER `size_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_package_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_delivery_timeline_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'delivery_timeline_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `delivery_timeline_text` VARCHAR(100) DEFAULT NULL AFTER `package_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_delivery_timeline_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_result_card_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'result_card_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `result_card_text` VARCHAR(1000) DEFAULT NULL AFTER `delivery_timeline_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_result_card_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_detail_highlight_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'detail_highlight_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `detail_highlight_text` VARCHAR(1000) DEFAULT NULL AFTER `result_card_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_detail_highlight_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_attribute_snapshot_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'attribute_snapshot_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `attribute_snapshot_text` VARCHAR(1000) DEFAULT NULL AFTER `detail_highlight_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_attribute_snapshot_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_shipping_snapshot_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'shipping_snapshot_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `shipping_snapshot_text` VARCHAR(1000) DEFAULT NULL AFTER `attribute_snapshot_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_shipping_snapshot_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_candidate_add_package_snapshot_text := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_candidate'
              AND COLUMN_NAME = 'package_snapshot_text'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_candidate` ADD COLUMN `package_snapshot_text` VARCHAR(1000) DEFAULT NULL AFTER `shipping_snapshot_text`'
    )
);
PREPARE stmt FROM @procurement_candidate_add_package_snapshot_text;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE FROM `procurement_auto_inquiry_event`;
DELETE FROM `procurement_auto_inquiry_task`;
DELETE FROM `procurement_auto_inquiry_session`;
DELETE FROM `procurement_candidate`;
DELETE FROM `procurement_match_task`;
DELETE FROM `procurement_demand_item`;
DELETE FROM `procurement_order`;
DELETE FROM `user_menu`;
DELETE FROM `role_menu`;
DELETE FROM `user_store`;
DELETE FROM `menu`;
DELETE FROM `user`;
DELETE FROM `role`;

INSERT INTO `procurement_auto_inquiry_session` (
    `id`, `platform`, `session_key`, `account_label`, `status`, `risk_code`, `leased_task_id`,
    `profile_path`, `browser_endpoint`, `note`, `last_checked_at`, `lease_updated_at`,
    `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (43999, '1688', '1688-local-chrome-send-validation', '1688本机Chrome验证会话', 'READY', 'NORMAL', NULL,
     '/Applications/Google Chrome.app', 'chrome-local://default', '发送链路验证专用；需要本机 Chrome 已登录 1688', NOW(), NOW(),
     b'0', 1, 1),
    (44001, '1688', '1688-svc-default-01', '1688服务账号-01', 'READY', 'NORMAL', NULL,
     '/srv/nuono/browser/1688-svc-default-01', 'mock://1688-svc-default-01', '本地开发默认会话样本', NOW(), NOW(),
     b'0', 1, 1),
    (44002, '1688', '1688-svc-default-02', '1688服务账号-02', 'READY', 'NORMAL', NULL,
     '/srv/nuono/browser/1688-svc-default-02', 'mock://1688-svc-default-02', '本地开发默认会话样本', NOW(), NOW(),
     b'0', 1, 1),
    (44003, '1688', '1688-svc-default-03', '1688服务账号-03', 'READY', 'NORMAL', NULL,
     '/srv/nuono/browser/1688-svc-default-03', 'mock://1688-svc-default-03', '本地开发默认会话样本', NOW(), NOW(),
     b'0', 1, 1);

INSERT INTO `role` (`id`, `name`, `code`, `description`, `is_system`, `parent_id`, `level`, `is_deleted`, `created_by`, `updated_by`)
VALUES
    (1, '系统管理员', 'SYSTEM_ADMIN', '平台最高权限', b'1', 0, 0, b'0', 1, 1),
    (2, '老板', 'BOSS', '商家负责人', b'1', 1, 1, b'0', 1, 1),
    (3, '运营主管', 'OPS_MANAGER', '管理运营团队', b'1', 2, 2, b'0', 1, 1),
    (4, '运营', 'OPS', '负责选品、上架、活动', b'1', 3, 3, b'0', 1, 1),
    (5, '采购', 'PURCHASE', '负责询价、下单、成本', b'1', 3, 3, b'0', 1, 1),
    (6, '仓管', 'WAREHOUSE', '负责入库、约仓、发货', b'1', 3, 3, b'0', 1, 1);

INSERT INTO `menu` (`id`, `name`, `parent_id`, `url_path`, `is_deleted`)
VALUES
    (6, '利润计算', 0, '/profit', b'0'),
    (9, '约仓看板', 0, '/warehouse/appointment', b'0'),
    (10, '用户管理', 0, '/user/manage', b'0'),
    (19, '任务列表', 0, '/task', b'0'),
    (21, '商品销量', 0, '/sales', b'0'),
    (22, '数据预约', 0, '/data-reservation', b'0'),
    (25, '角色分配', 0, '/user/role', b'0'),
    (26, '角色维护', 0, '/system/role', b'0'),
    (27, '菜单维护', 0, '/system/menu', b'0');

INSERT INTO `role_menu` (`id`, `role_id`, `menu_id`, `is_deleted`)
VALUES
    (1001, 1, 6, b'0'),
    (1002, 1, 9, b'0'),
    (1003, 1, 10, b'0'),
    (1004, 1, 19, b'0'),
    (1005, 1, 21, b'0'),
    (1006, 1, 22, b'0'),
    (1007, 1, 25, b'0'),
    (1008, 1, 26, b'0'),
    (1009, 1, 27, b'0'),
    (1101, 2, 6, b'0'),
    (1102, 2, 9, b'0'),
    (1103, 2, 19, b'0'),
    (1104, 2, 21, b'0'),
    (1105, 2, 22, b'0'),
    (1106, 2, 25, b'0'),
    (1107, 2, 26, b'0'),
    (1108, 2, 27, b'0'),
    (1201, 3, 6, b'0'),
    (1202, 3, 9, b'0'),
    (1203, 3, 19, b'0'),
    (1204, 3, 21, b'0'),
    (1205, 3, 22, b'0'),
    (1206, 3, 25, b'0'),
    (1301, 4, 6, b'0'),
    (1302, 4, 9, b'0'),
    (1303, 4, 19, b'0'),
    (1304, 4, 21, b'0'),
    (1305, 4, 22, b'0');

INSERT INTO `user` (
    `id`, `phone`, `email`, `account_no`, `password`, `role`, `role_id`, `account_type`, `real_name`,
    `company_name`, `list_limit`, `collect_limit`, `wh_ap_limit`, `chatgpt_translate_limit`, `level`,
    `noon_partner_user`, `noon_partner_project_user`, `noon_partner_pwd`, `noon_partner_id`,
    `noon_partner_user_code`, `noon_partner_mail_auth_code`, `status`, `effective_time`, `expired_time`,
    `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (10001, '18500000001', 'admin@nuono.local', 'admin', 'admin123', 'ADMIN', 1, 'internal', '系统管理员',
     'Nuono Next', 999, 999, 999, 999, 0,
     NULL, NULL, NULL, NULL,
     NULL, NULL, 1, NOW(), '2099-12-31 23:59:59',
     b'0', 1, 1),
    (10002, '18521524250', 'boss@demo.local', 'xingyaoqw', 'boss123', 'BOSS', 2, 'internal', '老板示例',
     'xingyao', 50, 50, 10, 20, 1,
     'nuonuo@p245027.idp.noon.partners', 'nuonuo@p245027.idp.noon.partners', 'j0vtZ%ftvG324VU(', 'P245027',
     'UC-10002', 'MAIL-10002', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10001, 10001),
    (10003, '18521524251', 'manager@demo.local', '毕翠红', 'manager123', 'OPS_MANAGER', 3, 'internal', '毕翠红',
     '松果果儿', 30, 30, 8, 12, 2,
     'bicuihong.noon', 'bicuihong.project', 'ops-pass', 'NP-10003',
     'UC-10003', 'MAIL-10003', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10002, 10002),
    (10004, '18521524252', 'ops1@demo.local', '马天龙', 'ops123', 'OPS', 4, 'internal', '马天龙',
     '松果果儿', 20, 20, 4, 6, 3,
     'matianlong.noon', 'matianlong.project', 'ops-pass', 'NP-10004',
     'UC-10004', 'MAIL-10004', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10003, 10003),
    (10005, '18660614134', 'ops2@demo.local', '18660614134', 'ops234', 'OPS', 4, 'external', '异常约仓样本',
     '松果果儿', 20, 20, 4, 6, 3,
     'appoint.demo', 'appoint.project', 'ops-pass', 'NP-10005',
     'UC-10005', 'MAIL-10005', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10003, 10003),
    (10006, '15812516142', 'legacy@demo.local', 'legacy_xingyaoqw', 'legacy123', 'BOSS', 2, 'external', '遗留绑定样本',
     '松果果儿', 30, 30, 6, 10, 1,
     'legacy.noon', NULL, 'legacy-pass', 'NP-10006',
     'UC-10006', 'MAIL-10006', 1, NOW(), '2099-12-31 23:59:59',
     b'0', 10001, 10001);

INSERT INTO `user_menu` (`id`, `user_id`, `menu_id`, `status`, `effective_time`, `expired_time`, `is_deleted`)
SELECT
    20000 + ROW_NUMBER() OVER (ORDER BY u.id, rm.menu_id) AS id,
    u.id,
    rm.menu_id,
    1,
    NOW(),
    '2099-12-31 23:59:59',
    b'0'
FROM `user` u
JOIN `role_menu` rm ON rm.role_id = u.role_id AND rm.is_deleted = b'0'
WHERE u.is_deleted = b'0';

INSERT INTO `user_store` (
    `id`, `user_id`, `org_code`, `org_name`, `project_code`, `project_name`,
    `store_code`, `site`, `is_authorized`, `is_deleted`, `created_by`, `updated_by`
)
VALUES
    (30001, 10002, 'ORG-P245027', 'xingyao', 'P245027', 'xingyao',
     'STR245027-NAE', 'AE', b'1', b'0', 10002, 10002),
    (30002, 10003, 'ORG-P245027', 'xingyao', 'P245027', 'xingyao',
     'STR245027-NAE', 'AE', b'1', b'0', 10002, 10003),
    (30003, 10004, 'ORG-001', '松果果儿', 'PJT-JED01', 'JED01 项目',
     'STORE-JED01', 'AE', b'1', b'0', 10003, 10004),
    (30004, 10005, 'ORG-001', '松果果儿', 'PJT-RUH01S', 'RUH01S 项目',
     'STORE-RUH01S', 'SA', b'1', b'0', 10003, 10005),
    (30005, 10006, 'ORG-001', '松果果儿', 'PJT-NOON-OLD', '遗留 Noon 项目',
     'STORE-NOON-OLD', 'SA', b'0', b'0', 10002, 10006);

INSERT INTO `procurement_order` (
    `id`, `owner_user_id`, `order_no`, `title`, `status`, `target_market`, `priority`, `source_type`,
    `item_count`, `selected_candidate_count`, `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
VALUES
    (40101, 10002, 'PO-VALIDATION-SEND-20260427', '自动询价发送链路验证样本 A', 'SCREENING', 'SA', 'HIGH', 'VALIDATION_SAMPLE',
     1, 0, b'0', 10002, 10002, '2026-04-16 09:30:00', '2026-04-16 09:30:00'),
    (40001, 10002, 'PO-DEMO-20260417-001', '香氛/焚香工具首批 5 款采购筛选单', 'SCREENING', 'SA', 'HIGH', 'LINK_LIST',
     5, 1, b'0', 10003, 10003, '2026-04-17 10:20:00', '2026-04-17 15:40:00');

INSERT INTO `procurement_demand_item` (
    `id`, `order_id`, `line_no`, `source_platform`, `source_url`, `source_title`, `source_image_url`, `source_detail_image_url`, `source_package_image_url`,
    `target_price_min`, `target_price_max`, `target_quantity`, `target_site`, `special_requirement`,
    `target_material`, `target_power_mode`, `target_size_text`, `target_package_type`, `delivery_expectation`,
    `status`, `selected_candidate_id`, `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
VALUES
    (41101, 40101, 1, '1688',
     'https://detail.1688.com/offer/798448779771.html?offerId=798448779771&hotSaleSkuId=5613239587877&spm=a260k.home2025.recommendpart.2',
     '自动询价发送链路验证样本 A（1688 实页）',
     NULL, NULL, NULL, 1.00, 5.00, 50, 'CN', '只用于验证服务端自动询价发送链路，不进入正常采购候选池决策',
     '复合材料', '无电', '标准卷装', '普通包装', '不限',
     'REVIEWING', NULL, b'0', 10002, 10002, '2026-04-16 09:30:00', '2026-04-16 09:30:00'),
    (41001, 40001, 1, 'amazon',
     'https://www.amazon.sa/-/en/Rechargeable-Bakhoor-Incense-Speaker-Control/dp/B0DVH1NFP3/',
     '可充电古兰经音箱焚香炉遥控礼盒款',
     NULL, NULL, NULL, 18.00, 28.00, 200, 'SA', '便携礼品感强，优先电池款，支持家居香氛场景',
     'ABS+电镀', '充电款', '手持便携', '礼盒装', '10天内',
     'REVIEWING', NULL, b'0', 10003, 10003, '2026-04-17 10:20:00', '2026-04-17 15:20:00'),
    (41002, 40001, 2, 'noon',
     'https://www.noon.com/saudi-en/usb-rechargeable-hair-electric-bakhoor-luxury-incense-burner/ZF4844D91A33B64771288Z/p/?o=zf4844d91a33b64771288z-1&shareId=41406d99-c74c-456b-bf7c-c47923de5136',
     'USB 充电轻奢焚香炉便携款',
     NULL, NULL, NULL, 12.00, 20.00, 300, 'SA', '轻小件优先，支持沙特站礼盒场景，发货稳定',
     'ABS+陶瓷内胆', '充电款', '便携小型', '礼盒装', '7天内',
     'DECIDED', 43005, b'0', 10003, 10003, '2026-04-17 10:22:00', '2026-04-17 15:35:00'),
    (41003, 40001, 3, 'noon',
     'https://www.noon.com/saudi-en/mini-elegant-arabic-oud-incense-burner-12-cm-height-for-home-and-office-bakhoor-holder-small-decore-and-fragrance/Z290A5BEA29DCD0DCB1D9Z/p/?o=z290a5bea29dcd0dcb1d9z-1&shareId=e53898ed-9999-4506-94f7-1f87a9e8448e',
     '阿拉伯风 12cm 迷你焚香炉摆件款',
     NULL, NULL, NULL, 4.00, 9.00, 500, 'SA', '小体积、装饰感强、适合作为入门款',
     '陶瓷', '无电', '12cm', '礼盒装', '12天内',
     'REVIEWING', NULL, b'0', 10003, 10003, '2026-04-17 10:24:00', '2026-04-17 15:26:00'),
    (41004, 40001, 4, 'amazon',
     'https://www.amazon.sa/-/en/Electric-Porcelain-Incense-Burner-Bakhoor/dp/B0BNNR14Z5/',
     '陶瓷插电焚香炉家居款',
     NULL, NULL, NULL, 14.00, 24.00, 220, 'SA', '陶瓷感、家居化、适合中高客单',
     '陶瓷', '插电款', '桌面款', '彩盒装', '10天内',
     'REVIEWING', NULL, b'0', 10003, 10003, '2026-04-17 10:26:00', '2026-04-17 15:18:00'),
    (41005, 40001, 5, 'noon',
     'https://www.noon.com/saudi-en/2025-new-portable-electric-incense-burner-rechargeable-with-ceramic-chamber-for-use-with-incense-charcoal/Z0362D557E964BF395564Z/p/?o=ef8df8db8aaccfbb&shareId=b2d5dd56-8a45-4435-a975-e3a684380d1d',
     '2025 新款便携充电焚香炉陶瓷胆款',
     NULL, NULL, NULL, 11.00, 19.00, 300, 'SA', '便携充电款，优先看外观一致性和发货稳定性',
     'ABS+陶瓷内胆', '充电款', '便携小型', '彩盒装', '8天内',
     'SCREENING', NULL, b'0', 10003, 10003, '2026-04-17 10:28:00', '2026-04-17 15:38:00');

INSERT INTO `procurement_match_task` (
    `id`, `demand_item_id`, `status`, `progress_percent`, `search_mode`, `selected_image_count`,
    `search_path`, `result_count`, `recommended_count`, `message`, `started_at`, `finished_at`,
    `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
VALUES
    (42001, 41001, 'SUCCESS', 100, 'IMAGE_MULTI', 4, 'IMAGE_ID_LIST_DIRECT', 18, 3,
     '已完成图搜和初筛，建议优先看前 3 条推荐结果。', '2026-04-17 10:31:00', '2026-04-17 10:34:00',
     b'0', 10003, 10003, '2026-04-17 10:31:00', '2026-04-17 10:34:00'),
    (42002, 41002, 'SUCCESS', 100, 'IMAGE_MULTI', 4, 'IMAGE_ID_LIST_DIRECT', 16, 2,
     '已完成图搜和筛选，当前有 2 条高置信推荐。', '2026-04-17 10:34:00', '2026-04-17 10:37:00',
     b'0', 10003, 10003, '2026-04-17 10:34:00', '2026-04-17 10:37:00'),
    (42003, 41003, 'SUCCESS', 100, 'IMAGE_MULTI', 3, 'IMAGE_ID_LIST_DIRECT', 14, 2,
     '结果已回收，需重点确认尺寸和材质。', '2026-04-17 10:37:00', '2026-04-17 10:40:00',
     b'0', 10003, 10003, '2026-04-17 10:37:00', '2026-04-17 10:40:00'),
    (42004, 41004, 'PARTIAL_SUCCESS', 100, 'IMAGE_SINGLE', 2, 'UPLOAD_FALLBACK', 12, 1,
     '已搜到可用候选，但价格和包装信息还偏弱。', '2026-04-17 10:40:00', '2026-04-17 10:44:00',
     b'0', 10003, 10003, '2026-04-17 10:40:00', '2026-04-17 10:44:00'),
    (42005, 41005, 'RUNNING', 72, 'IMAGE_MULTI', 3, 'IMAGE_ID_LIST_DIRECT', 0, 0,
     '正在抓取 1688 结果并做初筛，预计 1-2 分钟完成。', '2026-04-17 15:36:00', NULL,
     b'0', 10003, 10003, '2026-04-17 15:36:00', '2026-04-17 15:38:00');

INSERT INTO `procurement_candidate` (
    `id`, `demand_item_id`, `task_id`, `rank_no`, `level`, `total_score`, `fit_score`, `spec_score`, `price_score`,
    `supplier_score`, `logistics_score`, `candidate_platform`, `candidate_url`, `title`, `supplier_name`,
    `price_text`, `moq_text`, `location_text`, `material_text`, `power_mode_text`, `size_text`, `package_text`, `delivery_timeline_text`,
    `result_card_text`, `detail_highlight_text`, `attribute_snapshot_text`, `shipping_snapshot_text`, `package_snapshot_text`,
    `main_image_url`, `detail_image_url`, `delivery_image_url`,
    `manual_review_note`, `inquiry_summary`, `next_action`, `badges_text`, `reasons_text`, `warnings_text`,
    `is_selected`, `decision_status`, `is_deleted`, `created_by`, `updated_by`, `gmt_create`, `gmt_updated`
)
VALUES
    (43101, 41101, NULL, 1, 'validation', 100, 100, 100, 100, 100, 100, '1688',
     'https://detail.1688.com/offer/798448779771.html?offerId=798448779771&hotSaleSkuId=5613239587877&spm=a260k.home2025.recommendpart.2',
     '自动询价发送链路验证样本 A（苍南县永诚复合材料有限公司）', '苍南县永诚复合材料有限公司',
     '¥1.00', '50件起批', '温州', '复合材料', '无电', '标准卷装', '普通包装', '现货',
     '只用于验证服务端真实发送链路。', '不进入正常采购筛选', 'offerId=798448779771', NULL, NULL,
     NULL, NULL, NULL,
     '仅用于发送链路验收。', '系统将自动命中 1688 聊天桥并完成真实发送确认。', 'PREPARE_INQUIRY', '发送链路验证样本',
     '真实1688详情页|真实offerId|真实供应商主体', '正常采购候选池不使用本样本',
     b'0', 'PENDING', b'0', 10002, 10002, '2026-04-16 09:30:00', '2026-04-16 09:30:00'),
    (43001, 41001, 42001, 1, 'recommended', 82, 34, 18, 13, 12, 5, '1688',
     'https://detail.1688.com/offer/801000000101.html',
     '可充电古兰经音箱焚香炉礼盒便携款', '义乌香器工厂店',
     '16.8-22.5 元', '200 件起', '浙江金华', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；古兰经音箱焚香炉礼盒便携款；价格16.8-22.5元；200件起；浙江金华；48小时发货',
     '详情卖点；ABS电镀外壳；USB充电；手持便携；礼盒装',
     '属性快照；材质 ABS电镀外壳；供电方式 USB充电；尺寸 手持便携；包装 礼盒装',
     '物流说明；48小时发货；支持常规打样',
     '包装说明；礼盒装；礼盒配件待确认',
     NULL, NULL, NULL,
     '主体方向正确，但音箱细节还需要继续比对。', '暂未询价，建议先确认扬声器结构和礼盒清单。', 'PREPARE_INQUIRY', '实力商家|超级工厂|48小时发货',
     '标题命中 4 个核心词|价格落在目标区间|ABS电镀外壳', '扬声器细节需进详情页确认|手持便携|礼盒配件待确认',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:34:00', '2026-04-17 10:34:00'),
    (43002, 41001, 42001, 2, 'review', 61, 28, 12, 9, 9, 3, '1688',
     'https://detail.1688.com/offer/801000000102.html',
     '家用电子焚香炉 遥控款', '深圳礼品香薰商行',
     '14.5-19.8 元', '300 件起', '广东深圳', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；家用电子焚香炉遥控款；价格14.5-19.8元；300件起；广东深圳；48小时发货',
     '详情卖点；ABS外壳；充电款；桌面款；普通彩盒',
     '属性快照；材质 ABS外壳；供电方式 USB充电；尺寸 桌面款；包装 普通彩盒',
     '物流说明；48小时发货；常规补货可排单',
     '包装说明；普通彩盒；配件清单待确认',
     NULL, NULL, NULL,
     NULL, NULL, NULL, '诚信通|48小时发货',
     '图搜结果位次靠前|价格接近目标区间|ABS外壳', '音箱结构不明确|桌面款|普通彩盒',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:34:00', '2026-04-17 10:34:00'),
    (43003, 41001, 42001, 3, 'reject', 34, 16, 6, 5, 5, 2, '1688',
     'https://detail.1688.com/offer/801000000103.html',
     '普通电子香薰炉 无音箱版', '广州家居器具档口',
     '9.9-12.5 元', '500 件起', '广东广州', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；普通电子香薰炉无音箱版；价格9.9-12.5元；500件起；广东广州；3-5天发货',
     '详情卖点；塑料机身；插电款；桌面款；普通袋装',
     '属性快照；材质 塑料；供电方式 插电款；尺寸 桌面款；包装 普通袋装',
     '物流说明；3-5天发货；大货排产为主',
     '包装说明；普通袋装；简配出货',
     NULL, NULL, NULL,
     '核心功能偏差太大，不建议继续投入时间。', NULL, 'HOLD', '诚信通',
     '价格低|塑料机身|3-5天发货', '核心功能不一致|插电款|桌面款|普通袋装|MOQ 偏高',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:34:00', '2026-04-17 10:34:00'),
    (43004, 41002, 42002, 1, 'recommended', 79, 33, 17, 12, 12, 5, '1688',
     'https://detail.1688.com/offer/801000000201.html',
     'USB 充电发香器 头发衣物熏香款', '东莞香氛电器源头厂',
     '11.6-16.8 元', '240 件起', '广东东莞', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；USB充电发香器头发衣物熏香款；价格11.6-16.8元；240件起；广东东莞；48小时发货',
     '详情卖点；ABS外壳；USB充电；便携小型；简装彩盒',
     '属性快照；材质 ABS外壳；供电方式 USB充电；尺寸 便携小型；包装 简装彩盒',
     '物流说明；48小时发货；支持小批量补货；包装后重量 280g',
     '包装说明；简装彩盒；发热仓结构待确认',
     NULL, NULL, NULL,
     NULL, NULL, NULL, '实力商家|源头工厂|48小时发货',
     '标题命中 5 个核心词|价格合理|ABS外壳', '发热仓结构需进详情页确认|便携小型|简装彩盒',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:37:00', '2026-04-17 10:37:00'),
    (43005, 41002, 42002, 2, 'recommended', 86, 35, 18, 14, 14, 5, '1688',
     'https://detail.1688.com/offer/801000000202.html',
     '便携式 USB 充电电熏香炉 轻奢礼品款', '义乌轻奢香器供应链',
     '12.2-17.5 元', '180 件起', '浙江义乌', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；便携式USB充电电熏香炉轻奢礼品款；价格12.2-17.5元；180件起；浙江义乌；7-10天交期',
     '详情卖点；ABS+陶瓷内胆；USB充电；便携小型；轻奢礼盒',
     '属性快照；材质 ABS+陶瓷内胆；供电方式 USB充电；尺寸 便携小型；包装 轻奢礼盒',
     '物流说明；7-10天交期；支持定制包装排产',
     '包装说明；轻奢礼盒；包装清单需二次确认；包装尺寸 18x8x8cm',
     NULL, NULL, NULL,
     '外观一致性最佳，当前先作为优先意向采购。', '可先围绕电池容量、包装清单和交期做首轮询价。', 'INTENT', '实力商家|超级工厂|深度验厂',
     '外观一致性最好|价格在目标区间|ABS+陶瓷内胆', '需复核电池容量|轻奢礼盒|7-10天交期',
    b'1', 'SELECTED', b'0', 10003, 10003, '2026-04-17 10:37:00', '2026-04-17 15:35:00'),
    (43006, 41002, 42002, 3, 'review', 55, 24, 11, 10, 7, 3, '1688',
     'https://detail.1688.com/offer/798448779771.html?offerId=798448779771&hotSaleSkuId=5613239587877&spm=a260k.home2025.recommendpart.2',
     'USB 充电轻奢焚香炉现货款', '苍南县永诚复合材料有限公司',
     '10.8-13.5 元', '300 件起', '浙江温州', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；USB充电轻奢焚香炉现货款；价格10.8-13.5元；300件起；浙江温州；48小时响应',
     '详情卖点；现货款；支持打样；可直接进入自动询价',
     '属性快照；材质 复合材料；供电方式 无电；尺寸 标准卷装；包装 普通包装',
     '物流说明；48小时响应；支持首轮打样沟通',
     '包装说明；普通包装；规格细节待二次确认',
     NULL, NULL, NULL,
     NULL, '可先发起自动询价，确认首轮报价、打样和交期。', 'PREPARE_INQUIRY', '现货供应|支持打样|48小时响应',
     '可直接进入询价|供应商响应快|支持首轮打样', '规格细节待确认|包装仍需人工复核',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:37:00', '2026-04-17 10:37:00'),
    (43007, 41003, 42003, 1, 'recommended', 75, 31, 16, 12, 11, 5, '1688',
     'https://detail.1688.com/offer/801000000301.html',
     '12cm 阿拉伯迷你香炉 小摆件款', '泉州陶艺香炉厂',
     '4.6-6.8 元', '300 件起', '福建泉州', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；12cm阿拉伯迷你香炉小摆件款；价格4.6-6.8元；300件起；福建泉州；48小时发货',
     '详情卖点；釉面陶瓷；无电小摆件；12cm；礼盒装',
     '属性快照；材质 釉面陶瓷；供电方式 无电；尺寸 12cm；包装 礼盒装',
     '物流说明；48小时发货；常规家居摆件补货',
     '包装说明；礼盒装；适合家居礼赠',
     NULL, NULL, NULL,
     '尺寸方向基本合适，但材质还要再确认。', '建议询问釉面材质、尺寸公差和最小打样数量。', 'PREPARE_INQUIRY', '实力商家|48小时发货',
     '尺寸线索命中|价格适合入门款|釉面陶瓷', '礼盒装|无电小摆件|材质需确认是否为陶瓷上釉',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:40:00', '2026-04-17 10:40:00'),
    (43008, 41003, 42003, 2, 'review', 58, 26, 10, 11, 8, 3, '1688',
     'https://detail.1688.com/offer/801000000302.html',
     '阿拉伯风小香座 家居桌面摆件', '潮州香器工作室',
     '3.8-5.5 元', '500 件起', '广东潮州', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；阿拉伯风小香座家居桌面摆件；价格3.8-5.5元；500件起；广东潮州；10-12天交付',
     '详情卖点；陶瓷树脂材质待确认；无电摆件；10-11cm；普通盒',
     '属性快照；材质 陶瓷树脂待确认；供电方式 无电；尺寸 10-11cm；包装 普通盒',
     '物流说明；10-12天交付；装饰细节需复核',
     '包装说明；普通盒；桌面摆件常规包装',
     NULL, NULL, NULL,
     NULL, NULL, NULL, '诚信通',
     '价格可接受|陶瓷树脂材质待确认|10-11cm', '无电摆件|普通盒|10-12天交付|装饰细节需人工复核',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:40:00', '2026-04-17 10:40:00'),
    (43009, 41003, 42003, 3, 'reject', 28, 14, 5, 5, 2, 2, '1688',
     'https://detail.1688.com/offer/801000000303.html',
     '大香炉家居落地摆件', '河北摆件档口',
     '11.0-18.0 元', '50 件起', '河北石家庄', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；大香炉家居落地摆件；价格11.0-18.0元；50件起；河北石家庄；5-7天发货',
     '详情卖点；金属大摆件；无电；25cm；普通箱装',
     '属性快照；材质 金属；供电方式 无电；尺寸 25cm；包装 普通箱装',
     '物流说明；5-7天发货；大件商品运输',
     '包装说明；普通箱装；落地摆件防护包装',
     NULL, NULL, NULL,
     '尺寸和客单都偏离，不再继续。', NULL, 'HOLD', '',
     '金属大摆件|5-7天发货', '25cm|落地款|普通箱装|尺寸明显不符|客单偏离目标',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:40:00', '2026-04-17 10:40:00'),
    (43010, 41004, 42004, 1, 'recommended', 73, 30, 15, 11, 12, 5, '1688',
     'https://detail.1688.com/offer/801000000401.html',
     '陶瓷电熏香炉 家居插电款', '德化陶瓷香器厂',
     '13.5-18.8 元', '160 件起', '福建德化', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；陶瓷电熏香炉家居插电款；价格13.5-18.8元；160件起；福建德化；7-10天交付',
     '详情卖点；陶瓷；插电款；桌面款；彩盒装',
     '属性快照；材质 陶瓷；供电方式 插电款；尺寸 桌面款；包装 彩盒装',
     '物流说明；7-10天交付；插电款需确认插头规格',
     '包装说明；彩盒装；包装体积待确认',
     NULL, NULL, NULL,
     '候选方向正确，但插头和胆仓材质必须确认。', '优先询问插头规格、材质证明和包装体积。', 'PREPARE_INQUIRY', '超级工厂|深度验厂',
     '陶瓷和家居场景匹配|价格在目标区间|工厂标签强', '插电款|彩盒装|7-10天交付|需要确认插头规格和胆仓材质',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:44:00', '2026-04-17 10:44:00'),
    (43011, 41004, 42004, 2, 'review', 56, 23, 12, 10, 8, 3, '1688',
     'https://detail.1688.com/offer/801000000402.html',
     '欧式电熏香炉 陶瓷釉面款', '佛山家居小家电',
     '12.8-17.0 元', '240 件起', '广东佛山', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；欧式电熏香炉陶瓷釉面款；价格12.8-17.0元；240件起；广东佛山；10-12天交付',
     '详情卖点；釉面陶瓷；插电款；桌面家居款；彩盒装',
     '属性快照；材质 釉面陶瓷；供电方式 插电款；尺寸 桌面款；包装 彩盒装',
     '物流说明；10-12天交付；安全认证待确认',
     '包装说明；彩盒装；包装信息偏弱',
     NULL, NULL, NULL,
     NULL, NULL, NULL, '实力商家',
     '外观接近|釉面陶瓷|彩盒装', '桌面家居款|10-12天交付|插电结构和安全认证需确认',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:44:00', '2026-04-17 10:44:00'),
    (43012, 41004, 42004, 3, 'reject', 30, 15, 6, 4, 3, 2, '1688',
     'https://detail.1688.com/offer/801000000403.html',
     '蜡烛加热陶瓷香薰炉 牛皮盒款', '广州家居摆件档口',
     '7.5-9.9 元', '300 件起', '广东广州', NULL, NULL, NULL, NULL, NULL,
     '结果卡片；蜡烛加热陶瓷香薰炉牛皮盒款；价格7.5-9.9元；300件起；广东广州；5-7天发货',
     '详情卖点；陶瓷外壳；蜡烛加热；桌面款；牛皮盒',
     '属性快照；材质 陶瓷外壳；供电方式 蜡烛加热；尺寸 桌面款；包装 牛皮盒',
     '物流说明；5-7天发货；非电款常规排单',
     '包装说明；牛皮盒；桌面香薰炉简装',
     NULL, NULL, NULL,
     '产品工作原理不一致，直接淘汰。', NULL, 'HOLD', '',
     '价格低|陶瓷外壳|牛皮盒|5-7天发货', '蜡烛加热|桌面款|工作原理不一致|直接淘汰',
     b'0', 'PENDING', b'0', 10003, 10003, '2026-04-17 10:44:00', '2026-04-17 10:44:00');
