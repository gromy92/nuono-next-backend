-- One-time baseline gate for the published 223/224 migrations. This script is
-- executed only while the governed history is absent (or empty during an
-- explicit bootstrap), so later forward migrations may legitimately evolve
-- these product indexes without being coupled to the 227 postcheck.
SET @pre_catalog_table_count := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('product_site_offer', 'product_master')
      AND table_type = 'BASE TABLE'
      AND UPPER(engine) = 'INNODB'
);
SET @pre_catalog_223_columns_exact := (
    SELECT COUNT(*) = 2
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'product_site_offer'
      AND (
          (
              column_name = 'active_state_source'
              AND data_type = 'varchar'
              AND column_type = 'varchar(80)'
              AND is_nullable = 'YES'
              AND column_default IS NULL
              AND extra = ''
          )
          OR (
              column_name = 'active_state_synced_at'
              AND data_type = 'datetime'
              AND column_type = 'datetime'
              AND is_nullable = 'YES'
              AND column_default IS NULL
              AND extra = ''
          )
      )
);
SET @pre_catalog_223_index_exact := (
    SELECT IF(
        COUNT(*) = 4
            AND MIN(non_unique) = 1
            AND MAX(non_unique) = 1
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MAX(UPPER(index_type)) = 'BTREE'
            AND SUM(sub_part IS NULL) = 4
            AND SUM(collation = 'A') = 4
            AND SUM(is_visible = 'YES') = 4
            AND SUM(expression IS NULL) = 4
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:logical_store_id,2:site_id,3:maintenance_enabled,4:is_active',
        1,
        0
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product_site_offer'
      AND index_name = 'idx_product_site_offer_replenishment_coverage'
);
SET @pre_catalog_224_partner_index_exact := (
    SELECT IF(
        COUNT(*) = 2
            AND MIN(non_unique) = 0
            AND MAX(non_unique) = 0
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MAX(UPPER(index_type)) = 'BTREE'
            AND SUM(sub_part IS NULL) = 2
            AND SUM(collation = 'A') = 2
            AND SUM(is_visible = 'YES') = 2
            AND SUM(expression IS NULL) = 2
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:logical_store_id,2:partner_sku',
        1,
        0
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product_master'
      AND index_name = 'uk_product_master_store_partner_sku'
);
SET @pre_catalog_224_lookup_index_exact := (
    SELECT IF(
        COUNT(*) = 3
            AND MIN(non_unique) = 1
            AND MAX(non_unique) = 1
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MAX(UPPER(index_type)) = 'BTREE'
            AND SUM(sub_part IS NULL) = 3
            AND SUM(collation = 'A') = 3
            AND SUM(is_visible = 'YES') = 3
            AND SUM(expression IS NULL) = 3
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index
                SEPARATOR ','
            ) = '1:logical_store_id,2:sku_parent,3:is_deleted',
        1,
        0
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product_master'
      AND index_name = 'idx_product_master_store_sku_parent_lookup'
);
SET @pre_catalog_224_legacy_index_count := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'product_master'
      AND index_name = 'uk_product_master_store_sku_parent'
);

DROP TEMPORARY TABLE IF EXISTS `nuono_227_pre_catalog_baseline_guard`;
CREATE TEMPORARY TABLE `nuono_227_pre_catalog_baseline_guard` (
    `invalid_schema_count` BIGINT NOT NULL,
    CONSTRAINT `chk_227_pre_catalog_baseline`
        CHECK (`invalid_schema_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_227_pre_catalog_baseline_guard`
VALUES (IF(
    @pre_catalog_table_count = 2
        AND @pre_catalog_223_columns_exact = 1
        AND @pre_catalog_223_index_exact = 1
        AND @pre_catalog_224_partner_index_exact = 1
        AND @pre_catalog_224_lookup_index_exact = 1
        AND @pre_catalog_224_legacy_index_count = 0,
    0,
    1
));
DROP TEMPORARY TABLE `nuono_227_pre_catalog_baseline_guard`;

-- Release-side migration history. The Web application must never write these tables.
CREATE TABLE IF NOT EXISTS `nuono_schema_migration` (
    `migration_key` VARCHAR(191) NOT NULL,
    `script_path` VARCHAR(255) NOT NULL,
    `checksum_sha256` CHAR(64) NOT NULL,
    `postcheck_sha256` CHAR(64) NOT NULL,
    `state` VARCHAR(16) NOT NULL,
    `release_commit` CHAR(40) NOT NULL,
    `attempt_no` INT UNSIGNED NOT NULL,
    `started_at` DATETIME(6) NOT NULL,
    `finished_at` DATETIME(6) DEFAULT NULL,
    `installed_by` VARCHAR(128) NOT NULL,
    `error_code` VARCHAR(64) DEFAULT NULL,
    `error_digest` CHAR(64) DEFAULT NULL,
    `error_summary` VARCHAR(1000) DEFAULT NULL,
    `gmt_create` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `gmt_updated` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`migration_key`),
    KEY `idx_nuono_schema_migration_state` (`state`, `started_at`),
    KEY `idx_nuono_schema_migration_commit` (`release_commit`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `nuono_schema_migration_attempt` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `migration_key` VARCHAR(191) NOT NULL,
    `attempt_no` INT UNSIGNED NOT NULL,
    `checksum_sha256` CHAR(64) NOT NULL,
    `postcheck_sha256` CHAR(64) NOT NULL,
    `state` VARCHAR(16) NOT NULL,
    `operation` VARCHAR(16) NOT NULL,
    `reconciles_attempt_no` INT UNSIGNED DEFAULT NULL,
    `release_commit` CHAR(40) NOT NULL,
    `started_at` DATETIME(6) NOT NULL,
    `finished_at` DATETIME(6) DEFAULT NULL,
    `installed_by` VARCHAR(128) NOT NULL,
    `error_code` VARCHAR(64) DEFAULT NULL,
    `error_digest` CHAR(64) DEFAULT NULL,
    `error_summary` VARCHAR(1000) DEFAULT NULL,
    `gmt_create` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_nuono_schema_migration_attempt` (`migration_key`, `attempt_no`),
    KEY `idx_nuono_schema_migration_attempt_state` (`state`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
