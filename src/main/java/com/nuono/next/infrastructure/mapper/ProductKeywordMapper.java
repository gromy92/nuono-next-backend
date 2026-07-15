package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import com.nuono.next.productkeyword.ProductKeywordListQuery;
import com.nuono.next.productkeyword.ProductKeywordRecord;
import com.nuono.next.productkeyword.ProductKeywordUsageEventRecord;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface ProductKeywordMapper {

    @Insert({
            "INSERT INTO product_keyword_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextKeywordId() {
        return nextId("product_keyword", 300000L);
    }

    default Long nextUsageEventId() {
        return nextId("product_keyword_usage_event", 320000L);
    }

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("关键词 ID 序列分配失败：" + sequenceName);
        }
        return command.getAllocatedId();
    }

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "partner_sku AS partnerSku, keyword, keyword_norm AS keywordNorm, locale, status,",
            "intent_tags_json AS intentTagsJson, source_summary_json AS sourceSummaryJson,",
            "first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt, created_by AS createdBy, updated_by AS updatedBy",
            "FROM product_keyword",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND partner_sku = #{partnerSku}",
            "  AND keyword_norm = #{keywordNorm}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ProductKeywordRecord selectByScopeAndNorm(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("keywordNorm") String keywordNorm
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "partner_sku AS partnerSku, keyword, keyword_norm AS keywordNorm, locale, status,",
            "intent_tags_json AS intentTagsJson, source_summary_json AS sourceSummaryJson,",
            "first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt, created_by AS createdBy, updated_by AS updatedBy",
            "FROM product_keyword",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND id = #{keywordId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ProductKeywordRecord selectById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("keywordId") Long keywordId
    );

    @Update({
            "UPDATE product_keyword",
            "SET status = 'ARCHIVED', is_deleted = b'1', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{keywordId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND partner_sku = #{partnerSku}",
            "  AND is_deleted = b'0'"
    })
    int archiveKeyword(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("keywordId") Long keywordId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_keyword_usage_event",
            "SET is_deleted = b'1', updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE keyword_id = #{keywordId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND partner_sku = #{partnerSku}",
            "  AND is_deleted = b'0'"
    })
    int archiveKeywordEvents(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("keywordId") Long keywordId,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO product_keyword (",
            "id, owner_user_id, store_code, site_code, partner_sku, keyword, keyword_norm, locale, status,",
            "intent_tags_json, source_summary_json, first_seen_at, last_seen_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "LAST_INSERT_ID(#{record.id}), #{record.ownerUserId}, #{record.storeCode}, #{record.siteCode},",
            "#{record.partnerSku}, #{record.keyword}, #{record.keywordNorm}, #{record.locale}, #{record.status},",
            "#{record.intentTagsJson}, #{record.sourceSummaryJson}, #{record.firstSeenAt}, #{record.lastSeenAt},",
            "b'0', #{record.createdBy}, #{record.updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "id = LAST_INSERT_ID(id),",
            "keyword = VALUES(keyword),",
            "keyword_norm = VALUES(keyword_norm),",
            "locale = VALUES(locale),",
            "status = VALUES(status),",
            "intent_tags_json = VALUES(intent_tags_json),",
            "source_summary_json = COALESCE(VALUES(source_summary_json), source_summary_json),",
            "first_seen_at = COALESCE(first_seen_at, VALUES(first_seen_at)),",
            "last_seen_at = GREATEST(COALESCE(last_seen_at, VALUES(last_seen_at)), VALUES(last_seen_at)),",
            "is_deleted = b'0',",
            "updated_by = VALUES(updated_by),",
            "gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "record.id", before = false, resultType = Long.class)
    int upsertKeyword(@Param("record") ProductKeywordRecord record);

    @Insert({
            "INSERT INTO product_keyword_usage_event (",
            "id, keyword_id, owner_user_id, store_code, site_code, partner_sku, keyword, keyword_norm,",
            "source_type, source_ref_type, source_ref_id, source_ref_key, event_natural_key, event_status, occurred_at,",
            "fact_date_from, fact_date_to, payload_json, metrics_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "LAST_INSERT_ID(#{record.id}), #{record.keywordId}, #{record.ownerUserId}, #{record.storeCode}, #{record.siteCode},",
            "#{record.partnerSku}, #{record.keyword}, #{record.keywordNorm}, #{record.sourceType}, #{record.sourceRefType},",
            "#{record.sourceRefId}, #{record.sourceRefKey}, #{record.eventNaturalKey}, #{record.eventStatus}, #{record.occurredAt},",
            "#{record.factDateFrom}, #{record.factDateTo}, #{record.payloadJson}, #{record.metricsJson},",
            "b'0', #{record.createdBy}, #{record.updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "id = LAST_INSERT_ID(id),",
            "keyword_id = VALUES(keyword_id),",
            "keyword = VALUES(keyword),",
            "keyword_norm = VALUES(keyword_norm),",
            "source_ref_type = VALUES(source_ref_type),",
            "source_ref_id = VALUES(source_ref_id),",
            "source_ref_key = VALUES(source_ref_key),",
            "event_status = VALUES(event_status),",
            "occurred_at = VALUES(occurred_at),",
            "fact_date_from = VALUES(fact_date_from),",
            "fact_date_to = VALUES(fact_date_to),",
            "payload_json = VALUES(payload_json),",
            "metrics_json = VALUES(metrics_json),",
            "is_deleted = b'0',",
            "updated_by = VALUES(updated_by),",
            "gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "record.id", before = false, resultType = Long.class)
    int upsertUsageEvent(@Param("record") ProductKeywordUsageEventRecord record);

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "partner_sku AS partnerSku, keyword, keyword_norm AS keywordNorm, locale, status,",
            "intent_tags_json AS intentTagsJson, source_summary_json AS sourceSummaryJson,",
            "first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt, created_by AS createdBy, updated_by AS updatedBy",
            "FROM product_keyword",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND is_deleted = b'0'",
            "<if test='query.storeCode != null and query.storeCode != \"\"'> AND store_code = #{query.storeCode}</if>",
            "<if test='query.siteCode != null and query.siteCode != \"\"'> AND site_code = #{query.siteCode}</if>",
            "<if test='query.partnerSku != null and query.partnerSku != \"\"'> AND partner_sku = #{query.partnerSku}</if>",
            "<if test='query.keywordNorm != null and query.keywordNorm != \"\"'> AND keyword_norm LIKE CONCAT('%', #{query.keywordNorm}, '%')</if>",
            "<if test='query.status != null and query.status != \"\"'> AND status = #{query.status}</if>",
            "ORDER BY last_seen_at DESC, id DESC",
            "LIMIT #{query.limit}",
            "</script>"
    })
    List<ProductKeywordRecord> listKeywords(@Param("query") ProductKeywordListQuery query);

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "partner_sku AS partnerSku, keyword, keyword_norm AS keywordNorm, locale, status,",
            "intent_tags_json AS intentTagsJson, source_summary_json AS sourceSummaryJson,",
            "first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt, created_by AS createdBy, updated_by AS updatedBy",
            "FROM product_keyword",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND partner_sku = #{partnerSku}",
            "  AND status = 'ACTIVE'",
            "  AND is_deleted = b'0'",
            "<choose>",
            "  <when test='siteCode == null or siteCode == \"\" or siteCode == \"*\"'>",
            "    AND site_code = '*'",
            "  </when>",
            "  <otherwise>",
            "    AND site_code IN (#{siteCode}, '*')",
            "  </otherwise>",
            "</choose>",
            "  AND (intent_tags_json LIKE '%\"CORE\"%' OR intent_tags_json LIKE '%\"TITLE_TARGET\"%')",
            "ORDER BY CASE WHEN site_code = #{siteCode} THEN 0 ELSE 1 END, id ASC",
            "</script>"
    })
    List<ProductKeywordRecord> listActiveTitleTargetKeywords(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT id, keyword_id AS keywordId, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "partner_sku AS partnerSku, keyword, keyword_norm AS keywordNorm, source_type AS sourceType,",
            "source_ref_type AS sourceRefType, source_ref_id AS sourceRefId, source_ref_key AS sourceRefKey,",
            "event_natural_key AS eventNaturalKey, event_status AS eventStatus, occurred_at AS occurredAt,",
            "fact_date_from AS factDateFrom, fact_date_to AS factDateTo, payload_json AS payloadJson, metrics_json AS metricsJson,",
            "created_by AS createdBy, updated_by AS updatedBy",
            "FROM product_keyword_usage_event",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND partner_sku = #{partnerSku}",
            "  AND is_deleted = b'0'",
            "ORDER BY occurred_at DESC, id DESC",
            "LIMIT #{limit}"
    })
    List<ProductKeywordUsageEventRecord> listEvents(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT COUNT(1) > 0",
            "FROM INFORMATION_SCHEMA.TABLES",
            "WHERE TABLE_SCHEMA = DATABASE()",
            "  AND TABLE_NAME = 'noon_ad_query_fact'"
    })
    boolean adsQueryFactTableExists();

    @Select({
            "<script>",
            "SELECT",
            "  q.id,",
            "  q.batch_id AS batchId,",
            "  q.source_system AS sourceSystem,",
            "  q.owner_user_id AS ownerUserId,",
            "  q.project_code AS projectCode,",
            "  q.store_code AS storeCode,",
            "  q.site_code AS siteCode,",
            "  q.report_date_from AS reportDateFrom,",
            "  q.report_date_to AS reportDateTo,",
            "  q.campaign_code AS campaignCode,",
            "  q.campaign_name AS campaignName,",
            "  q.ad_sku_code AS adSkuCode,",
            "  COALESCE(NULLIF(q.partner_sku, ''), ow.partner_sku, pp.partner_sku) AS partnerSku,",
            "  q.query_text AS queryText,",
            "  q.query_hash AS queryHash,",
            "  q.query_kind AS queryKind,",
            "  q.views,",
            "  q.clicks,",
            "  q.orders_count AS ordersCount,",
            "  q.assisted_orders AS assistedOrders,",
            "  q.atc_count AS atcCount,",
            "  COALESCE(q.spend_amount, 0) AS spendAmount,",
            "  COALESCE(q.ad_revenue, 0) AS adRevenue,",
            "  COALESCE(q.ctr_percentage, 0) AS ctrPercentage,",
            "  COALESCE(q.roas, 0) AS roas,",
            "  COALESCE(q.cpc, 0) AS cpc,",
            "  COALESCE(q.cps, 0) AS cps,",
            "  COALESCE(q.cvr_percentage, 0) AS cvrPercentage,",
            "  q.raw_payload_json AS rawPayloadJson",
            "FROM noon_ad_query_fact q",
            "LEFT JOIN (",
            "  SELECT store_code, site_code, noon_sku, MIN(partner_sku) AS partner_sku",
            "  FROM official_warehouse_inventory_snapshot_line",
            "  WHERE COALESCE(partner_sku, '') != ''",
            "    AND COALESCE(noon_sku, '') != ''",
            "  GROUP BY store_code, site_code, noon_sku",
            ") ow ON ow.store_code = q.store_code",
            "  AND ow.site_code = q.site_code",
            "  AND UPPER(TRIM(ow.noon_sku)) = UPPER(TRIM(q.ad_sku_code))",
            "LEFT JOIN (",
            "  SELECT store_code, site_code, noon_product_code, MIN(partner_sku) AS partner_sku",
            "  FROM product_public_detail_snapshot pp",
            "  WHERE pp.is_latest = b'1'",
            "    AND pp.is_deleted = b'0'",
            "    AND COALESCE(partner_sku, '') != ''",
            "    AND COALESCE(noon_product_code, '') != ''",
            "  GROUP BY store_code, site_code, noon_product_code",
            ") pp ON pp.store_code = q.store_code",
            "  AND pp.site_code = q.site_code",
            "  AND UPPER(TRIM(pp.noon_product_code)) = UPPER(TRIM(SUBSTRING_INDEX(q.ad_sku_code, '-', 1)))",
            "WHERE q.owner_user_id = #{ownerUserId}",
            "  AND q.store_code = #{storeCode}",
            "  AND q.site_code = #{siteCode}",
            "  AND q.report_date_from &gt;= #{dateFrom}",
            "  AND q.report_date_to &lt;= #{dateTo}",
            "  AND COALESCE(q.query_text, '') != ''",
            "ORDER BY q.report_date_to DESC, q.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<NoonAdvertisingQueryFact> listAdsQueryFactsForKeywordIndexing(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("limit") Integer limit
    );
}
