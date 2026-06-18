package com.nuono.next.infrastructure.mapper;

import com.nuono.next.orderfinance.NoonFinanceTransactionFact;
import com.nuono.next.orderfinance.OrderFinanceDataStatus;
import com.nuono.next.orderfinance.OrderFinanceQuery;
import com.nuono.next.orderfinance.OrderFinanceSkuSummaryRow;
import com.nuono.next.orderfinance.OrderFinanceSummaryView;
import com.nuono.next.orderfinance.OrderFinanceTransactionLine;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonFinanceTransactionMapper {

    @Update({
            "CREATE TABLE IF NOT EXISTS `noon_finance_transaction_id_sequence` (",
            "  `sequence_name` VARCHAR(80) NOT NULL,",
            "  `next_id` BIGINT NOT NULL,",
            "  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,",
            "  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,",
            "  PRIMARY KEY (`sequence_name`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    })
    void ensureIdSequenceTable();

    @Insert({
            "INSERT INTO `noon_finance_transaction_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)",
            "VALUES ('finance_transaction_fact', 300000, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  `next_id` = `next_id`,",
            "  `gmt_updated` = VALUES(`gmt_updated`)"
    })
    void ensureFactSequence();

    @Update({
            "CREATE TABLE IF NOT EXISTS `noon_finance_transaction_fact` (",
            "  `id` BIGINT NOT NULL,",
            "  `source_system` VARCHAR(80) NOT NULL,",
            "  `source_batch_id` VARCHAR(160) DEFAULT NULL,",
            "  `file_digest_sha256` VARCHAR(128) DEFAULT NULL,",
            "  `row_hash` VARCHAR(128) NOT NULL,",
            "  `owner_user_id` BIGINT NOT NULL,",
            "  `store_code` VARCHAR(80) NOT NULL,",
            "  `site_code` VARCHAR(20) NOT NULL,",
            "  `contract_code` VARCHAR(80) DEFAULT NULL,",
            "  `contract_title` VARCHAR(160) DEFAULT NULL,",
            "  `reference_nr` VARCHAR(160) NOT NULL,",
            "  `order_nr` VARCHAR(160) NOT NULL,",
            "  `item_nr` VARCHAR(160) DEFAULT NULL,",
            "  `order_date` DATE DEFAULT NULL,",
            "  `transaction_date` DATE NOT NULL,",
            "  `title` VARCHAR(1024) DEFAULT NULL,",
            "  `sku` VARCHAR(160) DEFAULT NULL,",
            "  `partner_sku` VARCHAR(160) DEFAULT NULL,",
            "  `transaction_type` VARCHAR(80) NOT NULL,",
            "  `currency` VARCHAR(20) NOT NULL,",
            "  `net_proceeds` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `referral_fee_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `fulfillment_logistics_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `shipping_credits_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `other_order_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `order_subsidies_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `non_order_fees_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `non_order_subsidies_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `others_including_vat` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `total_amount` DECIMAL(18,6) NOT NULL DEFAULT 0,",
            "  `report_date_from` DATE NOT NULL,",
            "  `report_date_to` DATE NOT NULL,",
            "  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,",
            "  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,",
            "  PRIMARY KEY (`id`),",
            "  UNIQUE KEY `uk_noon_finance_transaction_fact_natural` (",
            "    `source_system`, `owner_user_id`, `store_code`, `site_code`, `row_hash`",
            "  ),",
            "  KEY `idx_noon_finance_scope_transaction_date` (`owner_user_id`, `store_code`, `site_code`, `transaction_date`),",
            "  KEY `idx_noon_finance_sku` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `sku`),",
            "  KEY `idx_noon_finance_order` (`owner_user_id`, `store_code`, `site_code`, `order_nr`),",
            "  KEY `idx_noon_finance_batch` (`source_batch_id`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    })
    void ensureFactTable();

    @Select({
            "SELECT COUNT(1)",
            "FROM INFORMATION_SCHEMA.STATISTICS",
            "WHERE TABLE_SCHEMA = DATABASE()",
            "  AND TABLE_NAME = 'noon_finance_transaction_fact'",
            "  AND INDEX_NAME = 'uk_noon_finance_transaction_fact_natural'",
            "  AND COLUMN_NAME = 'row_hash'"
    })
    int countNaturalUniqueKeyRowHashColumn();

    @Select({
            "SELECT COUNT(DISTINCT INDEX_NAME)",
            "FROM INFORMATION_SCHEMA.STATISTICS",
            "WHERE TABLE_SCHEMA = DATABASE()",
            "  AND TABLE_NAME = 'noon_finance_transaction_fact'",
            "  AND INDEX_NAME = 'uk_noon_finance_transaction_fact_natural'"
    })
    int countNaturalUniqueKey();

    @Select({
            "SELECT COUNT(1)",
            "FROM INFORMATION_SCHEMA.STATISTICS",
            "WHERE TABLE_SCHEMA = DATABASE()",
            "  AND TABLE_NAME = 'noon_finance_transaction_fact'",
            "  AND INDEX_NAME = 'uk_noon_finance_transaction_fact_natural'"
    })
    int countNaturalUniqueKeyColumns();

    @Select({
            "SELECT COUNT(1)",
            "FROM INFORMATION_SCHEMA.STATISTICS",
            "WHERE TABLE_SCHEMA = DATABASE()",
            "  AND TABLE_NAME = 'noon_finance_transaction_fact'",
            "  AND INDEX_NAME = 'uk_noon_finance_transaction_fact_natural'",
            "  AND COLUMN_NAME IN ('source_system', 'owner_user_id', 'store_code', 'site_code', 'row_hash')"
    })
    int countNaturalUniqueKeyExpectedColumns();

    @Update({
            "DELETE victim",
            "FROM noon_finance_transaction_fact victim",
            "JOIN noon_finance_transaction_fact keeper",
            "  ON keeper.source_system = victim.source_system",
            "  AND keeper.owner_user_id = victim.owner_user_id",
            "  AND keeper.store_code = victim.store_code",
            "  AND keeper.site_code = victim.site_code",
            "  AND keeper.row_hash = victim.row_hash",
            "  AND (",
            "    keeper.gmt_updated > victim.gmt_updated",
            "    OR (keeper.gmt_updated = victim.gmt_updated AND keeper.id > victim.id)",
            "  )"
    })
    int deleteDuplicateNaturalKeyRows();

    @Update({
            "ALTER TABLE noon_finance_transaction_fact",
            "DROP INDEX uk_noon_finance_transaction_fact_natural"
    })
    void dropNaturalUniqueKey();

    @Update({
            "ALTER TABLE noon_finance_transaction_fact",
            "ADD UNIQUE KEY uk_noon_finance_transaction_fact_natural (",
            "  source_system, owner_user_id, store_code, site_code, row_hash",
            ")"
    })
    void addNaturalUniqueKey();

    default void ensureFactNaturalUniqueKey() {
        int existingKeys = countNaturalUniqueKey();
        int keyColumns = countNaturalUniqueKeyColumns();
        int expectedColumns = countNaturalUniqueKeyExpectedColumns();
        if (existingKeys > 0 && (keyColumns != 5 || expectedColumns != 5)) {
            dropNaturalUniqueKey();
            deleteDuplicateNaturalKeyRows();
            addNaturalUniqueKey();
            return;
        }
        if (existingKeys == 0) {
            deleteDuplicateNaturalKeyRows();
            addNaturalUniqueKey();
        }
    }

    @Select({
            "SELECT COUNT(1)",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND lss.store_code = #{storeCode}",
            "  AND UPPER(TRIM(lss.site)) = UPPER(TRIM(#{siteCode}))",
            "  AND lss.is_deleted = b'0'"
    })
    int countActiveStoreSite(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Insert({
            "INSERT INTO noon_finance_transaction_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    void nextId(IdSequenceCommand command);

    default Long nextFinanceTransactionFactId() {
        IdSequenceCommand command = new IdSequenceCommand("finance_transaction_fact", 300000L);
        nextId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Noon finance transaction fact ID allocation failed.");
        }
        return id;
    }

    @Select({
            "SELECT MAX(transaction_date)",
            "FROM noon_finance_transaction_fact",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND LOWER(TRIM(transaction_type)) IN ('order', 'order_update')"
    })
    LocalDate selectLatestOrderFinanceTransactionDate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Insert({
            "INSERT INTO noon_finance_transaction_fact (",
            "  id, source_system, source_batch_id, file_digest_sha256, row_hash,",
            "  owner_user_id, store_code, site_code, contract_code, contract_title, reference_nr,",
            "  order_nr, item_nr, order_date, transaction_date, title, sku, partner_sku,",
            "  transaction_type, currency, net_proceeds, referral_fee_including_vat,",
            "  fulfillment_logistics_fees_including_vat, shipping_credits_including_vat,",
            "  other_order_fees_including_vat, order_subsidies_including_vat,",
            "  non_order_fees_including_vat, non_order_subsidies_including_vat, others_including_vat,",
            "  total_amount, report_date_from, report_date_to, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, 'noon_finance_transaction_report', #{fact.sourceBatchId}, #{fact.fileDigestSha256}, #{fact.rowHash},",
            "  #{fact.ownerUserId}, #{fact.storeCode}, #{fact.siteCode}, #{fact.contractCode}, #{fact.contractTitle}, #{fact.referenceNr},",
            "  #{fact.orderNr}, #{fact.itemNr}, #{fact.orderDate}, #{fact.transactionDate}, #{fact.title}, #{fact.sku}, #{fact.partnerSku},",
            "  #{fact.transactionType}, #{fact.currency}, #{fact.netProceeds}, #{fact.referralFeeIncludingVat},",
            "  #{fact.fulfillmentLogisticsFeesIncludingVat}, #{fact.shippingCreditsIncludingVat},",
            "  #{fact.otherOrderFeesIncludingVat}, #{fact.orderSubsidiesIncludingVat},",
            "  #{fact.nonOrderFeesIncludingVat}, #{fact.nonOrderSubsidiesIncludingVat}, #{fact.othersIncludingVat},",
            "  #{fact.totalAmount}, #{fact.reportDateFrom}, #{fact.reportDateTo}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  source_batch_id = VALUES(source_batch_id),",
            "  file_digest_sha256 = VALUES(file_digest_sha256),",
            "  row_hash = VALUES(row_hash),",
            "  contract_code = VALUES(contract_code),",
            "  contract_title = VALUES(contract_title),",
            "  order_date = VALUES(order_date),",
            "  title = VALUES(title),",
            "  sku = VALUES(sku),",
            "  partner_sku = VALUES(partner_sku),",
            "  currency = VALUES(currency),",
            "  net_proceeds = VALUES(net_proceeds),",
            "  referral_fee_including_vat = VALUES(referral_fee_including_vat),",
            "  fulfillment_logistics_fees_including_vat = VALUES(fulfillment_logistics_fees_including_vat),",
            "  shipping_credits_including_vat = VALUES(shipping_credits_including_vat),",
            "  other_order_fees_including_vat = VALUES(other_order_fees_including_vat),",
            "  order_subsidies_including_vat = VALUES(order_subsidies_including_vat),",
            "  non_order_fees_including_vat = VALUES(non_order_fees_including_vat),",
            "  non_order_subsidies_including_vat = VALUES(non_order_subsidies_including_vat),",
            "  others_including_vat = VALUES(others_including_vat),",
            "  total_amount = VALUES(total_amount),",
            "  report_date_from = VALUES(report_date_from),",
            "  report_date_to = VALUES(report_date_to),",
            "  gmt_updated = NOW()"
    })
    int upsertFinanceTransactionFact(@Param("id") Long id, @Param("fact") NoonFinanceTransactionFact fact);

    @Select({
            "<script>",
            "SELECT",
            "  CASE",
            "    WHEN COUNT(DISTINCT currency) = 0 THEN NULL",
            "    WHEN COUNT(DISTINCT currency) = 1 THEN MAX(currency)",
            "    ELSE 'MIXED'",
            "  END AS currency,",
            "  CASE WHEN COUNT(DISTINCT currency) &gt; 1 THEN TRUE ELSE FALSE END AS mixedCurrency,",
            "  COUNT(DISTINCT CASE WHEN NULLIF(TRIM(order_nr), '') IS NOT NULL AND UPPER(TRIM(order_nr)) != 'NA' THEN order_nr END) AS orderCount,",
            "  COUNT(DISTINCT NULLIF(TRIM(item_nr), '')) AS itemCount,",
            "  COUNT(1) AS transactionRowCount,",
            "  SUM(CASE WHEN LOWER(TRIM(transaction_type)) = 'order_update' THEN 1 ELSE 0 END) AS orderUpdateRowCount,",
            "  COALESCE(SUM(net_proceeds), 0) AS netProceeds,",
            "  COALESCE(SUM(referral_fee_including_vat), 0) AS referralFee,",
            "  COALESCE(SUM(fulfillment_logistics_fees_including_vat), 0) AS fulfillmentLogisticsFee,",
            "  COALESCE(SUM(other_order_fees_including_vat), 0) AS otherOrderFee,",
            "  COALESCE(SUM(total_amount), 0) AS totalAmount",
            "FROM noon_finance_transaction_fact nft",
            "WHERE nft.owner_user_id = #{query.ownerUserId}",
            "  AND nft.store_code = #{query.storeCode}",
            "  AND nft.site_code = #{query.siteCode}",
            "  AND nft.transaction_date &gt;= #{query.dateFrom}",
            "  AND nft.transaction_date &lt;= #{query.dateTo}",
            "  AND LOWER(TRIM(nft.transaction_type)) IN ('order', 'order_update')",
            "<if test='query.currency != null and query.currency != \"\"'>",
            "  AND nft.currency = #{query.currency}",
            "</if>",
            "<if test='query.search != null and query.search != \"\"'>",
            "  AND (LOWER(COALESCE(nft.partner_sku, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "    OR LOWER(COALESCE(nft.sku, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "    OR LOWER(COALESCE(nft.title, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM logical_store_site search_lss",
            "      JOIN logical_store search_ls",
            "        ON search_ls.id = search_lss.logical_store_id",
            "       AND search_ls.is_deleted = b'0'",
            "      JOIN product_master search_pm",
            "        ON search_pm.logical_store_id = search_ls.id",
            "       AND search_pm.is_deleted = b'0'",
            "      JOIN product_variant search_pv",
            "        ON search_pv.product_master_id = search_pm.id",
            "       AND search_pv.is_deleted = b'0'",
            "      LEFT JOIN product_site_offer search_pso",
            "        ON search_pso.site_id = search_lss.id",
            "       AND search_pso.variant_id = search_pv.id",
            "       AND search_pso.is_deleted = b'0'",
            "      WHERE search_ls.owner_user_id = nft.owner_user_id",
            "        AND search_lss.store_code = nft.store_code",
            "        AND search_lss.site = nft.site_code",
            "        AND search_lss.is_deleted = b'0'",
            "        AND (search_pv.partner_sku = nft.partner_sku",
            "          OR search_pso.psku_code = nft.partner_sku",
            "          OR search_pso.offer_code = nft.partner_sku)",
            "        AND (LOWER(COALESCE(search_pm.title_cn_cache, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "          OR LOWER(COALESCE(search_pm.title_cache, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%'))",
            "    ))",
            "</if>",
            "<if test='query.partnerSkuList != null and query.partnerSkuList.size() &gt; 0'>",
            "  AND (nft.partner_sku IN",
            "  <foreach item='partnerSku' collection='query.partnerSkuList' open='(' separator=',' close=')'>",
            "    #{partnerSku}",
            "  </foreach>",
            "  OR (#{query.missingPartnerSkuSelected} = TRUE AND NULLIF(TRIM(nft.partner_sku), '') IS NULL))",
            "</if>",
            "</script>"
    })
    OrderFinanceSummaryView selectOverallSummary(@Param("query") OrderFinanceQuery query);

    @Select({
            "<script>",
            "SELECT",
            "  currency,",
            "  FALSE AS mixedCurrency,",
            "  COUNT(DISTINCT CASE WHEN NULLIF(TRIM(order_nr), '') IS NOT NULL AND UPPER(TRIM(order_nr)) != 'NA' THEN order_nr END) AS orderCount,",
            "  COUNT(DISTINCT NULLIF(TRIM(item_nr), '')) AS itemCount,",
            "  COUNT(1) AS transactionRowCount,",
            "  SUM(CASE WHEN LOWER(TRIM(transaction_type)) = 'order_update' THEN 1 ELSE 0 END) AS orderUpdateRowCount,",
            "  COALESCE(SUM(net_proceeds), 0) AS netProceeds,",
            "  COALESCE(SUM(referral_fee_including_vat), 0) AS referralFee,",
            "  COALESCE(SUM(fulfillment_logistics_fees_including_vat), 0) AS fulfillmentLogisticsFee,",
            "  COALESCE(SUM(other_order_fees_including_vat), 0) AS otherOrderFee,",
            "  COALESCE(SUM(total_amount), 0) AS totalAmount",
            "FROM noon_finance_transaction_fact nft",
            "WHERE nft.owner_user_id = #{query.ownerUserId}",
            "  AND nft.store_code = #{query.storeCode}",
            "  AND nft.site_code = #{query.siteCode}",
            "  AND nft.transaction_date &gt;= #{query.dateFrom}",
            "  AND nft.transaction_date &lt;= #{query.dateTo}",
            "  AND LOWER(TRIM(nft.transaction_type)) IN ('order', 'order_update')",
            "<if test='query.currency != null and query.currency != \"\"'>",
            "  AND nft.currency = #{query.currency}",
            "</if>",
            "<if test='query.search != null and query.search != \"\"'>",
            "  AND (LOWER(COALESCE(nft.partner_sku, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "    OR LOWER(COALESCE(nft.sku, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "    OR LOWER(COALESCE(nft.title, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM logical_store_site search_lss",
            "      JOIN logical_store search_ls",
            "        ON search_ls.id = search_lss.logical_store_id",
            "       AND search_ls.is_deleted = b'0'",
            "      JOIN product_master search_pm",
            "        ON search_pm.logical_store_id = search_ls.id",
            "       AND search_pm.is_deleted = b'0'",
            "      JOIN product_variant search_pv",
            "        ON search_pv.product_master_id = search_pm.id",
            "       AND search_pv.is_deleted = b'0'",
            "      LEFT JOIN product_site_offer search_pso",
            "        ON search_pso.site_id = search_lss.id",
            "       AND search_pso.variant_id = search_pv.id",
            "       AND search_pso.is_deleted = b'0'",
            "      WHERE search_ls.owner_user_id = nft.owner_user_id",
            "        AND search_lss.store_code = nft.store_code",
            "        AND search_lss.site = nft.site_code",
            "        AND search_lss.is_deleted = b'0'",
            "        AND (search_pv.partner_sku = nft.partner_sku",
            "          OR search_pso.psku_code = nft.partner_sku",
            "          OR search_pso.offer_code = nft.partner_sku)",
            "        AND (LOWER(COALESCE(search_pm.title_cn_cache, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "          OR LOWER(COALESCE(search_pm.title_cache, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%'))",
            "    ))",
            "</if>",
            "<if test='query.partnerSkuList != null and query.partnerSkuList.size() &gt; 0'>",
            "  AND (nft.partner_sku IN",
            "  <foreach item='partnerSku' collection='query.partnerSkuList' open='(' separator=',' close=')'>",
            "    #{partnerSku}",
            "  </foreach>",
            "  OR (#{query.missingPartnerSkuSelected} = TRUE AND NULLIF(TRIM(nft.partner_sku), '') IS NULL))",
            "</if>",
            "GROUP BY nft.currency",
            "ORDER BY nft.currency ASC",
            "</script>"
    })
    List<OrderFinanceSummaryView> selectCurrencySummaryRows(@Param("query") OrderFinanceQuery query);

    @Select({
            "<script>",
            "SELECT",
            "  finance.partnerSku,",
            "  finance.sku,",
            "  COALESCE(product.title, finance.title) AS title,",
            "  product.imageUrl,",
            "  finance.currency,",
            "  finance.orderCount,",
            "  finance.itemCount,",
            "  finance.transactionRowCount,",
            "  finance.orderUpdateRowCount,",
            "  finance.netProceeds,",
            "  finance.referralFee,",
            "  finance.fulfillmentLogisticsFee,",
            "  finance.otherOrderFee,",
            "  finance.totalAmount,",
            "  finance.avgTotalPerOrder,",
            "  finance.avgFulfillmentFeePerItem,",
            "  finance.feeRate,",
            "  finance.missingPartnerSku",
            "FROM (",
            "  SELECT",
            "    CASE WHEN NULLIF(TRIM(partner_sku), '') IS NULL THEN '(missing)' ELSE partner_sku END AS partnerSku,",
            "    sku,",
            "    MAX(title) AS title,",
            "    currency,",
            "    COUNT(DISTINCT CASE WHEN NULLIF(TRIM(order_nr), '') IS NOT NULL AND UPPER(TRIM(order_nr)) != 'NA' THEN order_nr END) AS orderCount,",
            "    COUNT(DISTINCT NULLIF(TRIM(item_nr), '')) AS itemCount,",
            "    COUNT(1) AS transactionRowCount,",
            "    SUM(CASE WHEN LOWER(TRIM(transaction_type)) = 'order_update' THEN 1 ELSE 0 END) AS orderUpdateRowCount,",
            "    COALESCE(SUM(net_proceeds), 0) AS netProceeds,",
            "    COALESCE(SUM(referral_fee_including_vat), 0) AS referralFee,",
            "    COALESCE(SUM(fulfillment_logistics_fees_including_vat), 0) AS fulfillmentLogisticsFee,",
            "    COALESCE(SUM(other_order_fees_including_vat), 0) AS otherOrderFee,",
            "    COALESCE(SUM(total_amount), 0) AS totalAmount,",
            "    CASE WHEN COUNT(DISTINCT CASE WHEN NULLIF(TRIM(order_nr), '') IS NOT NULL AND UPPER(TRIM(order_nr)) != 'NA' THEN order_nr END) = 0",
            "      THEN NULL ELSE COALESCE(SUM(total_amount), 0) / COUNT(DISTINCT CASE WHEN NULLIF(TRIM(order_nr), '') IS NOT NULL AND UPPER(TRIM(order_nr)) != 'NA' THEN order_nr END) END AS avgTotalPerOrder,",
            "    CASE WHEN COUNT(DISTINCT NULLIF(TRIM(item_nr), '')) = 0",
            "      THEN NULL ELSE COALESCE(SUM(fulfillment_logistics_fees_including_vat), 0) / COUNT(DISTINCT NULLIF(TRIM(item_nr), '')) END AS avgFulfillmentFeePerItem,",
            "    CASE WHEN COALESCE(SUM(net_proceeds), 0) = 0 THEN NULL",
            "      ELSE ABS(COALESCE(SUM(referral_fee_including_vat), 0) + COALESCE(SUM(fulfillment_logistics_fees_including_vat), 0) + COALESCE(SUM(other_order_fees_including_vat), 0)) / COALESCE(SUM(net_proceeds), 0) END AS feeRate,",
            "    MAX(CASE WHEN NULLIF(TRIM(partner_sku), '') IS NULL THEN TRUE ELSE FALSE END) AS missingPartnerSku",
            "  FROM noon_finance_transaction_fact",
            "  WHERE owner_user_id = #{query.ownerUserId}",
            "    AND store_code = #{query.storeCode}",
            "    AND site_code = #{query.siteCode}",
            "    AND transaction_date &gt;= #{query.dateFrom}",
            "    AND transaction_date &lt;= #{query.dateTo}",
            "    AND LOWER(TRIM(transaction_type)) IN ('order', 'order_update')",
            "<if test='query.currency != null and query.currency != \"\"'>",
            "    AND currency = #{query.currency}",
            "</if>",
            "<if test='query.partnerSkuList != null and query.partnerSkuList.size() &gt; 0'>",
            "    AND (partner_sku IN",
            "    <foreach item='partnerSku' collection='query.partnerSkuList' open='(' separator=',' close=')'>",
            "      #{partnerSku}",
            "    </foreach>",
            "    OR (#{query.missingPartnerSkuSelected} = TRUE AND NULLIF(TRIM(partner_sku), '') IS NULL))",
            "</if>",
            "  GROUP BY CASE WHEN NULLIF(TRIM(partner_sku), '') IS NULL THEN '(missing)' ELSE partner_sku END, sku, currency",
            ") finance",
            "LEFT JOIN (",
            "  SELECT",
            "    product_candidates.ownerUserId,",
            "    product_candidates.storeCode,",
            "    product_candidates.siteCode,",
            "    product_candidates.partnerSku,",
            "    MAX(product_candidates.title) AS title,",
            "    MAX(product_candidates.imageUrl) AS imageUrl",
            "  FROM (",
            "    SELECT ls.owner_user_id AS ownerUserId, lss.store_code AS storeCode, lss.site AS siteCode, pv.partner_sku AS partnerSku, COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title, pm.cover_image_url AS imageUrl",
            "    FROM logical_store_site lss",
            "    JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "    JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "    JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "    WHERE ls.owner_user_id = #{query.ownerUserId}",
            "      AND lss.store_code = #{query.storeCode}",
            "      AND lss.site = #{query.siteCode}",
            "      AND lss.is_deleted = b'0'",
            "      AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL",
            "    UNION ALL",
            "    SELECT ls.owner_user_id AS ownerUserId, lss.store_code AS storeCode, lss.site AS siteCode, pso.psku_code AS partnerSku, COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title, pm.cover_image_url AS imageUrl",
            "    FROM logical_store_site lss",
            "    JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "    JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "    JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "    JOIN product_site_offer pso ON pso.site_id = lss.id AND pso.variant_id = pv.id AND pso.is_deleted = b'0'",
            "    WHERE ls.owner_user_id = #{query.ownerUserId}",
            "      AND lss.store_code = #{query.storeCode}",
            "      AND lss.site = #{query.siteCode}",
            "      AND lss.is_deleted = b'0'",
            "      AND NULLIF(TRIM(pso.psku_code), '') IS NOT NULL",
            "    UNION ALL",
            "    SELECT ls.owner_user_id AS ownerUserId, lss.store_code AS storeCode, lss.site AS siteCode, pso.offer_code AS partnerSku, COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title, pm.cover_image_url AS imageUrl",
            "    FROM logical_store_site lss",
            "    JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "    JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "    JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "    JOIN product_site_offer pso ON pso.site_id = lss.id AND pso.variant_id = pv.id AND pso.is_deleted = b'0'",
            "    WHERE ls.owner_user_id = #{query.ownerUserId}",
            "      AND lss.store_code = #{query.storeCode}",
            "      AND lss.site = #{query.siteCode}",
            "      AND lss.is_deleted = b'0'",
            "      AND NULLIF(TRIM(pso.offer_code), '') IS NOT NULL",
            "  ) product_candidates",
            "  GROUP BY product_candidates.ownerUserId, product_candidates.storeCode, product_candidates.siteCode, product_candidates.partnerSku",
            ") product ON product.ownerUserId = #{query.ownerUserId}",
            "  AND product.storeCode = #{query.storeCode}",
            "  AND product.siteCode = #{query.siteCode}",
            "  AND product.partnerSku = finance.partnerSku",
            "<if test='query.search != null and query.search != \"\"'>",
            "WHERE (LOWER(COALESCE(finance.partnerSku, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "  OR LOWER(COALESCE(finance.sku, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "  OR LOWER(COALESCE(finance.title, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%')",
            "  OR LOWER(COALESCE(product.title, '')) LIKE CONCAT('%', LOWER(#{query.search}), '%'))",
            "</if>",
            "ORDER BY finance.totalAmount DESC, finance.partnerSku ASC, finance.sku ASC, finance.currency ASC",
            "</script>"
    })
    List<OrderFinanceSkuSummaryRow> selectSkuSummaryRows(@Param("query") OrderFinanceQuery query);

    @Select({
            "<script>",
            "SELECT",
            "  id,",
            "  source_batch_id AS sourceBatchId,",
            "  reference_nr AS referenceNr,",
            "  order_nr AS orderNr,",
            "  item_nr AS itemNr,",
            "  order_date AS orderDate,",
            "  transaction_date AS transactionDate,",
            "  title,",
            "  sku,",
            "  partner_sku AS partnerSku,",
            "  transaction_type AS transactionType,",
            "  currency,",
            "  net_proceeds AS netProceeds,",
            "  referral_fee_including_vat AS referralFee,",
            "  fulfillment_logistics_fees_including_vat AS fulfillmentLogisticsFee,",
            "  shipping_credits_including_vat AS shippingCredits,",
            "  other_order_fees_including_vat AS otherOrderFee,",
            "  order_subsidies_including_vat AS orderSubsidies,",
            "  non_order_fees_including_vat AS nonOrderFees,",
            "  non_order_subsidies_including_vat AS nonOrderSubsidies,",
            "  others_including_vat AS others,",
            "  total_amount AS totalAmount",
            "FROM noon_finance_transaction_fact",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "  AND transaction_date &gt;= #{query.dateFrom}",
            "  AND transaction_date &lt;= #{query.dateTo}",
            "  AND LOWER(TRIM(transaction_type)) IN ('order', 'order_update')",
            "<if test='query.currency != null and query.currency != \"\"'>",
            "  AND currency = #{query.currency}",
            "</if>",
            "<choose>",
            "  <when test='query.missingPartnerSku'>",
            "    AND NULLIF(TRIM(partner_sku), '') IS NULL",
            "  </when>",
            "  <otherwise>",
            "    AND partner_sku = #{query.partnerSku}",
            "  </otherwise>",
            "</choose>",
            "<if test='query.sku != null and query.sku != \"\"'>",
            "  AND sku = #{query.sku}",
            "</if>",
            "ORDER BY order_nr ASC, transaction_date ASC, transaction_type ASC, item_nr ASC, id ASC",
            "</script>"
    })
    List<OrderFinanceTransactionLine> selectSkuOrderTransactionLines(@Param("query") OrderFinanceQuery query);

    @Select({
            "<script>",
            "SELECT",
            "  (SELECT source_batch_id FROM noon_finance_transaction_fact f2",
            "    WHERE f2.owner_user_id = #{query.ownerUserId}",
            "      AND f2.store_code = #{query.storeCode}",
            "      AND f2.site_code = #{query.siteCode}",
            "      AND f2.transaction_date &gt;= #{query.dateFrom}",
            "      AND f2.transaction_date &lt;= #{query.dateTo}",
            "    ORDER BY f2.transaction_date DESC, f2.id DESC LIMIT 1) AS latestSourceBatchId,",
            "  MAX(transaction_date) AS latestTransactionDate,",
            "  COALESCE(SUM(CASE WHEN NULLIF(TRIM(partner_sku), '') IS NULL THEN 1 ELSE 0 END), 0) AS missingPartnerSkuRowCount,",
            "  (SELECT t.status FROM noon_pull_task t",
            "    WHERE t.owner_user_id = #{query.ownerUserId}",
            "      AND t.store_code = #{query.storeCode}",
            "      AND t.site_code = #{query.siteCode}",
            "      AND t.data_domain = 'FINANCE_TRANSACTION'",
            "      AND t.pull_type = 'REPORT'",
            "      AND t.is_deleted = b'0'",
            "    ORDER BY t.id DESC LIMIT 1) AS latestSyncStatus,",
            "  (SELECT t.diagnostic_summary FROM noon_pull_task t",
            "    WHERE t.owner_user_id = #{query.ownerUserId}",
            "      AND t.store_code = #{query.storeCode}",
            "      AND t.site_code = #{query.siteCode}",
            "      AND t.data_domain = 'FINANCE_TRANSACTION'",
            "      AND t.pull_type = 'REPORT'",
            "      AND t.is_deleted = b'0'",
            "    ORDER BY t.id DESC LIMIT 1) AS latestSyncMessage,",
            "  (SELECT t.finished_at FROM noon_pull_task t",
            "    WHERE t.owner_user_id = #{query.ownerUserId}",
            "      AND t.store_code = #{query.storeCode}",
            "      AND t.site_code = #{query.siteCode}",
            "      AND t.data_domain = 'FINANCE_TRANSACTION'",
            "      AND t.pull_type = 'REPORT'",
            "      AND t.is_deleted = b'0'",
            "    ORDER BY t.id DESC LIMIT 1) AS latestSyncFinishedAt",
            "FROM noon_finance_transaction_fact",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "  AND transaction_date &gt;= #{query.dateFrom}",
            "  AND transaction_date &lt;= #{query.dateTo}",
            "</script>"
    })
    OrderFinanceDataStatus selectDataStatus(@Param("query") OrderFinanceQuery query);
}
