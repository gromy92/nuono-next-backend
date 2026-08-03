-- Align the shipping-link scope columns with the official-warehouse MySQL 8 contract.
-- This is a metadata repair only: unknown shapes and relationship drift fail closed.
SET SESSION `lock_wait_timeout` = 5;
SET SESSION `innodb_lock_wait_timeout` = 5;

SET @scope_required_table_count := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'official_warehouse_asn',
          'official_warehouse_asn_line',
          'official_warehouse_asn_shipping_batch_link'
      )
      AND table_type = 'BASE TABLE'
      AND UPPER(engine) = 'INNODB'
);
SET @scope_required_column_count := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND (
          (
              table_name = 'official_warehouse_asn'
              AND (
                  (column_name IN ('id', 'owner_user_id') AND data_type = 'bigint'
                      AND column_type = 'bigint' AND is_nullable = 'NO')
                  OR (column_name = 'store_code' AND data_type = 'varchar'
                      AND character_maximum_length = 100 AND is_nullable = 'NO'
                      AND character_set_name = 'utf8mb4'
                      AND collation_name = 'utf8mb4_0900_ai_ci')
                  OR (column_name = 'site_code' AND data_type = 'varchar'
                      AND character_maximum_length = 20 AND is_nullable = 'NO'
                      AND character_set_name = 'utf8mb4'
                      AND collation_name = 'utf8mb4_0900_ai_ci')
                  OR (column_name = 'is_deleted' AND data_type = 'bit'
                      AND column_type = 'bit(1)' AND is_nullable = 'NO')
              )
          )
          OR (
              table_name = 'official_warehouse_asn_line'
              AND (
                  (column_name IN ('id', 'asn_id', 'owner_user_id')
                      AND data_type = 'bigint' AND column_type = 'bigint'
                      AND is_nullable = 'NO')
                  OR (column_name = 'store_code' AND data_type = 'varchar'
                      AND character_maximum_length = 100 AND is_nullable = 'NO'
                      AND character_set_name = 'utf8mb4'
                      AND collation_name = 'utf8mb4_0900_ai_ci')
                  OR (column_name = 'site_code' AND data_type = 'varchar'
                      AND character_maximum_length = 20 AND is_nullable = 'NO'
                      AND character_set_name = 'utf8mb4'
                      AND collation_name = 'utf8mb4_0900_ai_ci')
                  OR (column_name = 'is_deleted' AND data_type = 'bit'
                      AND column_type = 'bit(1)' AND is_nullable = 'NO')
              )
          )
          OR (
              table_name = 'official_warehouse_asn_shipping_batch_link'
              AND (
                  (column_name IN ('id', 'asn_id', 'asn_line_id', 'owner_user_id')
                      AND data_type = 'bigint' AND column_type = 'bigint'
                      AND is_nullable = 'NO')
                  OR (column_name = 'store_code' AND data_type = 'varchar'
                      AND character_maximum_length = 100 AND is_nullable = 'NO'
                      AND character_set_name = 'utf8mb4'
                      AND collation_name IN (
                          'utf8mb4_unicode_ci', 'utf8mb4_0900_ai_ci'
                      ))
                  OR (column_name = 'site_code' AND data_type = 'varchar'
                      AND character_maximum_length = 20 AND is_nullable = 'NO'
                      AND character_set_name = 'utf8mb4'
                      AND collation_name IN (
                          'utf8mb4_unicode_ci', 'utf8mb4_0900_ai_ci'
                      ))
                  OR (column_name = 'is_deleted' AND data_type = 'bit'
                      AND column_type = 'bit(1)' AND is_nullable = 'NO')
              )
          )
      )
);
SET @scope_product_index_exact := (
    SELECT IF(
        COUNT(*) = 5
            AND MIN(non_unique) = 1 AND MAX(non_unique) = 1
            AND MIN(UPPER(index_type)) = 'BTREE'
            AND MAX(UPPER(index_type)) = 'BTREE'
            AND SUM(sub_part IS NULL) = 5
            AND SUM(collation = 'A') = 5
            AND SUM(is_visible = 'YES') = 5
            AND SUM(expression IS NULL) = 5
            AND GROUP_CONCAT(
                CONCAT(seq_in_index, ':', column_name)
                ORDER BY seq_in_index SEPARATOR ','
            ) = '1:owner_user_id,2:store_code,3:site_code,4:product_variant_id,5:is_deleted',
        1, 0
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'official_warehouse_asn_shipping_batch_link'
      AND index_name = 'idx_official_warehouse_asn_shipping_product'
);
SET @scope_legacy_state := (
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'official_warehouse_asn_shipping_batch_link'
       AND table_collation = 'utf8mb4_unicode_ci') = 1
    AND
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'official_warehouse_asn_shipping_batch_link'
       AND column_name IN ('store_code', 'site_code')
       AND collation_name = 'utf8mb4_unicode_ci') = 2
);
SET @scope_target_state := (
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'official_warehouse_asn_shipping_batch_link'
       AND table_collation = 'utf8mb4_0900_ai_ci') = 1
    AND
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'official_warehouse_asn_shipping_batch_link'
       AND column_name IN ('store_code', 'site_code')
       AND collation_name = 'utf8mb4_0900_ai_ci') = 2
);

