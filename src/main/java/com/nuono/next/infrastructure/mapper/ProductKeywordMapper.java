package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productkeyword.ProductKeywordListQuery;
import com.nuono.next.productkeyword.ProductKeywordRecord;
import com.nuono.next.productkeyword.ProductKeywordUsageEventRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

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
}
