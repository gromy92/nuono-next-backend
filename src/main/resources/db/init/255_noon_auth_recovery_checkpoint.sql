-- Restart-safe, short-lived encrypted checkpoint for one shared-email recovery.
-- Plain OTP, PKCE material, access token and cookies never appear in this table.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `noon_auth_recovery_checkpoint` (
    `recovery_id` BIGINT NOT NULL,
    `generation_no` INT NOT NULL,
    `checkpoint_kind` VARCHAR(32) NOT NULL,
    `key_version` VARCHAR(32) NOT NULL,
    `initialization_vector` VARBINARY(12) NOT NULL,
    `ciphertext` MEDIUMBLOB NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `version_no` BIGINT NOT NULL DEFAULT 0,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`recovery_id`),
    KEY `idx_noon_auth_checkpoint_expiry` (`expires_at`, `recovery_id`),
    CONSTRAINT `chk_noon_auth_checkpoint_generation` CHECK (`generation_no` > 0),
    CONSTRAINT `chk_noon_auth_checkpoint_kind`
        CHECK (`checkpoint_kind` IN ('OTP_CHALLENGE', 'IDENTITY_GRANT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
