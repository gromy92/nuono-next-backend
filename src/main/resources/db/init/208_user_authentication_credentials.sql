-- Expand adaptive password credentials without narrowing a wider future schema.
-- Existing credentials remain unchanged. Existing sessions start at version 0 and
-- explicit password changes atomically advance that version in the application SQL.

SELECT `password` FROM `user` LIMIT 0;

SET @expand_user_password_credential := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user'
              AND COLUMN_NAME = 'password'
              AND CHARACTER_MAXIMUM_LENGTH < 200
        ),
        'ALTER TABLE `user` MODIFY COLUMN `password` VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''登录密码凭据''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @expand_user_password_credential;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_user_credential_version := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user'
        )
        AND NOT EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user'
              AND COLUMN_NAME = 'credential_version'
        ),
        'ALTER TABLE `user` ADD COLUMN `credential_version` BIGINT NOT NULL DEFAULT 0 COMMENT ''登录凭据版本'' AFTER `password`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @add_user_credential_version;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT `password`, `credential_version` FROM `user` LIMIT 0;
