UPDATE `product_master`
SET `sync_status` = 'draft',
    `gmt_updated` = NOW()
WHERE `sync_status` = 'conflict';

UPDATE `product_group`
SET `sync_status` = 'draft',
    `gmt_updated` = NOW()
WHERE `sync_status` = 'conflict';

UPDATE `product_action_log`
SET `result_status` = 'draft',
    `gmt_updated` = NOW()
WHERE `result_status` = 'conflict';
