package com.nuono.next.infrastructure.mapper;

import com.nuono.next.nooncompleteness.NoonSalesOrderCompletenessAudit;
import com.nuono.next.noonpull.NoonOrderLineFact;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonOrderFactMapper {

    @Insert({
            "INSERT INTO noon_order_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
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

    default Long nextOrderLineFactId() {
        IdSequenceCommand command = new IdSequenceCommand("order_line_fact", 200000L);
        nextId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Noon order fact ID allocation failed.");
        }
        return id;
    }

    @Insert({
            "INSERT INTO noon_order_line_fact (",
            "  id, source_system, source_batch_id, owner_user_id, store_code, site_code,",
            "  id_partner, src_country, country_code, dest_country, bayan_nr, item_nr, order_identity,",
            "  partner_sku, sku, status, offer_price, gmv_lcy, currency_code, brand_code, family,",
            "  fulfillment_model, order_timestamp, shipment_timestamp, delivered_timestamp,",
            "  report_date_from, report_date_to, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, 'noon_order_report', #{fact.sourceBatchId}, #{fact.ownerUserId},",
            "  #{fact.storeCode}, #{fact.siteCode}, #{fact.idPartner}, #{fact.sourceCountry},",
            "  #{fact.countryCode}, #{fact.destinationCountry}, #{fact.bayanNr},",
            "  #{fact.orderLineIdentity}, #{fact.orderIdentity}, #{fact.partnerSku}, #{fact.sku},",
            "  #{fact.status}, #{fact.offerPrice}, #{fact.gmvLcy}, #{fact.currencyCode},",
            "  #{fact.brandCode}, #{fact.family}, #{fact.fulfillmentModel}, #{fact.orderTimestamp},",
            "  #{fact.shipmentTimestamp}, #{fact.deliveredTimestamp}, #{fact.reportDateFrom},",
            "  #{fact.reportDateTo}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  source_batch_id = VALUES(source_batch_id),",
            "  owner_user_id = VALUES(owner_user_id),",
            "  store_code = VALUES(store_code),",
            "  site_code = VALUES(site_code),",
            "  src_country = VALUES(src_country),",
            "  dest_country = VALUES(dest_country),",
            "  bayan_nr = VALUES(bayan_nr),",
            "  order_identity = VALUES(order_identity),",
            "  partner_sku = VALUES(partner_sku),",
            "  sku = VALUES(sku),",
            "  status = VALUES(status),",
            "  offer_price = VALUES(offer_price),",
            "  gmv_lcy = VALUES(gmv_lcy),",
            "  currency_code = VALUES(currency_code),",
            "  brand_code = VALUES(brand_code),",
            "  family = VALUES(family),",
            "  fulfillment_model = VALUES(fulfillment_model),",
            "  order_timestamp = VALUES(order_timestamp),",
            "  shipment_timestamp = VALUES(shipment_timestamp),",
            "  delivered_timestamp = VALUES(delivered_timestamp),",
            "  report_date_from = VALUES(report_date_from),",
            "  report_date_to = VALUES(report_date_to),",
            "  gmt_updated = NOW()"
    })
    int upsertOrderLineFact(@Param("id") Long id, @Param("fact") NoonOrderLineFact fact);

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN noon_order_line_fact order_line",
            "  ON order_line.source_system = 'noon_order_report'",
            " AND order_line.id_partner = #{fact.idPartner}",
            " AND order_line.country_code = #{fact.countryCode}",
            " AND order_line.item_nr = #{fact.orderLineIdentity}",
            " AND order_line.partner_sku IS NOT NULL",
            " AND TRIM(order_line.partner_sku) <> ''",
            "JOIN logical_store_site lss",
            "  ON lss.is_deleted = b'0'",
            " AND CONVERT(UPPER(TRIM(lss.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "     = CONVERT(UPPER(TRIM(order_line.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.owner_user_id = order_line.owner_user_id",
            " AND ls.is_deleted = b'0'",
            "SET pso.logistics_has_history = b'1',",
            "    pso.logistics_first_flow_at = CASE",
            "        WHEN pso.logistics_first_flow_at IS NULL THEN COALESCE(order_line.order_timestamp, CAST(order_line.report_date_from AS DATETIME), order_line.gmt_create, NOW())",
            "        WHEN COALESCE(order_line.order_timestamp, CAST(order_line.report_date_from AS DATETIME), order_line.gmt_create) IS NULL THEN pso.logistics_first_flow_at",
            "        WHEN pso.logistics_first_flow_at > COALESCE(order_line.order_timestamp, CAST(order_line.report_date_from AS DATETIME), order_line.gmt_create) THEN COALESCE(order_line.order_timestamp, CAST(order_line.report_date_from AS DATETIME), order_line.gmt_create)",
            "        ELSE pso.logistics_first_flow_at",
            "    END,",
            "    pso.logistics_last_flow_at = CASE",
            "        WHEN pso.logistics_last_flow_at IS NULL THEN COALESCE(order_line.delivered_timestamp, order_line.shipment_timestamp, order_line.order_timestamp, CAST(order_line.report_date_to AS DATETIME), order_line.gmt_updated, NOW())",
            "        WHEN COALESCE(order_line.delivered_timestamp, order_line.shipment_timestamp, order_line.order_timestamp, CAST(order_line.report_date_to AS DATETIME), order_line.gmt_updated) IS NULL THEN pso.logistics_last_flow_at",
            "        WHEN pso.logistics_last_flow_at < COALESCE(order_line.delivered_timestamp, order_line.shipment_timestamp, order_line.order_timestamp, CAST(order_line.report_date_to AS DATETIME), order_line.gmt_updated) THEN COALESCE(order_line.delivered_timestamp, order_line.shipment_timestamp, order_line.order_timestamp, CAST(order_line.report_date_to AS DATETIME), order_line.gmt_updated)",
            "        ELSE pso.logistics_last_flow_at",
            "    END,",
            "    pso.logistics_history_source = 'NOON_ORDER_LINE_FACT',",
            "    pso.updated_by = order_line.owner_user_id,",
            "    pso.gmt_updated = NOW()",
            "WHERE pso.is_deleted = b'0'",
            "  AND pso.logical_store_id = lss.logical_store_id",
            "  AND pso.partner_sku IS NOT NULL",
            "  AND TRIM(pso.partner_sku) <> ''",
            "  AND (",
            "      CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "          = CONVERT(UPPER(TRIM(order_line.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "      OR (",
            "          CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP '[0-9]-[0-9]+$'",
            "          AND CONVERT(REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '') USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "              = CONVERT(UPPER(TRIM(order_line.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "      )",
            "  )"
    })
    int markProductSiteOfferLogisticsHistoryByOrderLineFact(@Param("fact") NoonOrderLineFact fact);

    @Select({
            "SELECT",
            "  MAX(report_date_to) AS latestOrderDate,",
            "  MIN(report_date_from) AS historyCoveredFrom,",
            "  MAX(report_date_to) AS historyCoveredTo,",
            "  COUNT(1) AS orderLineCount,",
            "  TRUE AS integrated",
            "FROM noon_order_line_fact",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}"
    })
    NoonSalesOrderCompletenessAudit auditSalesOrderCompleteness(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "SELECT",
            "  DATE(order_timestamp) AS bucket_start,",
            "  AVG(offer_price) AS avg_offer_price,",
            "  MIN(offer_price) AS min_offer_price,",
            "  MAX(offer_price) AS max_offer_price,",
            "  COUNT(*) AS order_line_count,",
            "  currency_code",
            "FROM noon_order_line_fact",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND partner_sku = #{partnerSku}",
            "  AND order_timestamp >= #{dateFromStart}",
            "  AND order_timestamp < #{dateToExclusive}",
            "  AND offer_price IS NOT NULL",
            "  AND order_timestamp IS NOT NULL",
            "  AND NULLIF(TRIM(currency_code), '') IS NOT NULL",
            "  AND LOWER(TRIM(COALESCE(status, ''))) NOT LIKE '%cancel%'",
            "  AND LOWER(TRIM(COALESCE(status, ''))) NOT LIKE '%failed%'",
            "  AND LOWER(TRIM(COALESCE(status, ''))) NOT LIKE '%could_not_be_delivered%'",
            "  AND LOWER(TRIM(COALESCE(status, ''))) NOT LIKE '%rejected%'",
            "GROUP BY DATE(order_timestamp), currency_code",
            "ORDER BY DATE(order_timestamp)"
    })
    List<NoonOrderPriceTrendBucketRow> selectPriceTrendBuckets(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("sku") String sku,
            @Param("dateFromStart") LocalDateTime dateFromStart,
            @Param("dateToExclusive") LocalDateTime dateToExclusive
    );

    @Select({
            "SELECT COUNT(*)",
            "FROM noon_order_line_fact",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND partner_sku = #{partnerSku}",
            "  AND (",
            "    (order_timestamp >= #{dateFromStart} AND order_timestamp < #{dateToExclusive})",
            "    OR (",
            "      order_timestamp IS NULL",
            "      AND report_date_from <= #{reportDateTo}",
            "      AND report_date_to >= #{reportDateFrom}",
            "    )",
            "  )"
    })
    int countPriceTrendCandidateRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("sku") String sku,
            @Param("dateFromStart") LocalDateTime dateFromStart,
            @Param("dateToExclusive") LocalDateTime dateToExclusive,
            @Param("reportDateFrom") LocalDate reportDateFrom,
            @Param("reportDateTo") LocalDate reportDateTo
    );
}
