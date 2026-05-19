-- File management parse iteration alignment.
-- One logical document can have multiple parse tasks over time.

SET NAMES utf8mb4;

SET @add_file_parse_task_document_group := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_task'
              AND COLUMN_NAME = 'document_group_id'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_task` ADD COLUMN `document_group_id` BIGINT DEFAULT NULL AFTER `base_version_id`'
    )
);
PREPARE stmt FROM @add_file_parse_task_document_group;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_task_parent_task := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_task'
              AND COLUMN_NAME = 'parent_task_id'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_task` ADD COLUMN `parent_task_id` BIGINT DEFAULT NULL AFTER `document_group_id`'
    )
);
PREPARE stmt FROM @add_file_parse_task_parent_task;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_file_parse_task_iteration_no := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_task'
              AND COLUMN_NAME = 'iteration_no'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_task` ADD COLUMN `iteration_no` INT NOT NULL DEFAULT 1 AFTER `parent_task_id`'
    )
);
PREPARE stmt FROM @add_file_parse_task_iteration_no;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `file_mgmt_parse_task`
SET `document_group_id` = `id`,
    `iteration_no` = COALESCE(NULLIF(`iteration_no`, 0), 1),
    `gmt_updated` = NOW()
WHERE `is_deleted` = b'0'
  AND `document_group_id` IS NULL;

SET @add_file_parse_task_group_index := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'file_mgmt_parse_task'
              AND INDEX_NAME = 'idx_file_mgmt_parse_task_group'
        ),
        'SELECT 1',
        'ALTER TABLE `file_mgmt_parse_task` ADD KEY `idx_file_mgmt_parse_task_group` (`document_group_id`, `iteration_no`, `id`)'
    )
);
PREPARE stmt FROM @add_file_parse_task_group_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
