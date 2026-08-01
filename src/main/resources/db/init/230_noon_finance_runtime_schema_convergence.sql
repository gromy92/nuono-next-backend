-- Converge the finance fact schema and canonical five-column natural key.
SET SESSION `lock_wait_timeout` = 5;
SET SESSION `innodb_lock_wait_timeout` = 5;

CREATE TABLE IF NOT EXISTS `noon_finance_transaction_id_sequence` (
    `sequence_name` VARCHAR(80) NOT NULL,
    `next_id` BIGINT NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sequence_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `noon_finance_transaction_fact` (
    `id` BIGINT NOT NULL,
    `source_system` VARCHAR(80) NOT NULL,
    `source_batch_id` VARCHAR(160) DEFAULT NULL,
    `file_digest_sha256` VARCHAR(128) DEFAULT NULL,
    `row_hash` VARCHAR(128) NOT NULL,
    `owner_user_id` BIGINT NOT NULL,
    `store_code` VARCHAR(80) NOT NULL,
    `site_code` VARCHAR(20) NOT NULL,
    `contract_code` VARCHAR(80) DEFAULT NULL,
    `contract_title` VARCHAR(160) DEFAULT NULL,
    `reference_nr` VARCHAR(160) NOT NULL,
    `order_nr` VARCHAR(160) NOT NULL,
    `item_nr` VARCHAR(160) DEFAULT NULL,
    `order_date` DATE DEFAULT NULL,
    `transaction_date` DATE NOT NULL,
    `title` VARCHAR(1024) DEFAULT NULL,
    `sku` VARCHAR(160) DEFAULT NULL,
    `partner_sku` VARCHAR(160) DEFAULT NULL,
    `transaction_type` VARCHAR(80) NOT NULL,
    `currency` VARCHAR(20) NOT NULL,
    `net_proceeds` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `referral_fee_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `fulfillment_logistics_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `shipping_credits_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `other_order_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `order_subsidies_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `non_order_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `non_order_subsidies_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `others_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `total_amount` DECIMAL(18,6) NOT NULL DEFAULT 0,
    `report_date_from` DATE NOT NULL,
    `report_date_to` DATE NOT NULL,
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_noon_finance_transaction_fact_natural`
        (`source_system`, `owner_user_id`, `store_code`, `site_code`, `row_hash`),
    KEY `idx_noon_finance_scope_transaction_date`
        (`owner_user_id`, `store_code`, `site_code`, `transaction_date`),
    KEY `idx_noon_finance_sku`
        (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `sku`),
    KEY `idx_noon_finance_order`
        (`owner_user_id`, `store_code`, `site_code`, `order_nr`),
    KEY `idx_noon_finance_batch` (`source_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A duplicate natural key needs a separately scoped, evidenced data repair.
-- This guard deliberately fails without deleting or rewriting business rows.
SET @finance_duplicate_group_count := (
    SELECT COUNT(*)
    FROM (
        SELECT 1
        FROM `noon_finance_transaction_fact`
        GROUP BY
            `source_system`,
            `owner_user_id`,
            `store_code`,
            `site_code`,
            `row_hash`
        HAVING COUNT(*) > 1
    ) AS `duplicate_groups`
);

DROP TEMPORARY TABLE IF EXISTS `nuono_230_finance_duplicate_guard`;

CREATE TEMPORARY TABLE `nuono_230_finance_duplicate_guard` (
    `duplicate_group_count` BIGINT NOT NULL,
    CONSTRAINT `chk_230_no_finance_duplicate_groups`
        CHECK (`duplicate_group_count` = 0)
) ENGINE=MEMORY;

INSERT INTO `nuono_230_finance_duplicate_guard` (`duplicate_group_count`)
VALUES (@finance_duplicate_group_count);

DROP TEMPORARY TABLE `nuono_230_finance_duplicate_guard`;

SET @finance_key_exists := EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'noon_finance_transaction_fact'
      AND index_name = 'uk_noon_finance_transaction_fact_natural'
);

SET @finance_key_is_exact := (
    SELECT IF(
        COUNT(*) = 5
        AND MIN(`non_unique`) = 0
        AND MAX(`non_unique`) = 0
        AND MIN(`seq_in_index`) = 1
        AND MAX(`seq_in_index`) = 5
        AND COUNT(DISTINCT `seq_in_index`) = 5
        AND SUM(`sub_part` IS NULL) = 5
        AND MIN(UPPER(`index_type`)) = 'BTREE'
        AND MAX(UPPER(`index_type`)) = 'BTREE'
        AND MIN(`collation`) = 'A'
        AND MAX(`collation`) = 'A'
        AND SUM(`is_visible` = 'YES') = 5
        AND SUM(`expression` IS NULL) = 5
        AND GROUP_CONCAT(
            CONCAT(`seq_in_index`, ':', `column_name`)
            ORDER BY `seq_in_index`
            SEPARATOR ','
        ) = '1:source_system,2:owner_user_id,3:store_code,4:site_code,5:row_hash',
        1,
        0
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'noon_finance_transaction_fact'
      AND index_name = 'uk_noon_finance_transaction_fact_natural'
);

SET @finance_shadow_key_exists := EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'noon_finance_transaction_fact'
      AND index_name =
          'uk_noon_finance_transaction_fact_natural_shadow_230'
);

SET @finance_shadow_key_is_exact := (
    SELECT IF(
        COUNT(*) = 5
        AND MIN(`non_unique`) = 0
        AND MAX(`non_unique`) = 0
        AND MIN(`seq_in_index`) = 1
        AND MAX(`seq_in_index`) = 5
        AND COUNT(DISTINCT `seq_in_index`) = 5
        AND SUM(`sub_part` IS NULL) = 5
        AND MIN(UPPER(`index_type`)) = 'BTREE'
        AND MAX(UPPER(`index_type`)) = 'BTREE'
        AND MIN(`collation`) = 'A'
        AND MAX(`collation`) = 'A'
        AND SUM(`is_visible` = 'YES') = 5
        AND SUM(`expression` IS NULL) = 5
        AND GROUP_CONCAT(
            CONCAT(`seq_in_index`, ':', `column_name`)
            ORDER BY `seq_in_index`
            SEPARATOR ','
        ) = '1:source_system,2:owner_user_id,3:store_code,4:site_code,5:row_hash',
        1,
        0
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'noon_finance_transaction_fact'
      AND index_name =
          'uk_noon_finance_transaction_fact_natural_shadow_230'
);

-- The fixed shadow name is reserved by this migration. Never overwrite or drop
-- an unexpected index using that name; require an operator to inspect it.
DROP TEMPORARY TABLE IF EXISTS `nuono_230_finance_shadow_guard`;

CREATE TEMPORARY TABLE `nuono_230_finance_shadow_guard` (
    `conflicting_shadow_index_count` BIGINT NOT NULL,
    CONSTRAINT `chk_230_no_conflicting_finance_shadow_index`
        CHECK (`conflicting_shadow_index_count` = 0)
) ENGINE=MEMORY;

INSERT INTO `nuono_230_finance_shadow_guard`
    (`conflicting_shadow_index_count`)
VALUES (
    IF(
        @finance_shadow_key_exists = 1
            AND @finance_shadow_key_is_exact = 0,
        1,
        0
    )
);

DROP TEMPORARY TABLE `nuono_230_finance_shadow_guard`;

-- If the canonical name is free, either promote an exact shadow left by an
-- interrupted run or add the canonical key directly.
SET @finance_promote_or_add_key_sql := IF(
    @finance_key_exists = 0,
    IF(
        @finance_shadow_key_exists = 1,
        'ALTER TABLE `noon_finance_transaction_fact` RENAME INDEX `uk_noon_finance_transaction_fact_natural_shadow_230` TO `uk_noon_finance_transaction_fact_natural`',
        'ALTER TABLE `noon_finance_transaction_fact` ADD UNIQUE KEY `uk_noon_finance_transaction_fact_natural` (`source_system`, `owner_user_id`, `store_code`, `site_code`, `row_hash`)'
    ),
    'DO 0'
);
PREPARE finance_promote_or_add_key_stmt
    FROM @finance_promote_or_add_key_sql;
EXECUTE finance_promote_or_add_key_stmt;
DEALLOCATE PREPARE finance_promote_or_add_key_stmt;

-- When a wrong canonical index exists, first build the intended UNIQUE under a
-- fixed shadow name. Any failure leaves the existing canonical index intact.
SET @finance_add_shadow_key_sql := IF(
    @finance_key_exists = 1
        AND @finance_key_is_exact = 0
        AND @finance_shadow_key_exists = 0,
    'ALTER TABLE `noon_finance_transaction_fact` ADD UNIQUE KEY `uk_noon_finance_transaction_fact_natural_shadow_230` (`source_system`, `owner_user_id`, `store_code`, `site_code`, `row_hash`)',
    'DO 0'
);
PREPARE finance_add_shadow_key_stmt FROM @finance_add_shadow_key_sql;
EXECUTE finance_add_shadow_key_stmt;
DEALLOCATE PREPARE finance_add_shadow_key_stmt;

-- DROP + RENAME is one atomic InnoDB ALTER. If it fails, MySQL rolls the
-- statement back, so the pre-existing canonical index is not weakened.
SET @finance_swap_key_sql := IF(
    @finance_key_exists = 1 AND @finance_key_is_exact = 0,
    'ALTER TABLE `noon_finance_transaction_fact` DROP INDEX `uk_noon_finance_transaction_fact_natural`, RENAME INDEX `uk_noon_finance_transaction_fact_natural_shadow_230` TO `uk_noon_finance_transaction_fact_natural`',
    'DO 0'
);
PREPARE finance_swap_key_stmt FROM @finance_swap_key_sql;
EXECUTE finance_swap_key_stmt;
DEALLOCATE PREPARE finance_swap_key_stmt;

-- next_id is the last allocated value. Preserve a higher existing sequence and
-- catch up to restored/imported facts before application writes resume.
INSERT INTO `noon_finance_transaction_id_sequence`
    (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)
VALUES (
    'finance_transaction_fact',
    GREATEST(
        300000,
        COALESCE(
            (SELECT MAX(`id`) FROM `noon_finance_transaction_fact`),
            300000
        )
    ),
    NOW(),
    NOW()
) AS `incoming`
ON DUPLICATE KEY UPDATE
    `next_id` = GREATEST(
        `noon_finance_transaction_id_sequence`.`next_id`,
        `incoming`.`next_id`
    ),
    `gmt_updated` = NOW();
