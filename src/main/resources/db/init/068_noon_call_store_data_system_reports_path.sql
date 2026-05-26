-- Keep the Noon store-data menu URL aligned with the system-reports first-level link.
-- This is idempotent because earlier test environments may already have 064 applied
-- with the old /noon-call path.

SET NAMES utf8mb4;

UPDATE `menu`
SET `url_path` = '/system-reports',
    `is_deleted` = b'0',
    `gmt_updated` = NOW()
WHERE `id` = 9700
  AND `name` = 'Noon调用';

UPDATE `menu`
SET `url_path` = '/system-reports/store-data',
    `is_deleted` = b'0',
    `gmt_updated` = NOW()
WHERE `id` = 9701
  AND `name` = '店铺数据';
