package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productkeyword.ProductKeywordUsageEventRecord;
import com.nuono.next.productlisting.ProductListingDraftRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductListingKeywordSuggestionMapper {

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, store_code AS storeCode, draft_no AS draftNo,",
            "source_type AS sourceType, source_ref_id AS sourceRefId, optional_purchase_order_id AS optionalPurchaseOrderId,",
            "status, draft_json AS draftJson, validation_json AS validationJson,",
            "created_by AS createdBy, updated_by AS updatedBy, gmt_create AS gmtCreate, gmt_updated AS gmtUpdated",
            "FROM product_listing_draft",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND JSON_UNQUOTE(JSON_EXTRACT(draft_json, '$.psku')) = #{partnerSku}",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    ProductListingDraftRecord selectLatestDraftByProductScope(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT id, keyword_id AS keywordId, owner_user_id AS ownerUserId, store_code AS storeCode,",
            "site_code AS siteCode, partner_sku AS partnerSku, keyword, keyword_norm AS keywordNorm,",
            "source_type AS sourceType, source_ref_type AS sourceRefType, source_ref_id AS sourceRefId,",
            "source_ref_key AS sourceRefKey, event_natural_key AS eventNaturalKey, event_status AS eventStatus,",
            "occurred_at AS occurredAt, payload_json AS payloadJson, metrics_json AS metricsJson,",
            "created_by AS createdBy, updated_by AS updatedBy",
            "FROM product_keyword_usage_event",
            "WHERE source_type = 'LISTING_DRAFT'",
            "  AND source_ref_type = 'product_listing_draft'",
            "  AND source_ref_id = #{draftId}",
            "  AND is_deleted = b'0'",
            "ORDER BY occurred_at DESC, id DESC"
    })
    List<ProductKeywordUsageEventRecord> listDraftSuggestionEvents(@Param("draftId") Long draftId);
}