DROP TEMPORARY TABLE IF EXISTS `nuono_239_scope_schema_guard`;
CREATE TEMPORARY TABLE `nuono_239_scope_schema_guard` (
    `invalid_count` BIGINT NOT NULL,
    CONSTRAINT `chk_239_scope_schema` CHECK (`invalid_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_239_scope_schema_guard`
VALUES (IF(
    @scope_required_table_count = 3
        AND @scope_required_column_count = 18
        AND @scope_product_index_exact = 1
        AND (@scope_legacy_state OR @scope_target_state),
    0, 1
));
DROP TEMPORARY TABLE `nuono_239_scope_schema_guard`;

SET @scope_parent_mismatch_count := (
    SELECT COUNT(*)
    FROM official_warehouse_asn_shipping_batch_link link
    LEFT JOIN official_warehouse_asn_line parent_line
      ON parent_line.id = link.asn_line_id
    LEFT JOIN official_warehouse_asn parent_asn
      ON parent_asn.id = link.asn_id
    WHERE link.is_deleted = b'0'
      AND (
          parent_line.id IS NULL OR parent_asn.id IS NULL
          OR NOT (parent_line.is_deleted <=> b'0')
          OR NOT (parent_asn.is_deleted <=> b'0')
          OR NOT (parent_line.asn_id <=> parent_asn.id)
          OR NOT (parent_line.owner_user_id <=> parent_asn.owner_user_id)
          OR NOT (link.owner_user_id <=> parent_asn.owner_user_id)
          OR NOT (
              UPPER(parent_line.store_code) COLLATE utf8mb4_0900_ai_ci
              <=> UPPER(parent_asn.store_code) COLLATE utf8mb4_0900_ai_ci
          )
          OR NOT (
              UPPER(parent_line.site_code) COLLATE utf8mb4_0900_ai_ci
              <=> UPPER(parent_asn.site_code) COLLATE utf8mb4_0900_ai_ci
          )
          OR NOT (
              UPPER(parent_asn.store_code) COLLATE utf8mb4_0900_ai_ci
              <=> UPPER(link.store_code) COLLATE utf8mb4_0900_ai_ci
          )
          OR NOT (
              UPPER(parent_asn.site_code) COLLATE utf8mb4_0900_ai_ci
              <=> UPPER(link.site_code) COLLATE utf8mb4_0900_ai_ci
          )
      )
);
DROP TEMPORARY TABLE IF EXISTS `nuono_239_scope_data_guard`;
CREATE TEMPORARY TABLE `nuono_239_scope_data_guard` (
    `invalid_count` BIGINT NOT NULL,
    CONSTRAINT `chk_239_scope_data` CHECK (`invalid_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_239_scope_data_guard` VALUES (@scope_parent_mismatch_count);
DROP TEMPORARY TABLE `nuono_239_scope_data_guard`;

SET @align_official_warehouse_scope_collation := IF(
    @scope_target_state,
    'DO 0',
    'ALTER TABLE `official_warehouse_asn_shipping_batch_link`
        DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
        MODIFY COLUMN `store_code` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
        MODIFY COLUMN `site_code` VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL'
);
PREPARE align_official_warehouse_scope_collation_stmt
FROM @align_official_warehouse_scope_collation;
EXECUTE align_official_warehouse_scope_collation_stmt;
DEALLOCATE PREPARE align_official_warehouse_scope_collation_stmt;

DROP TEMPORARY TABLE IF EXISTS `nuono_239_scope_final_guard`;
CREATE TEMPORARY TABLE `nuono_239_scope_final_guard` (
    `invalid_count` BIGINT NOT NULL,
    CONSTRAINT `chk_239_scope_final` CHECK (`invalid_count` = 0)
) ENGINE=InnoDB;
INSERT INTO `nuono_239_scope_final_guard`
SELECT IF(
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'official_warehouse_asn_shipping_batch_link'
       AND table_collation = 'utf8mb4_0900_ai_ci') = 1
    AND
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'official_warehouse_asn_shipping_batch_link'
       AND column_name IN ('store_code', 'site_code')
       AND collation_name = 'utf8mb4_0900_ai_ci') = 2,
    0, 1
);
DROP TEMPORARY TABLE `nuono_239_scope_final_guard`;
