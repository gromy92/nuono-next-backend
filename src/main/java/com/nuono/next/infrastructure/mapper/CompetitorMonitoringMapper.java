package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorMonitoringBoundaryRow;
import com.nuono.next.competitoranalysis.CompetitorWatchProductRow;
import com.nuono.next.competitoranalysis.CompetitorWatchProductScopeRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CompetitorMonitoringMapper {

    @Select({
            "SELECT COUNT(DISTINCT wp.owner_user_id, wp.store_code, wp.site_code) AS eligibleTotal,",
            "       MAX(wp.id) AS upperWatchProductId",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.status = 'ACTIVE' AND wp.is_deleted = b'0'",
            "  AND EXISTS (",
            "    SELECT 1 FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    JOIN operations_competitor_keyword_product kp",
            "      ON kp.keyword_id = kw.id",
            "     AND kp.relation_status = 'CONFIRMED' AND kp.is_deleted = b'0'",
            "    JOIN operations_competitor_product cp",
            "      ON cp.id = kp.competitor_product_id",
            "     AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )"
    })
    CompetitorMonitoringBoundaryRow selectRefreshableScopeBoundary();

    @Select({
            "SELECT MIN(wp.id) AS id, wp.owner_user_id AS ownerUserId,",
            "       wp.store_code AS storeCode, wp.site_code AS siteCode",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.id <= #{upperWatchProductId}",
            "  AND wp.status = 'ACTIVE' AND wp.is_deleted = b'0'",
            "  AND EXISTS (",
            "    SELECT 1 FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    JOIN operations_competitor_keyword_product kp",
            "      ON kp.keyword_id = kw.id",
            "     AND kp.relation_status = 'CONFIRMED' AND kp.is_deleted = b'0'",
            "    JOIN operations_competitor_product cp",
            "      ON cp.id = kp.competitor_product_id",
            "     AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )",
            "GROUP BY wp.owner_user_id, wp.store_code, wp.site_code",
            "ORDER BY wp.owner_user_id DESC, wp.store_code DESC, wp.site_code DESC",
            "LIMIT 1"
    })
    CompetitorWatchProductScopeRow selectRefreshableScopeUpperBound(
            @Param("upperWatchProductId") Long upperWatchProductId
    );

    @Select({
            "SELECT MIN(wp.id) AS id, wp.owner_user_id AS ownerUserId,",
            "       wp.store_code AS storeCode, wp.site_code AS siteCode",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.id <= #{upperWatchProductId}",
            "  AND wp.status = 'ACTIVE' AND wp.is_deleted = b'0'",
            "  AND (#{afterOwnerUserId} IS NULL",
            "    OR wp.owner_user_id > #{afterOwnerUserId}",
            "    OR (wp.owner_user_id = #{afterOwnerUserId} AND wp.store_code > #{afterStoreCode})",
            "    OR (wp.owner_user_id = #{afterOwnerUserId} AND wp.store_code = #{afterStoreCode}",
            "        AND wp.site_code > #{afterSiteCode}))",
            "  AND (wp.owner_user_id < #{upperOwnerUserId}",
            "    OR (wp.owner_user_id = #{upperOwnerUserId} AND wp.store_code < #{upperStoreCode})",
            "    OR (wp.owner_user_id = #{upperOwnerUserId} AND wp.store_code = #{upperStoreCode}",
            "        AND wp.site_code <= #{upperSiteCode}))",
            "  AND EXISTS (",
            "    SELECT 1 FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    JOIN operations_competitor_keyword_product kp",
            "      ON kp.keyword_id = kw.id",
            "     AND kp.relation_status = 'CONFIRMED' AND kp.is_deleted = b'0'",
            "    JOIN operations_competitor_product cp",
            "      ON cp.id = kp.competitor_product_id",
            "     AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )",
            "GROUP BY wp.owner_user_id, wp.store_code, wp.site_code",
            "ORDER BY wp.owner_user_id ASC, wp.store_code ASC, wp.site_code ASC",
            "LIMIT #{limit}"
    })
    List<CompetitorWatchProductScopeRow> listRefreshableWatchProductScopes(
            @Param("upperWatchProductId") Long upperWatchProductId,
            @Param("afterOwnerUserId") Long afterOwnerUserId,
            @Param("afterStoreCode") String afterStoreCode,
            @Param("afterSiteCode") String afterSiteCode,
            @Param("upperOwnerUserId") Long upperOwnerUserId,
            @Param("upperStoreCode") String upperStoreCode,
            @Param("upperSiteCode") String upperSiteCode,
            @Param("limit") int limit
    );

    @Select({
            "SELECT COUNT(1) AS eligibleTotal, MAX(wp.id) AS upperWatchProductId",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND wp.status = 'ACTIVE' AND wp.is_deleted = b'0'",
            "  AND EXISTS (",
            "    SELECT 1 FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    JOIN operations_competitor_keyword_product kp",
            "      ON kp.keyword_id = kw.id",
            "     AND kp.relation_status = 'CONFIRMED' AND kp.is_deleted = b'0'",
            "    JOIN operations_competitor_product cp",
            "      ON cp.id = kp.competitor_product_id",
            "     AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )"
    })
    CompetitorMonitoringBoundaryRow selectRefreshableWatchProductBoundary(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "SELECT wp.id, wp.owner_user_id AS ownerUserId, wp.store_code AS storeCode,",
            "       wp.site_code AS siteCode, wp.logical_store_id AS logicalStoreId,",
            "       wp.product_master_id AS productMasterId, wp.product_variant_id AS productVariantId,",
            "       wp.product_site_offer_id AS productSiteOfferId, wp.sku_parent AS skuParent,",
            "       wp.partner_sku AS partnerSku, wp.child_sku AS childSku,",
            "       wp.self_noon_product_code AS selfNoonProductCode, wp.self_code_type AS selfCodeType,",
            "       wp.title_snapshot AS titleSnapshot, wp.brand_snapshot AS brandSnapshot,",
            "       wp.image_url_snapshot AS imageUrlSnapshot,",
            "       wp.product_fulltype_snapshot AS productFulltypeSnapshot, wp.status,",
            "       wp.latest_run_id AS latestRunId, wp.latest_run_status AS latestRunStatus,",
            "       wp.latest_run_at AS latestRunAt, wp.gmt_updated AS gmtUpdated",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND wp.id > #{afterWatchProductId}",
            "  AND wp.id <= #{upperWatchProductId}",
            "  AND wp.status = 'ACTIVE' AND wp.is_deleted = b'0'",
            "  AND EXISTS (",
            "    SELECT 1 FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    JOIN operations_competitor_keyword_product kp",
            "      ON kp.keyword_id = kw.id",
            "     AND kp.relation_status = 'CONFIRMED' AND kp.is_deleted = b'0'",
            "    JOIN operations_competitor_product cp",
            "      ON cp.id = kp.competitor_product_id",
            "     AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  )",
            "ORDER BY wp.id ASC",
            "LIMIT #{limit}"
    })
    List<CompetitorWatchProductRow> listRefreshableWatchProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("afterWatchProductId") Long afterWatchProductId,
            @Param("upperWatchProductId") Long upperWatchProductId,
            @Param("limit") int limit
    );
}
