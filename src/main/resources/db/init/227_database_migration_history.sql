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
