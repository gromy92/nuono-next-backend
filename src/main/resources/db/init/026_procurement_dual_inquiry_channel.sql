-- Nuono Next procurement dual inquiry channel
-- Purpose:
-- 1. Track the planned 1688 inquiry channel and the actual active channel.
-- 2. Preserve fallback reason when 1688 AI bulk inquiry is unavailable.
-- 3. Store external inquiry result and reply parsing evidence for later AI summary.

SET @procurement_auto_inquiry_task_add_planned_channel := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'planned_channel'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `planned_channel` VARCHAR(40) DEFAULT NULL AFTER `pool_item_id`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_planned_channel;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_active_channel := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'active_channel'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `active_channel` VARCHAR(40) DEFAULT NULL AFTER `planned_channel`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_active_channel;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_channel_fallback_reason := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'channel_fallback_reason'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `channel_fallback_reason` VARCHAR(500) DEFAULT NULL AFTER `active_channel`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_channel_fallback_reason;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_external_inquiry_id := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'external_inquiry_id'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `external_inquiry_id` VARCHAR(100) DEFAULT NULL AFTER `channel_fallback_reason`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_external_inquiry_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_external_inquiry_url := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'external_inquiry_url'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `external_inquiry_url` VARCHAR(500) DEFAULT NULL AFTER `external_inquiry_id`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_external_inquiry_url;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_external_result_status := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'external_result_status'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `external_result_status` VARCHAR(40) DEFAULT NULL AFTER `external_inquiry_url`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_external_result_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_external_result_payload := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'external_result_payload'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `external_result_payload` LONGTEXT DEFAULT NULL AFTER `external_result_status`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_external_result_payload;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_reply_source := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'reply_source'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `reply_source` VARCHAR(40) DEFAULT NULL AFTER `external_result_payload`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_reply_source;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_reply_parse_status := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'reply_parse_status'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `reply_parse_status` VARCHAR(40) DEFAULT NULL AFTER `reply_source`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_reply_parse_status;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_reply_parse_error := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND COLUMN_NAME = 'reply_parse_error'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD COLUMN `reply_parse_error` VARCHAR(500) DEFAULT NULL AFTER `reply_parse_status`'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_reply_parse_error;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_active_channel_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND INDEX_NAME = 'idx_procurement_auto_inquiry_task_channel'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD KEY `idx_procurement_auto_inquiry_task_channel` (`active_channel`)'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_active_channel_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @procurement_auto_inquiry_task_add_external_inquiry_index := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'procurement_auto_inquiry_task'
              AND INDEX_NAME = 'idx_procurement_auto_inquiry_task_external_inquiry'
        ),
        'SELECT 1',
        'ALTER TABLE `procurement_auto_inquiry_task` ADD KEY `idx_procurement_auto_inquiry_task_external_inquiry` (`external_inquiry_id`)'
    )
);
PREPARE stmt FROM @procurement_auto_inquiry_task_add_external_inquiry_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE procurement_auto_inquiry_task
SET planned_channel = COALESCE(planned_channel, 'ALI_AI_BULK_INQUIRY'),
    active_channel = CASE
        WHEN active_channel IN ('ALI_AI_BULK_INQUIRY', 'NUONO_CHAT_INQUIRY') THEN active_channel
        ELSE 'NUONO_CHAT_INQUIRY'
    END,
    channel_fallback_reason = CASE
        WHEN send_channel IS NOT NULL THEN channel_fallback_reason
        ELSE COALESCE(channel_fallback_reason, '1688 AI bulk inquiry adapter is not enabled; fallback to Nuono chat inquiry.')
    END,
    reply_source = COALESCE(reply_source, 'CHAT_THREAD'),
    reply_parse_status = COALESCE(reply_parse_status, CASE
        WHEN status IN ('PENDING', 'RUNNING', 'RETRYING', 'SENT', 'CHATTING') THEN 'PENDING'
        WHEN status = 'HANDOFF' THEN 'NOT_AVAILABLE'
        ELSE NULL
    END)
WHERE is_deleted = b'0';
