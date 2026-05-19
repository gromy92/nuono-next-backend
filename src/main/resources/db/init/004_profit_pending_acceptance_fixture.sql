-- Profit pending-data acceptance baseline
-- Purpose:
-- 1. Normalize the existing local-db procurement sample so profit acceptance no longer
--    depends on browser-session interception.
-- 2. Keep one candidate as "missing specs only" and one candidate as "missing weight only".
--
-- Resulting baseline:
-- - candidate 43004 -> only weight is parseable, so quick signals should show `待补：长度 / 宽度 / 高度`
-- - candidate 43005 -> only 3D dimensions are parseable, so quick signals should show `待补：重量`

UPDATE `procurement_candidate`
SET `shipping_snapshot_text` = '物流说明；48小时发货；支持小批量补货；包装后重量 280g'
WHERE `id` = 43004;

UPDATE `procurement_candidate`
SET `package_snapshot_text` = '包装说明；轻奢礼盒；包装清单需二次确认；包装尺寸 18x8x8cm'
WHERE `id` = 43005;
