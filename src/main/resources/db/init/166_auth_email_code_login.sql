SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `auth_email_code_challenge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(190) NOT NULL,
    `purpose` VARCHAR(32) NOT NULL,
    `code_hash` VARCHAR(128) NOT NULL,
    `code_salt` VARCHAR(64) NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `consumed_at` DATETIME DEFAULT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `request_ip` VARCHAR(64) DEFAULT NULL,
    `user_agent` VARCHAR(255) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_auth_email_code_active` (`email`, `purpose`, `consumed_at`, `expires_at`),
    KEY `idx_auth_email_code_created` (`email`, `purpose`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
