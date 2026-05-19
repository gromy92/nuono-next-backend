CREATE TABLE IF NOT EXISTS `merchant_payment` (
    `id` BIGINT NOT NULL,
    `merchant_user_id` BIGINT NOT NULL,
    `amount` DECIMAL(12,2) NOT NULL,
    `payment_date` DATE NOT NULL,
    `remark` VARCHAR(255) DEFAULT NULL,
    `is_deleted` BIT(1) DEFAULT b'0',
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_merchant_payment_user_id` (`merchant_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `merchant_payment` (
    `id`, `merchant_user_id`, `amount`, `payment_date`, `remark`,
    `is_deleted`, `created_by`, `updated_by`
)
SELECT 50001, 10002, 3999.00, '2026-03-01', '年费续费', b'0', 10001, 10001
WHERE NOT EXISTS (SELECT 1 FROM `merchant_payment` WHERE id = 50001);

INSERT INTO `merchant_payment` (
    `id`, `merchant_user_id`, `amount`, `payment_date`, `remark`,
    `is_deleted`, `created_by`, `updated_by`
)
SELECT 50002, 10006, 1200.00, '2026-02-15', '额度充值', b'0', 10001, 10001
WHERE NOT EXISTS (SELECT 1 FROM `merchant_payment` WHERE id = 50002);
