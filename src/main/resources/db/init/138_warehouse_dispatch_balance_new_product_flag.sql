-- Add the new-product flag required by warehouse dispatch read models.
-- Existing rows default to non-new-product so only explicitly classified new items require manual review.

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'procurement_fulfillment_balance'
      AND COLUMN_NAME = 'is_new_product'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE procurement_fulfillment_balance ADD COLUMN is_new_product BIT(1) NOT NULL DEFAULT b''0'' AFTER available_quantity',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
