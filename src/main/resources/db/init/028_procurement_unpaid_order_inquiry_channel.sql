-- Switch procurement module 2-4 default inquiry strategy to "unpaid order first".
-- This migration does not create external 1688 orders. It only updates local
-- planning metadata so the real adapter can be added behind safety gates later.

UPDATE procurement_auto_inquiry_task
SET planned_channel = 'ALI_UNPAID_ORDER_INQUIRY',
    channel_fallback_reason = CASE
        WHEN active_channel = 'NUONO_CHAT_INQUIRY'
            THEN '1688 unpaid order adapter is not enabled; fallback to Nuono chat inquiry.'
        ELSE channel_fallback_reason
    END,
    gmt_updated = NOW()
WHERE is_deleted = b'0'
  AND planned_channel = 'ALI_AI_BULK_INQUIRY';
