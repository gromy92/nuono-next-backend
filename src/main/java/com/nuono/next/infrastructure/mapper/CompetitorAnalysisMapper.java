package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorKeywordProductRow;
import com.nuono.next.competitoranalysis.CompetitorKeywordProductSearchCommand;
import com.nuono.next.competitoranalysis.CompetitorKeywordInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorKeywordRow;
import com.nuono.next.competitoranalysis.CompetitorKeywordRunInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorKeywordScopeRow;
import com.nuono.next.competitoranalysis.CompetitorKeywordUpdateCommand;
import com.nuono.next.competitoranalysis.CompetitorLatestRankPointRow;
import com.nuono.next.competitoranalysis.CompetitorProductInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorProductOptionRow;
import com.nuono.next.competitoranalysis.CompetitorProductRow;
import com.nuono.next.competitoranalysis.CompetitorProductScopeRow;
import com.nuono.next.competitoranalysis.CompetitorRankFactInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorSearchRunInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorSearchRunRow;
import com.nuono.next.competitoranalysis.CompetitorSearchResultInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorWatchProductInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorWatchProductListRow;
import com.nuono.next.competitoranalysis.CompetitorWatchProductQuery;
import com.nuono.next.competitoranalysis.CompetitorWatchProductRow;
import com.nuono.next.competitoranalysis.CompetitorWatchProductScopeRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface CompetitorAnalysisMapper {

    @Insert({
            "INSERT INTO operations_competitor_analysis_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
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
    int allocateCompetitorAnalysisId(IdSequenceCommand command);

    default Long nextCompetitorAnalysisId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateCompetitorAnalysisId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("竞品分析 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    default Long nextWatchProductId() {
        return nextCompetitorAnalysisId("operations_competitor_watch_product", 180000L);
    }

    default Long nextKeywordId() {
        return nextCompetitorAnalysisId("operations_competitor_keyword", 190000L);
    }

    default Long nextCompetitorProductId() {
        return nextCompetitorAnalysisId("operations_competitor_product", 200000L);
    }

    default Long nextKeywordProductId() {
        return nextCompetitorAnalysisId("operations_competitor_keyword_product", 210000L);
    }

    default Long nextSearchRunId() {
        return nextCompetitorAnalysisId("operations_competitor_search_run", 220000L);
    }

    default Long nextKeywordRunId() {
        return nextCompetitorAnalysisId("operations_competitor_keyword_run", 230000L);
    }

    default Long nextSearchResultId() {
        return nextCompetitorAnalysisId("operations_competitor_search_result", 240000L);
    }

    default Long nextRankFactId() {
        return nextCompetitorAnalysisId("operations_competitor_rank_fact", 250000L);
    }

    @Select({
            "<script>",
            "SELECT COUNT(DISTINCT wp.id)",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.is_deleted = b'0'",
            "  <if test='storeCodes != null and storeCodes.size() > 0'>",
            "    AND wp.store_code IN",
            "    <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>#{storeCode}</foreach>",
            "  </if>",
            "  <if test='query.storeCode != null and query.storeCode != \"\"'>",
            "    AND wp.store_code = #{query.storeCode}",
            "  </if>",
            "  <if test='query.siteCode != null and query.siteCode != \"\"'>",
            "    AND UPPER(wp.site_code) = UPPER(#{query.siteCode})",
            "  </if>",
            "  <if test='query.status != null and query.status != \"\"'>",
            "    AND wp.status = #{query.status}",
            "  </if>",
            "  <if test='query.productSearch != null and query.productSearch != \"\"'>",
            "    AND (",
            "      LOWER(COALESCE(wp.partner_sku, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "      OR LOWER(COALESCE(wp.child_sku, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "      OR LOWER(COALESCE(wp.sku_parent, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "      OR LOWER(COALESCE(wp.self_noon_product_code, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "      OR LOWER(COALESCE(wp.title_snapshot, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "    )",
            "  </if>",
            "  <if test='query.keywordSearch != null and query.keywordSearch != \"\"'>",
            "    AND EXISTS (",
            "      SELECT 1 FROM operations_competitor_keyword kw",
            "      WHERE kw.watch_product_id = wp.id",
            "        AND kw.is_deleted = b'0'",
            "        AND LOWER(kw.keyword) LIKE CONCAT('%', LOWER(#{query.keywordSearch}), '%')",
            "    )",
            "  </if>",
            "  <if test='query.competitorSearch != null and query.competitorSearch != \"\"'>",
            "    AND EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp",
            "      WHERE cp.watch_product_id = wp.id",
            "        AND cp.is_deleted = b'0'",
            "        AND (",
            "          LOWER(cp.noon_product_code) LIKE CONCAT('%', LOWER(#{query.competitorSearch}), '%')",
            "          OR LOWER(COALESCE(cp.title_snapshot, '')) LIKE CONCAT('%', LOWER(#{query.competitorSearch}), '%')",
            "          OR LOWER(COALESCE(cp.brand_snapshot, '')) LIKE CONCAT('%', LOWER(#{query.competitorSearch}), '%')",
            "        )",
            "    )",
            "  </if>",
            "</script>"
    })
    long countWatchProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") List<String> storeCodes,
            @Param("query") CompetitorWatchProductQuery query
    );

    @Select({
            "<script>",
            "SELECT",
            "  wp.id, wp.owner_user_id AS ownerUserId, wp.store_code AS storeCode, wp.site_code AS siteCode,",
            "  wp.logical_store_id AS logicalStoreId, wp.product_master_id AS productMasterId,",
            "  wp.product_variant_id AS productVariantId, wp.product_site_offer_id AS productSiteOfferId,",
            "  wp.sku_parent AS skuParent, wp.partner_sku AS partnerSku, wp.child_sku AS childSku,",
            "  wp.self_noon_product_code AS selfNoonProductCode, wp.self_code_type AS selfCodeType,",
            "  wp.title_snapshot AS titleSnapshot, wp.brand_snapshot AS brandSnapshot,",
            "  wp.image_url_snapshot AS imageUrlSnapshot, wp.product_fulltype_snapshot AS productFulltypeSnapshot,",
            "  wp.status, wp.latest_run_id AS latestRunId, wp.latest_run_status AS latestRunStatus,",
            "  wp.latest_run_at AS latestRunAt, wp.gmt_updated AS gmtUpdated,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  ) AS activeKeywordCount,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_product cp",
            "    WHERE cp.watch_product_id = wp.id AND cp.review_status = 'PENDING' AND cp.is_deleted = b'0'",
            "  ) AS pendingCandidateCount,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_product cp",
            "    WHERE cp.watch_product_id = wp.id AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "  ) AS confirmedCompetitorCount",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.is_deleted = b'0'",
            "  <if test='storeCodes != null and storeCodes.size() > 0'>",
            "    AND wp.store_code IN",
            "    <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>#{storeCode}</foreach>",
            "  </if>",
            "  <if test='query.storeCode != null and query.storeCode != \"\"'>",
            "    AND wp.store_code = #{query.storeCode}",
            "  </if>",
            "  <if test='query.siteCode != null and query.siteCode != \"\"'>",
            "    AND UPPER(wp.site_code) = UPPER(#{query.siteCode})",
            "  </if>",
            "  <if test='query.status != null and query.status != \"\"'>",
            "    AND wp.status = #{query.status}",
            "  </if>",
            "  <if test='query.productSearch != null and query.productSearch != \"\"'>",
            "    AND (",
            "      LOWER(COALESCE(wp.partner_sku, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "      OR LOWER(COALESCE(wp.child_sku, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "      OR LOWER(COALESCE(wp.sku_parent, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "      OR LOWER(COALESCE(wp.self_noon_product_code, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "      OR LOWER(COALESCE(wp.title_snapshot, '')) LIKE CONCAT('%', LOWER(#{query.productSearch}), '%')",
            "    )",
            "  </if>",
            "  <if test='query.keywordSearch != null and query.keywordSearch != \"\"'>",
            "    AND EXISTS (",
            "      SELECT 1 FROM operations_competitor_keyword kw",
            "      WHERE kw.watch_product_id = wp.id",
            "        AND kw.is_deleted = b'0'",
            "        AND LOWER(kw.keyword) LIKE CONCAT('%', LOWER(#{query.keywordSearch}), '%')",
            "    )",
            "  </if>",
            "  <if test='query.competitorSearch != null and query.competitorSearch != \"\"'>",
            "    AND EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp",
            "      WHERE cp.watch_product_id = wp.id",
            "        AND cp.is_deleted = b'0'",
            "        AND (",
            "          LOWER(cp.noon_product_code) LIKE CONCAT('%', LOWER(#{query.competitorSearch}), '%')",
            "          OR LOWER(COALESCE(cp.title_snapshot, '')) LIKE CONCAT('%', LOWER(#{query.competitorSearch}), '%')",
            "          OR LOWER(COALESCE(cp.brand_snapshot, '')) LIKE CONCAT('%', LOWER(#{query.competitorSearch}), '%')",
            "        )",
            "    )",
            "  </if>",
            "ORDER BY wp.gmt_updated DESC, wp.id DESC",
            "LIMIT #{query.pageSize} OFFSET #{query.offset}",
            "</script>"
    })
    List<CompetitorWatchProductListRow> listWatchProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") List<String> storeCodes,
            @Param("query") CompetitorWatchProductQuery query
    );

    @Select({
            "<script>",
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  lss.store_code AS storeCode,",
            "  lss.site AS siteCode,",
            "  pm.id AS productMasterId,",
            "  pv.id AS productVariantId,",
            "  pso.id AS productSiteOfferId,",
            "  pm.sku_parent AS skuParent,",
            "  pv.partner_sku AS partnerSku,",
            "  pv.child_sku AS childSku,",
            "  pso.psku_code AS pskuCode,",
            "  pm.title_cache AS title,",
            "  pm.brand_cache AS brand,",
            "  pm.cover_image_url AS imageUrl,",
            "  pm.product_fulltype_cache AS productFulltype",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND UPPER(lss.site) = UPPER(#{siteCode})",
            " AND lss.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.site_id = lss.id",
            " AND pso.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  AND NULLIF(TRIM(pso.psku_code), '') IS NOT NULL",
            "  AND (UPPER(pso.psku_code) LIKE 'Z%' OR UPPER(pso.psku_code) LIKE 'N%')",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(pm.sku_parent) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pv.partner_sku, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pv.child_sku, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pso.psku_code, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pm.title_cache, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "ORDER BY pm.sku_parent ASC, pv.partner_sku ASC, pso.id ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<CompetitorProductOptionRow> listProductOptions(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, keyword, keyword_norm AS keywordNorm, locale,",
            "  status, display_order AS displayOrder, last_provider_status AS lastProviderStatus,",
            "  last_succeeded_at AS lastSucceededAt, last_error_code AS lastErrorCode,",
            "  last_error_message AS lastErrorMessage",
            "FROM operations_competitor_keyword",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND keyword_norm = #{keywordNorm}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorKeywordRow selectKeywordByNorm(
            @Param("watchProductId") Long watchProductId,
            @Param("keywordNorm") String keywordNorm
    );

    @Insert({
            "INSERT INTO operations_competitor_keyword (",
            "  id, watch_product_id, keyword, keyword_norm, locale, status, display_order,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{watchProductId}, #{keyword}, #{keywordNorm}, #{locale}, #{status}, #{displayOrder},",
            "  b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertKeyword(CompetitorKeywordInsertCommand command);

    @Update({
            "<script>",
            "UPDATE operations_competitor_keyword",
            "SET updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "  <if test='keyword != null and keyword != \"\"'>, keyword = #{keyword}</if>",
            "  <if test='keywordNorm != null and keywordNorm != \"\"'>, keyword_norm = #{keywordNorm}</if>",
            "  <if test='locale != null'>, locale = #{locale}</if>",
            "  <if test='status != null and status != \"\"'>, status = #{status}</if>",
            "  <if test='displayOrder != null'>, display_order = #{displayOrder}</if>",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'",
            "</script>"
    })
    int updateKeyword(CompetitorKeywordUpdateCommand command);

    @Update({
            "UPDATE operations_competitor_keyword",
            "SET is_deleted = b'1',",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{keywordId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteKeyword(
            @Param("keywordId") Long keywordId,
            @Param("actorUserId") Long actorUserId
    );

    @Select({
            "SELECT",
            "  kw.id AS keywordId, kw.watch_product_id AS watchProductId,",
            "  wp.owner_user_id AS ownerUserId, wp.store_code AS storeCode, wp.site_code AS siteCode,",
            "  kw.status AS status",
            "FROM operations_competitor_keyword kw",
            "JOIN operations_competitor_watch_product wp",
            "  ON wp.id = kw.watch_product_id",
            " AND wp.is_deleted = b'0'",
            "WHERE kw.id = #{keywordId}",
            "  AND kw.is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorKeywordScopeRow selectKeywordScopeById(@Param("keywordId") Long keywordId);

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, keyword, keyword_norm AS keywordNorm, locale,",
            "  status, display_order AS displayOrder, last_provider_status AS lastProviderStatus,",
            "  last_succeeded_at AS lastSucceededAt, last_error_code AS lastErrorCode,",
            "  last_error_message AS lastErrorMessage",
            "FROM operations_competitor_keyword",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND status = 'ACTIVE'",
            "  AND is_deleted = b'0'",
            "ORDER BY display_order ASC, id ASC"
    })
    List<CompetitorKeywordRow> listActiveKeywordsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, noon_product_code AS noonProductCode, code_type AS codeType,",
            "  canonical_url AS canonicalUrl, title_snapshot AS titleSnapshot, brand_snapshot AS brandSnapshot,",
            "  image_url_snapshot AS imageUrlSnapshot, price_amount_snapshot AS priceAmountSnapshot,",
            "  currency_code_snapshot AS currencyCodeSnapshot, rating_snapshot AS ratingSnapshot,",
            "  review_count_snapshot AS reviewCountSnapshot, source_type AS sourceType, review_status AS reviewStatus,",
            "  confirmed_by AS confirmedBy, confirmed_at AS confirmedAt, first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt",
            "FROM operations_competitor_product",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND noon_product_code = #{noonProductCode}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorProductRow selectCompetitorProductByCode(
            @Param("watchProductId") Long watchProductId,
            @Param("noonProductCode") String noonProductCode
    );

    @Insert({
            "INSERT INTO operations_competitor_product (",
            "  id, watch_product_id, noon_product_code, code_type, canonical_url, title_snapshot,",
            "  brand_snapshot, image_url_snapshot, price_amount_snapshot, currency_code_snapshot,",
            "  rating_snapshot, review_count_snapshot, source_type, review_status, confirmed_by, confirmed_at,",
            "  first_seen_at, last_seen_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{watchProductId}, #{noonProductCode}, #{codeType}, #{canonicalUrl}, #{titleSnapshot},",
            "  #{brandSnapshot}, #{imageUrlSnapshot}, #{priceAmountSnapshot}, #{currencyCodeSnapshot},",
            "  #{ratingSnapshot}, #{reviewCountSnapshot}, #{sourceType}, #{reviewStatus},",
            "  CASE WHEN #{reviewStatus} = 'CONFIRMED' THEN #{actorUserId} ELSE NULL END,",
            "  CASE WHEN #{reviewStatus} = 'CONFIRMED' THEN NOW() ELSE NULL END,",
            "  NOW(), NOW(), b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertCompetitorProduct(CompetitorProductInsertCommand command);

    @Update({
            "UPDATE operations_competitor_product",
            "SET canonical_url = COALESCE(#{canonicalUrl}, canonical_url),",
            "    title_snapshot = COALESCE(#{titleSnapshot}, title_snapshot),",
            "    brand_snapshot = COALESCE(#{brandSnapshot}, brand_snapshot),",
            "    image_url_snapshot = COALESCE(#{imageUrlSnapshot}, image_url_snapshot),",
            "    price_amount_snapshot = COALESCE(#{priceAmountSnapshot}, price_amount_snapshot),",
            "    currency_code_snapshot = COALESCE(#{currencyCodeSnapshot}, currency_code_snapshot),",
            "    rating_snapshot = COALESCE(#{ratingSnapshot}, rating_snapshot),",
            "    review_count_snapshot = COALESCE(#{reviewCountSnapshot}, review_count_snapshot),",
            "    last_seen_at = NOW(),",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    int updateCompetitorProductFromSearch(CompetitorProductInsertCommand command);

    @Update({
            "UPDATE operations_competitor_product",
            "SET review_status = 'CONFIRMED',",
            "    confirmed_by = #{actorUserId},",
            "    confirmed_at = NOW(),",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{competitorProductId}",
            "  AND is_deleted = b'0'"
    })
    int markCompetitorProductConfirmed(
            @Param("competitorProductId") Long competitorProductId,
            @Param("actorUserId") Long actorUserId
    );

    @Select({
            "SELECT",
            "  cp.id AS competitorProductId, cp.watch_product_id AS watchProductId,",
            "  wp.owner_user_id AS ownerUserId, wp.store_code AS storeCode, wp.site_code AS siteCode,",
            "  cp.review_status AS reviewStatus, cp.noon_product_code AS noonProductCode",
            "FROM operations_competitor_product cp",
            "JOIN operations_competitor_watch_product wp",
            "  ON wp.id = cp.watch_product_id",
            " AND wp.is_deleted = b'0'",
            "WHERE cp.id = #{competitorProductId}",
            "  AND cp.is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorProductScopeRow selectCompetitorProductScopeById(@Param("competitorProductId") Long competitorProductId);

    default int upsertKeywordProductRelation(
            Long keywordId,
            Long competitorProductId,
            String relationStatus,
            Long actorUserId
    ) {
        Long id = nextKeywordProductId();
        return upsertKeywordProductRelationWithId(id, keywordId, competitorProductId, relationStatus, actorUserId);
    }

    @Insert({
            "INSERT INTO operations_competitor_keyword_product (",
            "  id, keyword_id, competitor_product_id, relation_status, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{keywordId}, #{competitorProductId}, #{relationStatus}, b'0',",
            "  #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  relation_status = VALUES(relation_status),",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertKeywordProductRelationWithId(
            @Param("id") Long id,
            @Param("keywordId") Long keywordId,
            @Param("competitorProductId") Long competitorProductId,
            @Param("relationStatus") String relationStatus,
            @Param("actorUserId") Long actorUserId
    );

    @Update({
            "UPDATE operations_competitor_keyword_product",
            "SET relation_status = 'IGNORED',",
            "    ignored_by = #{actorUserId},",
            "    ignored_at = NOW(),",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE keyword_id = #{keywordId}",
            "  AND competitor_product_id = #{competitorProductId}",
            "  AND is_deleted = b'0'"
    })
    int markKeywordProductRelationIgnored(
            @Param("keywordId") Long keywordId,
            @Param("competitorProductId") Long competitorProductId,
            @Param("actorUserId") Long actorUserId
    );

    @Select({
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  lss.store_code AS storeCode,",
            "  lss.site AS siteCode,",
            "  pm.id AS productMasterId,",
            "  pv.id AS productVariantId,",
            "  pso.id AS productSiteOfferId,",
            "  pm.sku_parent AS skuParent,",
            "  pv.partner_sku AS partnerSku,",
            "  pv.child_sku AS childSku,",
            "  pso.psku_code AS pskuCode,",
            "  pm.title_cache AS title,",
            "  pm.brand_cache AS brand,",
            "  pm.cover_image_url AS imageUrl,",
            "  pm.product_fulltype_cache AS productFulltype",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.store_code = #{storeCode}",
            " AND UPPER(lss.site) = UPPER(#{siteCode})",
            " AND lss.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.site_id = lss.id",
            " AND pso.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  AND pso.id = #{productSiteOfferId}",
            "LIMIT 1"
    })
    CompetitorProductOptionRow selectProductOptionByOfferId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("productSiteOfferId") Long productSiteOfferId
    );

    @Insert({
            "INSERT INTO operations_competitor_watch_product (",
            "  id, owner_user_id, store_code, site_code, logical_store_id, product_master_id,",
            "  product_variant_id, product_site_offer_id, sku_parent, partner_sku, child_sku,",
            "  self_noon_product_code, self_code_type, title_snapshot, brand_snapshot,",
            "  image_url_snapshot, product_fulltype_snapshot, status, is_deleted, created_by, updated_by,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{logicalStoreId}, #{productMasterId},",
            "  #{productVariantId}, #{productSiteOfferId}, #{skuParent}, #{partnerSku}, #{childSku},",
            "  #{selfNoonProductCode}, #{selfCodeType}, #{titleSnapshot}, #{brandSnapshot},",
            "  #{imageUrlSnapshot}, #{productFulltypeSnapshot}, #{status}, b'0', #{actorUserId}, #{actorUserId},",
            "  NOW(), NOW()",
            ")"
    })
    int insertWatchProduct(CompetitorWatchProductInsertCommand command);

    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "  product_variant_id AS productVariantId, product_site_offer_id AS productSiteOfferId,",
            "  sku_parent AS skuParent, partner_sku AS partnerSku, child_sku AS childSku,",
            "  self_noon_product_code AS selfNoonProductCode, self_code_type AS selfCodeType,",
            "  title_snapshot AS titleSnapshot, brand_snapshot AS brandSnapshot,",
            "  image_url_snapshot AS imageUrlSnapshot, product_fulltype_snapshot AS productFulltypeSnapshot,",
            "  status, latest_run_id AS latestRunId, latest_run_status AS latestRunStatus,",
            "  latest_run_at AS latestRunAt, gmt_updated AS gmtUpdated",
            "FROM operations_competitor_watch_product",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND id = #{watchProductId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorWatchProductRow selectWatchProductById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("watchProductId") Long watchProductId
    );

    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode",
            "FROM operations_competitor_watch_product",
            "WHERE id = #{watchProductId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorWatchProductScopeRow selectWatchProductScopeById(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, keyword, keyword_norm AS keywordNorm, locale,",
            "  status, display_order AS displayOrder, last_provider_status AS lastProviderStatus,",
            "  last_succeeded_at AS lastSucceededAt, last_error_code AS lastErrorCode,",
            "  last_error_message AS lastErrorMessage",
            "FROM operations_competitor_keyword",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND is_deleted = b'0'",
            "ORDER BY display_order ASC, id ASC"
    })
    List<CompetitorKeywordRow> listKeywordsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, noon_product_code AS noonProductCode, code_type AS codeType,",
            "  canonical_url AS canonicalUrl, title_snapshot AS titleSnapshot, brand_snapshot AS brandSnapshot,",
            "  image_url_snapshot AS imageUrlSnapshot, price_amount_snapshot AS priceAmountSnapshot,",
            "  currency_code_snapshot AS currencyCodeSnapshot, rating_snapshot AS ratingSnapshot,",
            "  review_count_snapshot AS reviewCountSnapshot, source_type AS sourceType, review_status AS reviewStatus,",
            "  confirmed_by AS confirmedBy, confirmed_at AS confirmedAt, first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt",
            "FROM operations_competitor_product",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND is_deleted = b'0'",
            "ORDER BY FIELD(review_status, 'PENDING', 'CONFIRMED', 'IGNORED'), id ASC"
    })
    List<CompetitorProductRow> listProductsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  id, keyword_id AS keywordId, competitor_product_id AS competitorProductId,",
            "  relation_status AS relationStatus, first_seen_rank_no AS firstSeenRankNo,",
            "  last_seen_rank_no AS lastSeenRankNo, last_seen_sponsored AS lastSeenSponsored, last_seen_at AS lastSeenAt",
            "FROM operations_competitor_keyword_product",
            "WHERE is_deleted = b'0'",
            "  AND keyword_id IN (",
            "    SELECT id FROM operations_competitor_keyword WHERE watch_product_id = #{watchProductId} AND is_deleted = b'0'",
            "  )",
            "ORDER BY keyword_id ASC, id ASC"
    })
    List<CompetitorKeywordProductRow> listKeywordProductRelationsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  rf.keyword_id AS keywordId, kw.keyword AS keyword, rf.tracked_product_type AS trackedProductType,",
            "  rf.noon_product_code AS noonProductCode, rf.rank_status AS rankStatus, rf.rank_no AS rankNo,",
            "  rf.is_sponsored AS sponsored, rf.price_amount AS priceAmount, rf.currency_code AS currencyCode,",
            "  rf.fact_time AS factTime",
            "FROM operations_competitor_rank_fact rf",
            "JOIN operations_competitor_keyword kw",
            "  ON kw.id = rf.keyword_id",
            "WHERE rf.watch_product_id = #{watchProductId}",
            "  AND rf.is_deleted = b'0'",
            "  AND kw.is_deleted = b'0'",
            "  AND rf.fact_time = (",
            "    SELECT MAX(latest.fact_time)",
            "    FROM operations_competitor_rank_fact latest",
            "    WHERE latest.watch_product_id = rf.watch_product_id",
            "      AND latest.keyword_id = rf.keyword_id",
            "      AND latest.noon_product_code = rf.noon_product_code",
            "      AND latest.tracked_product_type = rf.tracked_product_type",
            "      AND latest.is_deleted = b'0'",
            "  )",
            "ORDER BY kw.display_order ASC, rf.tracked_product_type ASC, rf.rank_no ASC"
    })
    List<CompetitorLatestRankPointRow> listLatestRankPointsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Insert({
            "INSERT INTO operations_competitor_search_run (",
            "  id, watch_product_id, task_id, trigger_mode, status, requested_by, started_at,",
            "  keyword_total, keyword_success, keyword_failed, candidate_upserted_count,",
            "  rank_fact_written_count, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{watchProductId}, #{taskId}, #{triggerMode}, #{status}, #{requestedBy}, NOW(),",
            "  #{keywordTotal}, 0, 0, 0, 0, b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertSearchRun(CompetitorSearchRunInsertCommand command);

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, task_id AS taskId, trigger_mode AS triggerMode,",
            "  status, requested_by AS requestedBy, started_at AS startedAt, finished_at AS finishedAt,",
            "  keyword_total AS keywordTotal, keyword_success AS keywordSuccess, keyword_failed AS keywordFailed,",
            "  candidate_upserted_count AS candidateUpsertedCount,",
            "  rank_fact_written_count AS rankFactWrittenCount,",
            "  error_code AS errorCode, error_message AS errorMessage",
            "FROM operations_competitor_search_run",
            "WHERE id = #{runId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorSearchRunRow selectSearchRunById(@Param("runId") Long runId);

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, task_id AS taskId, trigger_mode AS triggerMode,",
            "  status, requested_by AS requestedBy, started_at AS startedAt, finished_at AS finishedAt,",
            "  keyword_total AS keywordTotal, keyword_success AS keywordSuccess, keyword_failed AS keywordFailed,",
            "  candidate_upserted_count AS candidateUpsertedCount,",
            "  rank_fact_written_count AS rankFactWrittenCount,",
            "  error_code AS errorCode, error_message AS errorMessage",
            "FROM operations_competitor_search_run",
            "WHERE task_id = #{taskId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    CompetitorSearchRunRow selectSearchRunByTaskId(@Param("taskId") Long taskId);

    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = 'FAILED',",
            "    finished_at = NOW(),",
            "    error_code = #{errorCode},",
            "    error_message = #{errorMessage},",
            "    gmt_updated = NOW()",
            "WHERE id = #{runId}",
            "  AND is_deleted = b'0'"
    })
    int markSearchRunFailed(
            @Param("runId") Long runId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage
    );

    @Update({
            "UPDATE operations_competitor_search_run",
            "SET status = #{status},",
            "    finished_at = NOW(),",
            "    keyword_success = #{keywordSuccess},",
            "    keyword_failed = #{keywordFailed},",
            "    candidate_upserted_count = #{candidateUpsertedCount},",
            "    rank_fact_written_count = #{rankFactWrittenCount},",
            "    error_code = #{errorCode},",
            "    error_message = #{errorMessage},",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{runId}",
            "  AND is_deleted = b'0'"
    })
    int completeSearchRun(
            @Param("runId") Long runId,
            @Param("status") String status,
            @Param("keywordSuccess") int keywordSuccess,
            @Param("keywordFailed") int keywordFailed,
            @Param("candidateUpsertedCount") int candidateUpsertedCount,
            @Param("rankFactWrittenCount") int rankFactWrittenCount,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("actorUserId") Long actorUserId
    );

    @Insert({
            "INSERT INTO operations_competitor_keyword_run (",
            "  id, search_run_id, keyword_id, keyword_snapshot, locale_snapshot, provider_status,",
            "  result_count, source_url, parser_version, provider_http_status, response_hash,",
            "  captured_at, error_code, error_message, started_at, finished_at, is_deleted, created_by, updated_by,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{searchRunId}, #{keywordId}, #{keywordSnapshot}, #{localeSnapshot}, #{providerStatus},",
            "  #{resultCount}, #{sourceUrl}, #{parserVersion}, #{providerHttpStatus}, #{responseHash},",
            "  #{capturedAt}, #{errorCode}, #{errorMessage}, NOW(), NOW(), b'0', #{actorUserId}, #{actorUserId},",
            "  NOW(), NOW()",
            ")"
    })
    int insertKeywordRun(CompetitorKeywordRunInsertCommand command);

    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "  product_variant_id AS productVariantId, product_site_offer_id AS productSiteOfferId,",
            "  sku_parent AS skuParent, partner_sku AS partnerSku, child_sku AS childSku,",
            "  self_noon_product_code AS selfNoonProductCode, self_code_type AS selfCodeType,",
            "  title_snapshot AS titleSnapshot, brand_snapshot AS brandSnapshot,",
            "  image_url_snapshot AS imageUrlSnapshot, product_fulltype_snapshot AS productFulltypeSnapshot,",
            "  status, latest_run_id AS latestRunId, latest_run_status AS latestRunStatus,",
            "  latest_run_at AS latestRunAt, gmt_updated AS gmtUpdated",
            "FROM operations_competitor_watch_product",
            "WHERE id = #{watchProductId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorWatchProductRow selectWatchProductForRefresh(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, noon_product_code AS noonProductCode, code_type AS codeType,",
            "  canonical_url AS canonicalUrl, title_snapshot AS titleSnapshot, brand_snapshot AS brandSnapshot,",
            "  image_url_snapshot AS imageUrlSnapshot, price_amount_snapshot AS priceAmountSnapshot,",
            "  currency_code_snapshot AS currencyCodeSnapshot, rating_snapshot AS ratingSnapshot,",
            "  review_count_snapshot AS reviewCountSnapshot, source_type AS sourceType, review_status AS reviewStatus,",
            "  confirmed_by AS confirmedBy, confirmed_at AS confirmedAt, first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt",
            "FROM operations_competitor_product",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND review_status = 'CONFIRMED'",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<CompetitorProductRow> listConfirmedCompetitorProductsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Insert({
            "INSERT INTO operations_competitor_keyword_product (",
            "  id, keyword_id, competitor_product_id, relation_status,",
            "  first_seen_run_id, last_seen_run_id, first_seen_rank_no, last_seen_rank_no,",
            "  last_seen_sponsored, last_seen_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{keywordId}, #{competitorProductId}, 'DISCOVERED',",
            "  #{searchRunId}, #{searchRunId}, #{rankNo}, #{rankNo}, #{sponsored}, NOW(),",
            "  b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  relation_status = CASE WHEN relation_status = 'IGNORED' THEN relation_status ELSE 'DISCOVERED' END,",
            "  last_seen_run_id = VALUES(last_seen_run_id),",
            "  first_seen_rank_no = COALESCE(first_seen_rank_no, VALUES(first_seen_rank_no)),",
            "  last_seen_rank_no = VALUES(last_seen_rank_no),",
            "  last_seen_sponsored = VALUES(last_seen_sponsored),",
            "  last_seen_at = NOW(),",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertKeywordProductRelationFromSearch(CompetitorKeywordProductSearchCommand command);

    @Insert({
            "INSERT INTO operations_competitor_search_result (",
            "  id, keyword_run_id, result_position, noon_product_code, code_type, canonical_url,",
            "  title_snapshot, brand_snapshot, image_url_snapshot, price_amount, currency_code,",
            "  rating, review_count, is_sponsored, raw_result_json, captured_at, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{keywordRunId}, #{resultPosition}, #{noonProductCode}, #{codeType}, #{canonicalUrl},",
            "  #{titleSnapshot}, #{brandSnapshot}, #{imageUrlSnapshot}, #{priceAmount}, #{currencyCode},",
            "  #{rating}, #{reviewCount}, #{sponsored}, #{rawResultJson}, #{capturedAt}, b'0',",
            "  #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertSearchResult(CompetitorSearchResultInsertCommand command);

    @Insert({
            "INSERT INTO operations_competitor_rank_fact (",
            "  id, watch_product_id, keyword_id, keyword_run_id, search_run_id, fact_time, fact_date,",
            "  tracked_product_type, noon_product_code, rank_status, rank_no, is_sponsored,",
            "  price_amount, currency_code, rating, review_count, source_result_id, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{watchProductId}, #{keywordId}, #{keywordRunId}, #{searchRunId}, #{factTime}, #{factDate},",
            "  #{trackedProductType}, #{noonProductCode}, #{rankStatus}, #{rankNo}, #{sponsored},",
            "  #{priceAmount}, #{currencyCode}, #{rating}, #{reviewCount}, #{sourceResultId}, b'0',",
            "  #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertRankFact(CompetitorRankFactInsertCommand command);

    @Update({
            "UPDATE operations_competitor_keyword",
            "SET last_provider_status = #{providerStatus},",
            "    last_succeeded_at = NOW(),",
            "    last_error_code = NULL,",
            "    last_error_message = NULL,",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{keywordId}",
            "  AND is_deleted = b'0'"
    })
    int markKeywordProviderSucceeded(
            @Param("keywordId") Long keywordId,
            @Param("providerStatus") String providerStatus,
            @Param("actorUserId") Long actorUserId
    );

    @Update({
            "UPDATE operations_competitor_keyword",
            "SET last_provider_status = 'FAILED',",
            "    last_error_code = #{errorCode},",
            "    last_error_message = #{errorMessage},",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{keywordId}",
            "  AND is_deleted = b'0'"
    })
    int markKeywordProviderFailed(
            @Param("keywordId") Long keywordId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("actorUserId") Long actorUserId
    );

    @Update({
            "UPDATE operations_competitor_watch_product",
            "SET latest_run_id = #{runId},",
            "    latest_run_status = #{runStatus},",
            "    latest_run_at = NOW(),",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{watchProductId}",
            "  AND is_deleted = b'0'"
    })
    int updateWatchProductLatestRun(
            @Param("watchProductId") Long watchProductId,
            @Param("runId") Long runId,
            @Param("runStatus") String runStatus,
            @Param("actorUserId") Long actorUserId
    );
}
