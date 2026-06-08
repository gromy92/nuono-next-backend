-- Treat ignored competitor candidates as unseen by removing active keyword relations.

SET NAMES utf8mb4;

UPDATE `operations_competitor_keyword_product`
SET `is_deleted` = b'1',
    `gmt_updated` = NOW()
WHERE `relation_status` = 'IGNORED'
  AND `is_deleted` = b'0';
