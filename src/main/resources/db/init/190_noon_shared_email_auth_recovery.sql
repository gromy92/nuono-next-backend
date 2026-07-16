-- Durable shared-email Noon authentication recovery queue.
--
-- The recovery tables deliberately persist only hashes and safe operational metadata.
-- Sensitive login material and raw provider responses stay outside this boundary.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `noon_auth_identity_recovery` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `predecessor_recovery_id` BIGINT DEFAULT NULL,
    `identity_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'COALESCING',
    `active_identity_slot` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE
                WHEN `status` IN (
                    'COALESCING',
                    'AUTHENTICATING',
                    'WAITING_EMAIL',
                    'VALIDATING',
                    'APPLYING_PROJECTS',
                    'RECOVERING_PULLS',
                    'WAITING_COOLDOWN',
                    'MANUAL_HOLD'
                ) THEN `identity_key`
                ELSE NULL
            END
        ) STORED,
    `successor_identity_slot` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'WAITING_PREDECESSOR' THEN `identity_key` ELSE NULL END
        ) STORED,
    `generation_no` INT NOT NULL DEFAULT 0,
    `send_budget_epoch` INT NOT NULL DEFAULT 0,
    `send_attempt_count` INT NOT NULL DEFAULT 0,
    `first_send_at` DATETIME DEFAULT NULL,
    `second_send_at` DATETIME DEFAULT NULL,
    `coalesce_until` DATETIME NOT NULL,
    `next_attempt_at` DATETIME NOT NULL,
    `lease_owner` VARCHAR(100) DEFAULT NULL,
    `lease_token` VARCHAR(64) DEFAULT NULL,
    `lease_until` DATETIME DEFAULT NULL,
    `version_no` BIGINT NOT NULL DEFAULT 0,
    `config_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    `last_mail_uid_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    `last_message_id_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    `failure_code` VARCHAR(80) DEFAULT NULL,
    `diagnostic_summary` VARCHAR(1000) DEFAULT NULL,
    `requested_at` DATETIME NOT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `completed_at` DATETIME DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_auth_identity_recovery_active` (`active_identity_slot`),
    UNIQUE KEY `uk_noon_auth_identity_recovery_successor` (`successor_identity_slot`),
    KEY `idx_noon_auth_identity_recovery_predecessor` (`predecessor_recovery_id`, `status`),
    KEY `idx_noon_auth_identity_recovery_due` (`status`, `next_attempt_at`, `lease_until`, `id`),
    KEY `idx_noon_auth_identity_recovery_history` (`identity_key`, `gmt_create`, `id`),
    KEY `idx_noon_auth_identity_recovery_send_quota` (`identity_key`, `first_send_at`, `second_send_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @noon_auth_recovery_add_send_budget_epoch := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_auth_identity_recovery'
              AND COLUMN_NAME = 'send_budget_epoch'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_auth_identity_recovery` ADD COLUMN `send_budget_epoch` INT NOT NULL DEFAULT 0 AFTER `generation_no`'
    )
);
PREPARE stmt FROM @noon_auth_recovery_add_send_budget_epoch;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `noon_auth_identity_send_ledger` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `identity_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `recovery_id` BIGINT NOT NULL,
    `config_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `send_budget_epoch` INT NOT NULL,
    `generation_no` INT NOT NULL,
    `send_intent_at` DATETIME NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_auth_send_ledger_generation` (`recovery_id`, `send_budget_epoch`, `generation_no`),
    KEY `idx_noon_auth_send_ledger_quota` (`identity_key`, `send_intent_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `noon_auth_identity_send_ledger` (
    `identity_key`, `recovery_id`, `config_fingerprint`, `send_budget_epoch`, `generation_no`,
    `send_intent_at`, `gmt_create`
)
SELECT `identity_key`, `id`, `config_fingerprint`, `send_budget_epoch`, 1, `first_send_at`, `first_send_at`
FROM `noon_auth_identity_recovery`
WHERE `config_fingerprint` IS NOT NULL
  AND `first_send_at` IS NOT NULL
UNION ALL
SELECT `identity_key`, `id`, `config_fingerprint`, `send_budget_epoch`, 2, `second_send_at`, `second_send_at`
FROM `noon_auth_identity_recovery`
WHERE `config_fingerprint` IS NOT NULL
  AND `second_send_at` IS NOT NULL;

CREATE TABLE IF NOT EXISTS `noon_project_auth_state` (
    `owner_user_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `identity_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'HEALTHY',
    `active_recovery_id` BIGINT DEFAULT NULL,
    `auth_version` BIGINT NOT NULL DEFAULT 0,
    `binding_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    `config_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    `last_failure_code` VARCHAR(80) DEFAULT NULL,
    `last_failure_task_id` BIGINT DEFAULT NULL,
    `last_failure_at` DATETIME DEFAULT NULL,
    `last_success_at` DATETIME DEFAULT NULL,
    `manual_hold_reason` VARCHAR(500) DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`owner_user_id`, `project_code`),
    KEY `idx_noon_project_auth_identity` (`identity_key`, `status`, `active_recovery_id`),
    KEY `idx_noon_project_auth_recovery` (`active_recovery_id`, `status`),
    KEY `idx_noon_project_auth_failure` (`status`, `last_failure_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @noon_project_auth_add_binding_fingerprint := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_project_auth_state'
              AND COLUMN_NAME = 'binding_fingerprint'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_project_auth_state` ADD COLUMN `binding_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `auth_version`'
    )
);
PREPARE stmt FROM @noon_project_auth_add_binding_fingerprint;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_project_auth_add_config_fingerprint := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_project_auth_state'
              AND COLUMN_NAME = 'config_fingerprint'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_project_auth_state` ADD COLUMN `config_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER `binding_fingerprint`'
    )
);
PREPARE stmt FROM @noon_project_auth_add_config_fingerprint;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `noon_auth_identity_recovery_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `recovery_id` BIGINT NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `project_code` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    `store_code` VARCHAR(100) DEFAULT NULL,
    `site_code` VARCHAR(32) DEFAULT NULL,
    `source_task_id` BIGINT DEFAULT NULL,
    `source_task_slot` BIGINT
        GENERATED ALWAYS AS (COALESCE(`source_task_id`, 0)) STORED,
    `source_domain` VARCHAR(64) DEFAULT NULL,
    `expected_auth_version` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `failure_code` VARCHAR(80) DEFAULT NULL,
    `diagnostic_summary` VARCHAR(1000) DEFAULT NULL,
    `recovered_at` DATETIME DEFAULT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_auth_recovery_item_source` (
        `recovery_id`,
        `owner_user_id`,
        `project_code`,
        `source_task_slot`
    ),
    KEY `idx_noon_auth_recovery_item_pending` (`recovery_id`, `status`, `id`),
    KEY `idx_noon_auth_recovery_item_project` (`owner_user_id`, `project_code`, `status`),
    KEY `idx_noon_auth_recovery_item_task` (`source_task_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @noon_pull_add_auth_recovery_id := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'auth_recovery_id'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD COLUMN `auth_recovery_id` BIGINT DEFAULT NULL AFTER `active_lock_key`'
    )
);
PREPARE stmt FROM @noon_pull_add_auth_recovery_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_add_auth_recovery_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND INDEX_NAME = 'idx_noon_pull_task_auth_recovery'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` ADD KEY `idx_noon_pull_task_auth_recovery` (`auth_recovery_id`, `status`)'
    )
);
PREPARE stmt FROM @noon_pull_add_auth_recovery_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @noon_pull_expand_auth_active_slot := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'noon_pull_task'
              AND COLUMN_NAME = 'active_lock_slot'
              AND UPPER(COALESCE(GENERATION_EXPRESSION, '')) LIKE '%BLOCKED_AUTH%'
        ),
        'SELECT 1',
        'ALTER TABLE `noon_pull_task` MODIFY COLUMN `active_lock_slot` VARCHAR(512) GENERATED ALWAYS AS (CASE WHEN `status` IN (''QUEUED'', ''RUNNING'', ''BLOCKED_AUTH'') THEN `active_lock_key` ELSE NULL END) STORED'
    )
);
PREPARE stmt FROM @noon_pull_expand_auth_active_slot;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
