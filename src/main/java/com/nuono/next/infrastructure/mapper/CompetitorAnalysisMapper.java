package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorDashboardAttributeChangeRow;
import com.nuono.next.competitoranalysis.CompetitorDashboardProductRow;
import com.nuono.next.competitoranalysis.CompetitorDashboardRankChangeRow;
import com.nuono.next.competitoranalysis.CompetitorDashboardSummaryRow;
import com.nuono.next.competitoranalysis.CompetitorDashboardTrendRow;
import com.nuono.next.competitoranalysis.CompetitorKeywordProductRow;
import com.nuono.next.competitoranalysis.CompetitorKeywordProductSearchCommand;
import com.nuono.next.competitoranalysis.CompetitorKeywordInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorKeywordRow;
import com.nuono.next.competitoranalysis.CompetitorKeywordRunInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorKeywordRunRow;
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
import com.nuono.next.competitoranalysis.CompetitorTransientKeywordFailureRow;
import com.nuono.next.competitoranalysis.CompetitorWatchProductInsertCommand;
import com.nuono.next.competitoranalysis.CompetitorWatchProductListRow;
import com.nuono.next.competitoranalysis.CompetitorWatchProductQuery;
import com.nuono.next.competitoranalysis.CompetitorWatchProductRow;
import com.nuono.next.competitoranalysis.CompetitorWatchProductScopeRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
            "SELECT COUNT(1)",
            "FROM operations_competitor_product cp",
            "JOIN operations_competitor_watch_product wp",
            "  ON wp.id = cp.watch_product_id",
            " AND wp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND cp.review_status = 'PENDING'",
            "  AND cp.is_deleted = b'0'"
    })
    long countPendingCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM (",
            "  SELECT wp.id, COUNT(cp.id) AS confirmed_count",
            "  FROM operations_competitor_watch_product wp",
            "  LEFT JOIN operations_competitor_product cp",
            "    ON cp.watch_product_id = wp.id",
            "   AND cp.review_status = 'CONFIRMED'",
            "   AND cp.is_deleted = b'0'",
            "  WHERE wp.owner_user_id = #{ownerUserId}",
            "    AND wp.store_code = #{storeCode}",
            "    AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "    AND wp.status = 'ACTIVE'",
            "    AND wp.is_deleted = b'0'",
            "  GROUP BY wp.id",
            "  HAVING confirmed_count < #{targetCount}",
            ") shortage"
    })
    long countMonitoringShortageProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("targetCount") int targetCount
    );

    @Select({
            "SELECT COUNT(DISTINCT wp.id)",
            "FROM operations_competitor_rank_fact rf",
            "JOIN operations_competitor_watch_product wp",
            "  ON wp.id = rf.watch_product_id",
            " AND wp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND rf.fact_date >= #{fromDate}",
            "  AND rf.is_deleted = b'0'",
            "  AND (",
            "    (rf.tracked_product_type = 'SELF' AND (rf.rank_status <> 'RANKED' OR rf.rank_no IS NULL OR rf.rank_no > 100))",
            "    OR (rf.tracked_product_type = 'COMPETITOR' AND rf.is_sponsored = b'1')",
            "  )"
    })
    long countRankAnomalyProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("fromDate") LocalDate fromDate
    );

    @Select({
            "SELECT COUNT(DISTINCT changed.watch_product_id)",
            "FROM operations_competitor_product_change_event changed",
            "JOIN operations_competitor_watch_product wp",
            "  ON wp.id = changed.watch_product_id",
            " AND wp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND changed.subject_type = 'COMPETITOR'",
            "  AND changed.field_key IN ('price', 'title')",
            "  AND changed.fact_date >= #{fromDate}",
            "  AND changed.is_deleted = b'0'"
    })
    long countCompetitorChangeProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("fromDate") LocalDate fromDate
    );

    @Select({
            "SELECT trend_date AS date, issue_type AS issueType, label, COUNT(1) AS value",
            "FROM (",
            "  SELECT DATE(COALESCE(cp.first_seen_at, cp.gmt_create)) AS trend_date,",
            "         'PENDING_CANDIDATE' AS issue_type, '待确认候选' AS label",
            "  FROM operations_competitor_product cp",
            "  JOIN operations_competitor_watch_product wp ON wp.id = cp.watch_product_id AND wp.is_deleted = b'0'",
            "  WHERE wp.owner_user_id = #{ownerUserId}",
            "    AND wp.store_code = #{storeCode}",
            "    AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "    AND cp.review_status = 'PENDING'",
            "    AND cp.is_deleted = b'0'",
            "    AND DATE(COALESCE(cp.first_seen_at, cp.gmt_create)) >= #{fromDate}",
            "  UNION ALL",
            "  SELECT DATE(wp.gmt_updated) AS trend_date,",
            "         'MONITORING_SHORTAGE' AS issue_type, '监控不足' AS label",
            "  FROM operations_competitor_watch_product wp",
            "  LEFT JOIN operations_competitor_product cp",
            "    ON cp.watch_product_id = wp.id AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "  WHERE wp.owner_user_id = #{ownerUserId}",
            "    AND wp.store_code = #{storeCode}",
            "    AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "    AND wp.status = 'ACTIVE'",
            "    AND wp.is_deleted = b'0'",
            "    AND DATE(wp.gmt_updated) >= #{fromDate}",
            "  GROUP BY wp.id, DATE(wp.gmt_updated)",
            "  HAVING COUNT(cp.id) < 3",
            "  UNION ALL",
            "  SELECT rf.fact_date AS trend_date,",
            "         'RANK_ANOMALY' AS issue_type, '排名异常' AS label",
            "  FROM operations_competitor_rank_fact rf",
            "  JOIN operations_competitor_watch_product wp ON wp.id = rf.watch_product_id AND wp.is_deleted = b'0'",
            "  WHERE wp.owner_user_id = #{ownerUserId}",
            "    AND wp.store_code = #{storeCode}",
            "    AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "    AND rf.fact_date >= #{fromDate}",
            "    AND rf.is_deleted = b'0'",
            "    AND ((rf.tracked_product_type = 'SELF' AND (rf.rank_status <> 'RANKED' OR rf.rank_no IS NULL OR rf.rank_no > 100))",
            "      OR (rf.tracked_product_type = 'COMPETITOR' AND rf.is_sponsored = b'1'))",
            ") trend",
            "GROUP BY trend_date, issue_type, label",
            "ORDER BY trend_date ASC, issue_type ASC"
    })
    List<CompetitorDashboardTrendRow> listDashboardIssueTrend(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("fromDate") LocalDate fromDate
    );

    @Select({
            "SELECT wp.id AS watchProductId, wp.product_site_offer_id AS productSiteOfferId,",
            "       wp.partner_sku AS partnerSku, COALESCE(NULLIF(wp.title_snapshot, ''), wp.partner_sku) AS title,",
            "       'MONITORING_SHORTAGE' AS issueType,",
            "       GREATEST(#{targetCount} - COUNT(cp.id), 0) AS value,",
            "       #{targetCount} AS targetValue",
            "FROM operations_competitor_watch_product wp",
            "LEFT JOIN operations_competitor_product cp",
            "  ON cp.watch_product_id = wp.id",
            " AND cp.review_status = 'CONFIRMED'",
            " AND cp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND wp.status = 'ACTIVE'",
            "  AND wp.is_deleted = b'0'",
            "GROUP BY wp.id, wp.product_site_offer_id, wp.partner_sku, wp.title_snapshot",
            "HAVING COUNT(cp.id) < #{targetCount}",
            "ORDER BY value DESC, wp.gmt_updated DESC",
            "LIMIT #{limit}"
    })
    List<CompetitorDashboardProductRow> listCoverageTopProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("targetCount") int targetCount,
            @Param("limit") int limit
    );

    @Select({
            "SELECT wp.id AS watchProductId, wp.product_site_offer_id AS productSiteOfferId,",
            "       wp.partner_sku AS partnerSku, COALESCE(NULLIF(wp.title_snapshot, ''), wp.partner_sku) AS title,",
            "       'RANK_ANOMALY' AS issueType, COUNT(1) AS value",
            "FROM operations_competitor_rank_fact rf",
            "JOIN operations_competitor_watch_product wp ON wp.id = rf.watch_product_id AND wp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND rf.fact_date >= #{fromDate}",
            "  AND rf.is_deleted = b'0'",
            "  AND ((rf.tracked_product_type = 'SELF' AND (rf.rank_status <> 'RANKED' OR rf.rank_no IS NULL OR rf.rank_no > 100))",
            "    OR (rf.tracked_product_type = 'COMPETITOR' AND rf.is_sponsored = b'1'))",
            "GROUP BY wp.id, wp.product_site_offer_id, wp.partner_sku, wp.title_snapshot",
            "ORDER BY value DESC, MAX(rf.fact_time) DESC",
            "LIMIT #{limit}"
    })
    List<CompetitorDashboardProductRow> listRankIssueTopProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("limit") int limit
    );

    @Select({
            "SELECT CASE ev.field_key",
            "         WHEN 'price' THEN 'PRICE'",
            "         WHEN 'rating' THEN 'RATING'",
            "         WHEN 'reviewCount' THEN 'REVIEW_COUNT'",
            "         WHEN 'mainImage' THEN 'IMAGE'",
            "         WHEN 'title' THEN 'TITLE'",
            "         WHEN 'brand' THEN 'BRAND'",
            "         ELSE UPPER(ev.field_key)",
            "       END AS changeType,",
            "       CASE ev.field_key",
            "         WHEN 'price' THEN '价格变化'",
            "         WHEN 'rating' THEN '评分变化'",
            "         WHEN 'reviewCount' THEN '评论数变化'",
            "         WHEN 'mainImage' THEN '主图变化'",
            "         WHEN 'title' THEN '标题变化'",
            "         WHEN 'brand' THEN '品牌变化'",
            "         ELSE CONCAT(ev.field_label, '变化')",
            "       END AS label,",
            "       'COMPETITOR_CHANGE' AS issueType, COUNT(1) AS value",
            "FROM operations_competitor_product_change_event ev",
            "JOIN operations_competitor_watch_product wp ON wp.id = ev.watch_product_id AND wp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND ev.subject_type = 'COMPETITOR'",
            "  AND ev.field_key IN ('price', 'title')",
            "  AND ev.fact_date >= #{fromDate}",
            "  AND ev.is_deleted = b'0'",
            "GROUP BY changeType, label",
            "ORDER BY value DESC"
    })
    List<CompetitorDashboardSummaryRow> listChangeTypeDistribution(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("fromDate") LocalDate fromDate
    );

    @Select({
            "SELECT wp.id AS watchProductId, wp.product_site_offer_id AS productSiteOfferId,",
            "       wp.partner_sku AS partnerSku, COALESCE(NULLIF(wp.title_snapshot, ''), wp.partner_sku) AS title,",
            "       'COMPETITOR_CHANGE' AS issueType, COUNT(1) AS value",
            "FROM operations_competitor_product_change_event ev",
            "JOIN operations_competitor_watch_product wp ON wp.id = ev.watch_product_id AND wp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND ev.subject_type = 'COMPETITOR'",
            "  AND ev.field_key IN ('price', 'title')",
            "  AND ev.fact_date >= #{fromDate}",
            "  AND ev.is_deleted = b'0'",
            "GROUP BY wp.id, wp.product_site_offer_id, wp.partner_sku, wp.title_snapshot",
            "ORDER BY value DESC, MAX(ev.gmt_create) DESC",
            "LIMIT #{limit}"
    })
    List<CompetitorDashboardProductRow> listChangedProductTop(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("limit") int limit
    );

    @Select({
            "SELECT MAX(rf.fact_date)",
            "FROM operations_competitor_rank_fact rf",
            "JOIN operations_competitor_watch_product wp ON wp.id = rf.watch_product_id AND wp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND rf.fact_date <= #{upperBoundDate}",
            "  AND rf.rank_status = 'RANKED'",
            "  AND rf.rank_no IS NOT NULL",
            "  AND rf.is_deleted = b'0'"
    })
    LocalDate selectLatestRankFactDate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("upperBoundDate") LocalDate upperBoundDate
    );

    @Select({
            "WITH ranked AS (",
            "  SELECT wp.id AS watch_product_id, wp.product_site_offer_id,",
            "         CASE WHEN rf.tracked_product_type = 'SELF' THEN wp.partner_sku",
            "              ELSE COALESCE(NULLIF(rf.noon_product_code, ''), NULLIF(cp.noon_product_code, ''), wp.partner_sku)",
            "         END AS partner_sku,",
            "         CASE WHEN rf.tracked_product_type = 'SELF' THEN COALESCE(NULLIF(wp.title_snapshot, ''), wp.partner_sku)",
            "              ELSE COALESCE(NULLIF(sr.title_snapshot, ''), NULLIF(cp.title_snapshot, ''), rf.noon_product_code)",
            "         END AS title,",
            "         CASE WHEN rf.tracked_product_type = 'SELF' THEN COALESCE(sr.image_url_snapshot, wp.image_url_snapshot)",
            "              ELSE COALESCE(sr.image_url_snapshot, cp.image_url_snapshot)",
            "         END AS image_url,",
            "         k.id AS keyword_id, k.keyword, rf.tracked_product_type, rf.noon_product_code,",
            "         rf.rank_status, rf.rank_no, rf.fact_date,",
            "         ROW_NUMBER() OVER endpoint_rank_window AS endpoint_rank_row",
            "  FROM operations_competitor_rank_fact rf",
            "  JOIN operations_competitor_watch_product wp ON wp.id = rf.watch_product_id AND wp.is_deleted = b'0'",
            "  JOIN operations_competitor_keyword k ON k.id = rf.keyword_id AND k.is_deleted = b'0'",
            "  LEFT JOIN operations_competitor_search_result sr ON sr.id = rf.source_result_id AND sr.is_deleted = b'0'",
            "  LEFT JOIN operations_competitor_product cp ON cp.watch_product_id = rf.watch_product_id",
            "    AND cp.noon_product_code = rf.noon_product_code",
            "    AND cp.is_deleted = b'0'",
            "  WHERE wp.owner_user_id = #{ownerUserId}",
            "    AND wp.store_code = #{storeCode}",
            "    AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "    AND rf.tracked_product_type = #{trackedProductType}",
            "    AND rf.fact_date IN (#{fromDate}, #{toDate})",
            "    AND rf.rank_status = 'RANKED'",
            "    AND rf.rank_no IS NOT NULL",
            "    AND rf.is_deleted = b'0'",
            "  WINDOW endpoint_rank_window AS (",
            "    PARTITION BY rf.watch_product_id, rf.keyword_id, rf.tracked_product_type, rf.noon_product_code, rf.fact_date",
            "    ORDER BY rf.fact_time DESC, rf.id DESC",
            "  )",
            "),",
            "range_change_event AS (",
            "  SELECT watch_product_id, subject_type, noon_product_code,",
            "         MAX(CASE WHEN field_key = 'price' THEN CONCAT(",
            "           COALESCE(JSON_UNQUOTE(CAST(old_value_json AS CHAR)), '-'), ' -> ',",
            "           COALESCE(JSON_UNQUOTE(CAST(new_value_json AS CHAR)), '-'), ' · ', fact_date",
            "         ) END) AS price_change_summary,",
            "         MAX(CASE WHEN field_key = 'title' THEN CONCAT('有变化 · ', fact_date) END) AS title_change_summary",
            "  FROM (",
            "    SELECT ev.watch_product_id, ev.subject_type, ev.noon_product_code, ev.field_key,",
            "           ev.old_value_json, ev.new_value_json, ev.fact_date, ev.id,",
            "           ROW_NUMBER() OVER (PARTITION BY ev.watch_product_id, ev.subject_type, ev.noon_product_code, ev.field_key",
            "             ORDER BY ev.fact_date DESC, ev.id DESC) AS change_row",
            "    FROM operations_competitor_product_change_event ev",
            "    JOIN operations_competitor_watch_product wp_event ON wp_event.id = ev.watch_product_id AND wp_event.is_deleted = b'0'",
            "    WHERE wp_event.owner_user_id = #{ownerUserId}",
            "      AND wp_event.store_code = #{storeCode}",
            "      AND UPPER(wp_event.site_code) = UPPER(#{siteCode})",
            "      AND ev.subject_type = #{trackedProductType}",
            "      AND ev.field_key IN ('price', 'title')",
            "      AND ev.fact_date BETWEEN #{fromDate} AND #{toDate}",
            "      AND ev.is_deleted = b'0'",
            "  ) event_ranked",
            "  WHERE change_row = 1",
            "  GROUP BY watch_product_id, subject_type, noon_product_code",
            "),",
            "range_ad_change AS (",
            "  SELECT rf.watch_product_id, rf.keyword_id, rf.tracked_product_type, rf.noon_product_code,",
            "         GROUP_CONCAT(DISTINCT DATE_FORMAT(rf.fact_date, '%m-%d') ORDER BY rf.fact_date DESC SEPARATOR '，') AS ad_change_summary",
            "  FROM operations_competitor_rank_fact rf",
            "  JOIN operations_competitor_watch_product wp_ad ON wp_ad.id = rf.watch_product_id AND wp_ad.is_deleted = b'0'",
            "  WHERE wp_ad.owner_user_id = #{ownerUserId}",
            "    AND wp_ad.store_code = #{storeCode}",
            "    AND UPPER(wp_ad.site_code) = UPPER(#{siteCode})",
            "    AND rf.tracked_product_type = #{trackedProductType}",
            "    AND rf.fact_date BETWEEN #{fromDate} AND #{toDate}",
            "    AND rf.rank_status = 'RANKED'",
            "    AND rf.rank_no IS NOT NULL",
            "    AND rf.is_sponsored = b'1'",
            "    AND rf.is_deleted = b'0'",
            "  GROUP BY rf.watch_product_id, rf.keyword_id, rf.tracked_product_type, rf.noon_product_code",
            "),",
            "first_rank AS (",
            "  SELECT watch_product_id, product_site_offer_id, partner_sku, title, image_url, keyword_id, keyword, tracked_product_type, noon_product_code,",
            "         rank_status AS first_rank_status, rank_no AS first_rank_no, fact_date AS first_rank_date,",
            "         rank_no AS first_rank_sort",
            "  FROM ranked",
            "  WHERE endpoint_rank_row = 1",
            "    AND fact_date = #{fromDate}",
            "),",
            "last_rank AS (",
            "  SELECT watch_product_id, product_site_offer_id, partner_sku, title, image_url, keyword_id, keyword,",
            "         tracked_product_type, noon_product_code, rank_status AS last_rank_status,",
            "         rank_no AS last_rank_no, fact_date AS last_rank_date, rank_no AS last_rank_sort",
            "  FROM ranked",
            "  WHERE endpoint_rank_row = 1",
            "    AND fact_date = #{toDate}",
            ")",
            "SELECT last_rank.watch_product_id AS watchProductId, last_rank.product_site_offer_id AS productSiteOfferId,",
            "       last_rank.partner_sku AS partnerSku, last_rank.title, last_rank.image_url AS imageUrl,",
            "       last_rank.keyword_id AS keywordId, last_rank.keyword,",
            "       last_rank.tracked_product_type AS trackedProductType, last_rank.noon_product_code AS noonProductCode,",
            "       first_rank.first_rank_status AS previousRankStatus, first_rank.first_rank_no AS previousRankNo,",
            "       first_rank.first_rank_date AS previousDate, last_rank.last_rank_status AS rankStatus, last_rank.last_rank_no AS rankNo,",
            "       last_rank.last_rank_date AS currentDate, first_rank.first_rank_sort - last_rank.last_rank_sort AS rankDelta,",
            "       change_event.price_change_summary AS priceChangeSummary, change_event.title_change_summary AS titleChangeSummary,",
            "       ad_change.ad_change_summary AS adChangeSummary",
            "FROM last_rank",
            "JOIN first_rank ON first_rank.watch_product_id = last_rank.watch_product_id",
            "  AND first_rank.keyword_id = last_rank.keyword_id",
            "  AND first_rank.tracked_product_type = last_rank.tracked_product_type",
            "  AND first_rank.noon_product_code = last_rank.noon_product_code",
            "  AND first_rank.first_rank_date = #{fromDate}",
            "  AND last_rank.last_rank_date = #{toDate}",
            "LEFT JOIN range_change_event change_event ON change_event.watch_product_id = last_rank.watch_product_id",
            "  AND change_event.subject_type = last_rank.tracked_product_type",
            "  AND change_event.noon_product_code = last_rank.noon_product_code",
            "LEFT JOIN range_ad_change ad_change ON ad_change.watch_product_id = last_rank.watch_product_id",
            "  AND ad_change.keyword_id = last_rank.keyword_id",
            "  AND ad_change.tracked_product_type = last_rank.tracked_product_type",
            "  AND ad_change.noon_product_code = last_rank.noon_product_code",
            "WHERE last_rank.last_rank_sort <> first_rank.first_rank_sort",
            "  AND (#{rankDirection} IS NULL",
            "    OR (#{rankDirection} = 'UP' AND first_rank.first_rank_sort - last_rank.last_rank_sort > 0)",
            "    OR (#{rankDirection} = 'DOWN' AND first_rank.first_rank_sort - last_rank.last_rank_sort < 0)",
            "  )",
            "ORDER BY ABS(first_rank.first_rank_sort - last_rank.last_rank_sort) DESC, last_rank.last_rank_date DESC, last_rank.watch_product_id ASC",
            "LIMIT #{limit}"
    })
    List<CompetitorDashboardRankChangeRow> listRankChanges(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("trackedProductType") String trackedProductType,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("rankDirection") String rankDirection,
            @Param("limit") int limit
    );

    @Select({
            "SELECT COUNT(DISTINCT ps.watch_product_id, ps.noon_product_code)",
            "FROM operations_competitor_product_snapshot ps",
            "JOIN operations_competitor_watch_product wp ON wp.id = ps.watch_product_id AND wp.is_deleted = b'0'",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND ps.subject_type = 'COMPETITOR'",
            "  AND ps.fact_date >= #{fromDate}",
            "  AND ps.is_deleted = b'0'"
    })
    Long countCompetitorAttributeSnapshots(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("fromDate") LocalDate fromDate
    );

    @Select({
            "WITH latest_rank AS (",
            "  SELECT watch_product_id, noon_product_code, keyword_id, keyword, rank_no",
            "  FROM (",
            "    SELECT rf.watch_product_id, rf.noon_product_code, rf.keyword_id, kw.keyword, rf.rank_no,",
            "           ROW_NUMBER() OVER (PARTITION BY rf.watch_product_id, rf.noon_product_code",
            "             ORDER BY rf.fact_date DESC, rf.fact_time DESC, rf.id DESC) AS rank_row",
            "    FROM operations_competitor_rank_fact rf",
            "    JOIN operations_competitor_watch_product wp_rank ON wp_rank.id = rf.watch_product_id AND wp_rank.is_deleted = b'0'",
            "    LEFT JOIN operations_competitor_keyword kw ON kw.id = rf.keyword_id AND kw.is_deleted = b'0'",
            "    WHERE wp_rank.owner_user_id = #{ownerUserId}",
            "      AND wp_rank.store_code = #{storeCode}",
            "      AND UPPER(wp_rank.site_code) = UPPER(#{siteCode})",
            "      AND rf.tracked_product_type = 'COMPETITOR'",
            "      AND rf.rank_status = 'RANKED'",
            "      AND rf.rank_no IS NOT NULL",
            "      AND rf.is_deleted = b'0'",
            "  ) ranked",
            "  WHERE rank_row = 1",
            "), change_date_rank AS (",
            "  SELECT watch_product_id, noon_product_code, keyword_id, fact_date, rank_no",
            "  FROM (",
            "    SELECT rf.watch_product_id, rf.noon_product_code, rf.keyword_id, rf.fact_date, rf.rank_no,",
            "           ROW_NUMBER() OVER (PARTITION BY rf.watch_product_id, rf.noon_product_code, rf.keyword_id, rf.fact_date",
            "             ORDER BY rf.fact_time DESC, rf.id DESC) AS rank_row",
            "    FROM operations_competitor_rank_fact rf",
            "    JOIN operations_competitor_watch_product wp_change_rank ON wp_change_rank.id = rf.watch_product_id AND wp_change_rank.is_deleted = b'0'",
            "    WHERE wp_change_rank.owner_user_id = #{ownerUserId}",
            "      AND wp_change_rank.store_code = #{storeCode}",
            "      AND UPPER(wp_change_rank.site_code) = UPPER(#{siteCode})",
            "      AND rf.tracked_product_type = 'COMPETITOR'",
            "      AND rf.rank_status = 'RANKED'",
            "      AND rf.rank_no IS NOT NULL",
            "      AND rf.is_deleted = b'0'",
            "  ) ranked_change",
            "  WHERE rank_row = 1",
            "), latest_self_rank AS (",
            "  SELECT watch_product_id, keyword_id, self_latest_rank_status, self_latest_rank_no, self_latest_scan_depth",
            "  FROM (",
            "    SELECT rf.watch_product_id, rf.keyword_id, rf.rank_status AS self_latest_rank_status,",
            "           rf.rank_no AS self_latest_rank_no, rf.scan_depth AS self_latest_scan_depth,",
            "           ROW_NUMBER() OVER (PARTITION BY rf.watch_product_id, rf.keyword_id",
            "             ORDER BY rf.fact_date DESC, rf.fact_time DESC, rf.id DESC) AS rank_row",
            "    FROM operations_competitor_rank_fact rf",
            "    JOIN operations_competitor_watch_product wp_self_rank ON wp_self_rank.id = rf.watch_product_id AND wp_self_rank.is_deleted = b'0'",
            "    WHERE wp_self_rank.owner_user_id = #{ownerUserId}",
            "      AND wp_self_rank.store_code = #{storeCode}",
            "      AND UPPER(wp_self_rank.site_code) = UPPER(#{siteCode})",
            "      AND rf.tracked_product_type = 'SELF'",
            "      AND rf.is_deleted = b'0'",
            "  ) self_ranked",
            "  WHERE rank_row = 1",
            "), latest_self_price_change AS (",
            "  SELECT watch_product_id, self_previous_value, self_current_value, self_current_date",
            "  FROM (",
            "    SELECT ev.watch_product_id,",
            "           JSON_UNQUOTE(CAST(ev.old_value_json AS CHAR)) AS self_previous_value,",
            "           JSON_UNQUOTE(CAST(ev.new_value_json AS CHAR)) AS self_current_value,",
            "           ev.fact_date AS self_current_date,",
            "           ROW_NUMBER() OVER (PARTITION BY ev.watch_product_id ORDER BY ev.fact_date DESC, ev.id DESC) AS change_row",
            "    FROM operations_competitor_product_change_event ev",
            "    JOIN operations_competitor_watch_product wp_self ON wp_self.id = ev.watch_product_id AND wp_self.is_deleted = b'0'",
            "    WHERE wp_self.owner_user_id = #{ownerUserId}",
            "      AND wp_self.store_code = #{storeCode}",
            "      AND UPPER(wp_self.site_code) = UPPER(#{siteCode})",
            "      AND ev.subject_type = 'SELF'",
            "      AND ev.field_key = 'price'",
            "      AND ev.fact_date >= #{fromDate}",
            "      AND ev.is_deleted = b'0'",
            "  ) self_changed",
            "  WHERE change_row = 1",
            "), latest_self_price_snapshot AS (",
            "  SELECT watch_product_id, self_snapshot_count, self_latest_value, self_latest_date",
            "  FROM (",
            "    SELECT ps.watch_product_id,",
            "           COUNT(1) OVER (PARTITION BY ps.watch_product_id) AS self_snapshot_count,",
            "           CAST(ps.price_amount AS CHAR) AS self_latest_value,",
            "           ps.fact_date AS self_latest_date,",
            "           ROW_NUMBER() OVER (PARTITION BY ps.watch_product_id ORDER BY ps.fact_date DESC, ps.id DESC) AS snapshot_row",
            "    FROM operations_competitor_product_snapshot ps",
            "    JOIN operations_competitor_watch_product wp_snapshot ON wp_snapshot.id = ps.watch_product_id AND wp_snapshot.is_deleted = b'0'",
            "    WHERE wp_snapshot.owner_user_id = #{ownerUserId}",
            "      AND wp_snapshot.store_code = #{storeCode}",
            "      AND UPPER(wp_snapshot.site_code) = UPPER(#{siteCode})",
            "      AND ps.subject_type = 'SELF'",
            "      AND ps.fact_date >= #{fromDate}",
            "      AND ps.price_amount IS NOT NULL",
            "      AND ps.is_deleted = b'0'",
            "  ) self_snapshot",
            "  WHERE snapshot_row = 1",
            "), latest_system_price AS (",
            "  SELECT watch_product_id, system_latest_value, system_latest_date",
            "  FROM (",
            "    SELECT wp_system.id AS watch_product_id,",
            "           CAST(COALESCE(public_detail.price_amount, pso.final_price, pso.sale_price, pso.price) AS CHAR) AS system_latest_value,",
            "           DATE(COALESCE(public_detail.fetched_at, pso.price_synced_at, pso.gmt_updated)) AS system_latest_date,",
            "           ROW_NUMBER() OVER (PARTITION BY wp_system.id",
            "             ORDER BY public_detail.fetched_at DESC, pso.price_synced_at DESC, pso.gmt_updated DESC, pso.id DESC) AS price_row",
            "    FROM operations_competitor_watch_product wp_system",
            "    JOIN logical_store ls ON ls.owner_user_id = wp_system.owner_user_id",
            "      AND ls.is_deleted = b'0'",
            "    JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "      AND UPPER(lss.store_code) COLLATE utf8mb4_unicode_ci = UPPER(wp_system.store_code) COLLATE utf8mb4_unicode_ci",
            "      AND UPPER(lss.site) COLLATE utf8mb4_unicode_ci = UPPER(wp_system.site_code) COLLATE utf8mb4_unicode_ci",
            "      AND lss.is_deleted = b'0'",
            "    JOIN product_master pm ON pm.logical_store_id = ls.id",
            "      AND pm.is_deleted = b'0'",
            "    JOIN product_variant pv ON pv.product_master_id = pm.id",
            "      AND pv.partner_sku COLLATE utf8mb4_unicode_ci = wp_system.partner_sku COLLATE utf8mb4_unicode_ci",
            "      AND pv.is_deleted = b'0'",
            "    JOIN product_site_offer pso ON pso.variant_id = pv.id",
            "      AND pso.site_id = lss.id",
            "      AND pso.is_deleted = b'0'",
            "    LEFT JOIN product_public_detail_snapshot public_detail ON public_detail.owner_user_id = wp_system.owner_user_id",
            "      AND UPPER(public_detail.store_code) COLLATE utf8mb4_unicode_ci = UPPER(wp_system.store_code) COLLATE utf8mb4_unicode_ci",
            "      AND UPPER(public_detail.site_code) COLLATE utf8mb4_unicode_ci = UPPER(wp_system.site_code) COLLATE utf8mb4_unicode_ci",
            "      AND public_detail.partner_sku COLLATE utf8mb4_unicode_ci = wp_system.partner_sku COLLATE utf8mb4_unicode_ci",
            "      AND public_detail.source_platform = 'NOON'",
            "      AND public_detail.is_latest = b'1'",
            "      AND public_detail.is_deleted = b'0'",
            "    WHERE wp_system.owner_user_id = #{ownerUserId}",
            "      AND wp_system.store_code = #{storeCode}",
            "      AND UPPER(wp_system.site_code) = UPPER(#{siteCode})",
            "      AND wp_system.is_deleted = b'0'",
            "      AND COALESCE(public_detail.price_amount, pso.final_price, pso.sale_price, pso.price) IS NOT NULL",
            "  ) system_price",
            "  WHERE price_row = 1",
            "), detail_events AS (",
            "  SELECT *",
            "  FROM (",
            "    SELECT ev.*,",
            "           ROW_NUMBER() OVER (PARTITION BY ev.field_key ORDER BY ev.fact_date DESC, ev.id DESC) AS detail_change_row",
            "    FROM operations_competitor_product_change_event ev",
            "    JOIN operations_competitor_watch_product wp_event ON wp_event.id = ev.watch_product_id AND wp_event.is_deleted = b'0'",
            "    WHERE wp_event.owner_user_id = #{ownerUserId}",
            "      AND wp_event.store_code = #{storeCode}",
            "      AND UPPER(wp_event.site_code) = UPPER(#{siteCode})",
            "      AND ev.subject_type = 'COMPETITOR'",
            "      AND ev.field_key IN ('price', 'title')",
            "      AND ev.fact_date >= #{fromDate}",
            "      AND ev.is_deleted = b'0'",
            "  ) changed_events",
            "  WHERE detail_change_row <= #{limit}",
            ")",
            "SELECT ev.watch_product_id AS watchProductId, wp.product_site_offer_id AS productSiteOfferId,",
            "       wp.partner_sku AS partnerSku, COALESCE(NULLIF(wp.title_snapshot, ''), wp.partner_sku) AS title,",
            "       wp.image_url_snapshot AS productImageUrl,",
            "       self_price.self_previous_value AS selfPreviousValue,",
            "       self_price.self_current_value AS selfCurrentValue,",
            "       self_price.self_current_date AS selfCurrentDate,",
            "       COALESCE(self_snapshot.self_snapshot_count, 0) AS selfSnapshotCount,",
            "       COALESCE(self_snapshot.self_latest_value, system_price.system_latest_value) AS selfLatestValue,",
            "       COALESCE(self_snapshot.self_latest_date, system_price.system_latest_date) AS selfLatestDate,",
            "       ev.noon_product_code AS noonProductCode,",
            "       COALESCE(NULLIF(ps.title_en, ''), NULLIF(cp.title_snapshot, ''), ev.noon_product_code) AS competitorTitle,",
            "       COALESCE(NULLIF(ps.main_image_url_normalized, ''), NULLIF(ps.main_image_url_raw, ''), cp.image_url_snapshot) AS competitorImageUrl,",
            "       CASE ev.field_key",
            "         WHEN 'price' THEN 'PRICE'",
            "         WHEN 'rating' THEN 'RATING'",
            "         WHEN 'reviewCount' THEN 'REVIEW_COUNT'",
            "         WHEN 'mainImage' THEN 'IMAGE'",
            "         WHEN 'title' THEN 'TITLE'",
            "         WHEN 'brand' THEN 'BRAND'",
            "         ELSE UPPER(ev.field_key)",
            "       END AS changeType,",
            "       CASE ev.field_key",
            "         WHEN 'price' THEN '价格变化'",
            "         WHEN 'rating' THEN '评分变化'",
            "         WHEN 'reviewCount' THEN '评论数变化'",
            "         WHEN 'mainImage' THEN '主图变化'",
            "         WHEN 'title' THEN '标题变化'",
            "         WHEN 'brand' THEN '品牌变化'",
            "         ELSE CONCAT(ev.field_label, '变化')",
            "       END AS label,",
            "       JSON_UNQUOTE(CAST(ev.old_value_json AS CHAR)) AS previousValue,",
            "       JSON_UNQUOTE(CAST(ev.new_value_json AS CHAR)) AS currentValue,",
            "       ev.fact_date AS currentDate,",
            "       latest_rank.keyword AS latestRankKeyword, change_rank.rank_no AS changeDateRankNo, latest_rank.rank_no AS latestRankNo,",
            "       latest_rank.keyword AS selfLatestRankKeyword,",
            "       self_rank.self_latest_rank_status AS selfLatestRankStatus,",
            "       self_rank.self_latest_rank_no AS selfLatestRankNo,",
            "       self_rank.self_latest_scan_depth AS selfLatestScanDepth",
            "FROM detail_events ev",
            "JOIN operations_competitor_watch_product wp ON wp.id = ev.watch_product_id AND wp.is_deleted = b'0'",
            "LEFT JOIN operations_competitor_product_snapshot ps ON ps.id = ev.snapshot_id AND ps.is_deleted = b'0'",
            "LEFT JOIN operations_competitor_product cp ON cp.id = ev.competitor_product_id AND cp.is_deleted = b'0'",
            "LEFT JOIN latest_rank ON latest_rank.watch_product_id = ev.watch_product_id",
            "  AND latest_rank.noon_product_code = ev.noon_product_code",
            "LEFT JOIN change_date_rank change_rank ON change_rank.watch_product_id = ev.watch_product_id",
            "  AND change_rank.noon_product_code = ev.noon_product_code",
            "  AND change_rank.keyword_id = latest_rank.keyword_id",
            "  AND change_rank.fact_date = ev.fact_date",
            "LEFT JOIN latest_self_rank self_rank ON self_rank.watch_product_id = ev.watch_product_id",
            "  AND self_rank.keyword_id = latest_rank.keyword_id",
            "LEFT JOIN latest_self_price_change self_price ON self_price.watch_product_id = ev.watch_product_id",
            "LEFT JOIN latest_self_price_snapshot self_snapshot ON self_snapshot.watch_product_id = ev.watch_product_id",
            "LEFT JOIN latest_system_price system_price ON system_price.watch_product_id = ev.watch_product_id",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND ev.subject_type = 'COMPETITOR'",
            "  AND ev.field_key IN ('price', 'title')",
            "  AND ev.fact_date >= #{fromDate}",
            "  AND ev.is_deleted = b'0'",
            "ORDER BY ev.field_key ASC, ev.fact_date DESC, ev.id DESC"
    })
    List<CompetitorDashboardAttributeChangeRow> listCompetitorAttributeChanges(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("limit") int limit
    );

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
            "  <if test='query.pendingCandidateCountZero'>",
            "    AND NOT EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp_zero",
            "      WHERE cp_zero.watch_product_id = wp.id",
            "        AND cp_zero.review_status = 'PENDING'",
            "        AND cp_zero.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test='query.confirmedCompetitorCountZero'>",
            "    AND NOT EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp_zero",
            "      WHERE cp_zero.watch_product_id = wp.id",
            "        AND cp_zero.review_status = 'CONFIRMED'",
            "        AND cp_zero.is_deleted = b'0'",
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
            "  wp.psku_code AS pskuCode,",
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
            "    SELECT GROUP_CONCAT(CONCAT(kw.keyword, CHAR(9), (",
            "      SELECT COUNT(DISTINCT cp.id)",
            "      FROM operations_competitor_keyword_product kp",
            "      JOIN operations_competitor_product cp",
            "        ON cp.id = kp.competitor_product_id",
            "       AND cp.is_deleted = b'0'",
            "       AND cp.review_status = 'CONFIRMED'",
            "      WHERE kp.keyword_id = kw.id",
            "        AND kp.relation_status = 'CONFIRMED'",
            "        AND kp.is_deleted = b'0'",
            "        AND kp.id = (",
            "          SELECT MIN(primary_kp.id)",
            "          FROM operations_competitor_keyword_product primary_kp",
            "          JOIN operations_competitor_keyword primary_kw",
            "            ON primary_kw.id = primary_kp.keyword_id",
            "           AND primary_kw.watch_product_id = kw.watch_product_id",
            "           AND primary_kw.status = 'ACTIVE'",
            "           AND primary_kw.is_deleted = b'0'",
            "          WHERE primary_kp.competitor_product_id = kp.competitor_product_id",
            "            AND primary_kp.relation_status = 'CONFIRMED'",
            "            AND primary_kp.is_deleted = b'0'",
            "        )",
            "    )) ORDER BY kw.display_order ASC, kw.id ASC SEPARATOR '||')",
            "    FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  ) AS activeKeywordSummary,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_product cp",
            "    WHERE cp.watch_product_id = wp.id AND cp.review_status = 'PENDING' AND cp.is_deleted = b'0'",
            "  ) AS pendingCandidateCount,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_product cp",
            "    WHERE cp.watch_product_id = wp.id AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "  ) AS confirmedCompetitorCount,",
            "  (",
            "    SELECT COUNT(DISTINCT ce.noon_product_code) FROM operations_competitor_product_change_event ce",
            "    WHERE ce.owner_user_id = wp.owner_user_id",
            "      AND ce.watch_product_id = wp.id",
            "      AND ce.subject_type = 'COMPETITOR'",
            "      AND ce.fact_date >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY)",
            "      AND ce.is_deleted = b'0'",
            "  ) AS recent7dChangedCompetitorCount,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_product_change_event ce",
            "    WHERE ce.owner_user_id = wp.owner_user_id",
            "      AND ce.watch_product_id = wp.id",
            "      AND ce.subject_type = 'COMPETITOR'",
            "      AND ce.fact_date >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY)",
            "      AND ce.is_deleted = b'0'",
            "  ) AS recent7dCompetitorChangeCount",
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
            "  <if test='query.pendingCandidateCountZero'>",
            "    AND NOT EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp_zero",
            "      WHERE cp_zero.watch_product_id = wp.id",
            "        AND cp_zero.review_status = 'PENDING'",
            "        AND cp_zero.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test='query.confirmedCompetitorCountZero'>",
            "    AND NOT EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp_zero",
            "      WHERE cp_zero.watch_product_id = wp.id",
            "        AND cp_zero.review_status = 'CONFIRMED'",
            "        AND cp_zero.is_deleted = b'0'",
            "    )",
            "  </if>",
            "<choose>",
            "  <when test='query.sortBy == \"CANDIDATE_COUNT_ASC\"'>",
            "    ORDER BY pendingCandidateCount ASC, confirmedCompetitorCount ASC, wp.gmt_updated DESC, wp.id DESC",
            "  </when>",
            "  <when test='query.sortBy == \"MONITORED_COUNT_DESC\"'>",
            "    ORDER BY confirmedCompetitorCount DESC, pendingCandidateCount DESC, wp.gmt_updated DESC, wp.id DESC",
            "  </when>",
            "  <when test='query.sortBy == \"MONITORED_COUNT_ASC\"'>",
            "    ORDER BY confirmedCompetitorCount ASC, pendingCandidateCount ASC, wp.gmt_updated DESC, wp.id DESC",
            "  </when>",
            "  <when test='query.sortBy == \"RECENT_7D_CHANGE_COUNT_DESC\"'>",
            "    ORDER BY recent7dCompetitorChangeCount DESC, recent7dChangedCompetitorCount DESC, confirmedCompetitorCount DESC, wp.gmt_updated DESC, wp.id DESC",
            "  </when>",
            "  <when test='query.sortBy == \"RECENT_7D_CHANGE_COUNT_ASC\"'>",
            "    ORDER BY recent7dCompetitorChangeCount ASC, recent7dChangedCompetitorCount ASC, confirmedCompetitorCount ASC, wp.gmt_updated DESC, wp.id DESC",
            "  </when>",
            "  <otherwise>",
            "    ORDER BY pendingCandidateCount DESC, confirmedCompetitorCount DESC, wp.gmt_updated DESC, wp.id DESC",
            "  </otherwise>",
            "</choose>",
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
            "SELECT COUNT(DISTINCT pso.id)",
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
            "LEFT JOIN product_master_draft pmd",
            "  ON pmd.product_master_id = pm.id",
            " AND pmd.is_deleted = b'0'",
            "LEFT JOIN product_master_snapshot pms",
            "  ON pms.id = (",
            "    SELECT pms_latest.id",
            "    FROM product_master_snapshot pms_latest",
            "    WHERE pms_latest.product_master_id = pm.id",
            "      AND pms_latest.snapshot_type = 'baseline'",
            "      AND pms_latest.is_deleted = b'0'",
            "    ORDER BY pms_latest.fetched_at DESC, pms_latest.id DESC",
            "    LIMIT 1",
            "  )",
            "LEFT JOIN operations_competitor_watch_product wp",
            "  ON wp.id = (",
            "    SELECT wp_match.id",
            "    FROM operations_competitor_watch_product wp_match",
            "    WHERE wp_match.owner_user_id = ls.owner_user_id",
            "      AND wp_match.store_code = #{storeCode}",
            "      AND UPPER(wp_match.site_code) = UPPER(#{siteCode})",
            "      AND wp_match.is_deleted = b'0'",
            "      AND (",
            "        (",
            "          wp_match.product_site_offer_id = pso.id",
            "          AND wp_match.partner_sku = pv.partner_sku COLLATE utf8mb4_unicode_ci",
            "        )",
            "        OR (",
            "          NULLIF(TRIM(wp_match.partner_sku), '') IS NOT NULL",
            "          AND wp_match.partner_sku = pv.partner_sku COLLATE utf8mb4_unicode_ci",
            "          AND (wp_match.logical_store_id = ls.id OR wp_match.logical_store_id IS NULL)",
            "        )",
            "      )",
            "    ORDER BY",
            "      CASE WHEN wp_match.product_site_offer_id = pso.id AND wp_match.partner_sku = pv.partner_sku COLLATE utf8mb4_unicode_ci THEN 0 ELSE 1 END,",
            "      wp_match.latest_run_at DESC, wp_match.gmt_updated DESC, wp_match.id DESC",
            "    LIMIT 1",
            "  )",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  <if test='query.status != null and query.status != \"\"'>",
            "    AND COALESCE(wp.status, 'ACTIVE') = #{query.status}",
            "  </if>",
            "  <if test='query.productSearch != null and query.productSearch != \"\"'>",
            "    AND (",
            "      LOWER(COALESCE(pm.sku_parent COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pv.partner_sku COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pv.child_sku COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pso.psku_code COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pso.offer_code COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pm.title_cache COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pm.title_cn_cache COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleEn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleEn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleCn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleZh')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleCn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleZh')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "    )",
            "  </if>",
            "  <if test='query.keywordSearch != null and query.keywordSearch != \"\"'>",
            "    AND wp.id IS NOT NULL",
            "    AND EXISTS (",
            "      SELECT 1 FROM operations_competitor_keyword kw",
            "      WHERE kw.watch_product_id = wp.id",
            "        AND kw.is_deleted = b'0'",
            "        AND LOWER(kw.keyword) LIKE CONCAT('%', LOWER(#{query.keywordSearch}), '%')",
            "    )",
            "  </if>",
            "  <if test='query.competitorSearch != null and query.competitorSearch != \"\"'>",
            "    AND wp.id IS NOT NULL",
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
            "  <if test='query.pendingCandidateCountZero'>",
            "    AND NOT EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp_zero",
            "      WHERE cp_zero.watch_product_id = wp.id",
            "        AND cp_zero.review_status = 'PENDING'",
            "        AND cp_zero.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test='query.confirmedCompetitorCountZero'>",
            "    AND NOT EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp_zero",
            "      WHERE cp_zero.watch_product_id = wp.id",
            "        AND cp_zero.review_status = 'CONFIRMED'",
            "        AND cp_zero.is_deleted = b'0'",
            "    )",
            "  </if>",
            "</script>"
    })
    long countProductBaselines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("query") CompetitorWatchProductQuery query
    );

    @Select({
            "<script>",
            "SELECT",
            "  wp.id AS id,",
            "  ls.owner_user_id AS ownerUserId, lss.store_code AS storeCode, lss.site AS siteCode,",
            "  ls.id AS logicalStoreId, pm.id AS productMasterId, pv.id AS productVariantId,",
            "  pso.id AS productSiteOfferId, pm.sku_parent AS skuParent, pv.partner_sku AS partnerSku,",
            "  pv.child_sku AS childSku, pso.psku_code AS pskuCode,",
            "  wp.self_noon_product_code AS selfNoonProductCode,",
            "  CASE",
            "    WHEN UPPER(COALESCE(wp.self_noon_product_code COLLATE utf8mb4_unicode_ci, pso.psku_code COLLATE utf8mb4_unicode_ci, '')) LIKE 'Z%' THEN 'Z_CODE'",
            "    WHEN UPPER(COALESCE(wp.self_noon_product_code COLLATE utf8mb4_unicode_ci, pso.psku_code COLLATE utf8mb4_unicode_ci, '')) LIKE 'N%' THEN 'N_CODE'",
            "    ELSE NULL",
            "  END AS selfCodeType,",
            "  COALESCE(",
            "    NULLIF(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleEn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, ''),",
            "    NULLIF(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleEn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, ''),",
            "    NULLIF(pm.title_cache COLLATE utf8mb4_unicode_ci, '')",
            "  ) AS titleSnapshot,",
            "  COALESCE(",
            "    NULLIF(pm.title_cn_cache COLLATE utf8mb4_unicode_ci, ''),",
            "    NULLIF(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleCn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, ''),",
            "    NULLIF(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleZh')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, ''),",
            "    NULLIF(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleCn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, ''),",
            "    NULLIF(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleZh')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, ''),",
            "    NULLIF(pm.title_cache COLLATE utf8mb4_unicode_ci, '')",
            "  ) AS titleCnSnapshot,",
            "  pm.brand_cache AS brandSnapshot,",
            "  pm.cover_image_url AS imageUrlSnapshot, pm.product_fulltype_cache AS productFulltypeSnapshot,",
            "  COALESCE(wp.status, 'ACTIVE') AS status, wp.latest_run_id AS latestRunId,",
            "  wp.latest_run_status AS latestRunStatus, wp.latest_run_at AS latestRunAt,",
            "  wp.gmt_updated AS gmtUpdated,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  ) AS activeKeywordCount,",
            "  (",
            "    SELECT GROUP_CONCAT(CONCAT(",
            "      kw.keyword, CHAR(9), (",
            "      SELECT COUNT(DISTINCT cp.id)",
            "      FROM operations_competitor_keyword_product kp",
            "      JOIN operations_competitor_product cp",
            "        ON cp.id = kp.competitor_product_id",
            "       AND cp.is_deleted = b'0'",
            "       AND cp.review_status = 'CONFIRMED'",
            "      WHERE kp.keyword_id = kw.id",
            "        AND kp.relation_status = 'CONFIRMED'",
            "        AND kp.is_deleted = b'0'",
            "        AND kp.id = (",
            "          SELECT MIN(primary_kp.id)",
            "          FROM operations_competitor_keyword_product primary_kp",
            "          JOIN operations_competitor_keyword primary_kw",
            "            ON primary_kw.id = primary_kp.keyword_id",
            "           AND primary_kw.watch_product_id = kw.watch_product_id",
            "           AND primary_kw.status = 'ACTIVE'",
            "           AND primary_kw.is_deleted = b'0'",
            "          WHERE primary_kp.competitor_product_id = kp.competitor_product_id",
            "            AND primary_kp.relation_status = 'CONFIRMED'",
            "            AND primary_kp.is_deleted = b'0'",
            "        )",
            "      ), CHAR(9), COALESCE((",
            "        SELECT prev_rf.rank_status",
            "        FROM operations_competitor_rank_fact prev_rf",
            "        WHERE prev_rf.watch_product_id = kw.watch_product_id",
            "          AND prev_rf.keyword_id = kw.id",
            "          AND prev_rf.tracked_product_type = 'SELF'",
            "          AND prev_rf.is_sponsored = b'0'",
            "          AND prev_rf.fact_date = DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)",
            "          AND prev_rf.is_deleted = b'0'",
            "        ORDER BY prev_rf.fact_time DESC, prev_rf.id DESC",
            "        LIMIT 1",
            "      ), ''), CHAR(9), COALESCE((",
            "        SELECT prev_rf.rank_no",
            "        FROM operations_competitor_rank_fact prev_rf",
            "        WHERE prev_rf.watch_product_id = kw.watch_product_id",
            "          AND prev_rf.keyword_id = kw.id",
            "          AND prev_rf.tracked_product_type = 'SELF'",
            "          AND prev_rf.is_sponsored = b'0'",
            "          AND prev_rf.fact_date = DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)",
            "          AND prev_rf.is_deleted = b'0'",
            "        ORDER BY prev_rf.fact_time DESC, prev_rf.id DESC",
            "        LIMIT 1",
            "      ), ''), CHAR(9), COALESCE((",
            "        SELECT prev_rf.fact_date",
            "        FROM operations_competitor_rank_fact prev_rf",
            "        WHERE prev_rf.watch_product_id = kw.watch_product_id",
            "          AND prev_rf.keyword_id = kw.id",
            "          AND prev_rf.tracked_product_type = 'SELF'",
            "          AND prev_rf.is_sponsored = b'0'",
            "          AND prev_rf.fact_date = DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY)",
            "          AND prev_rf.is_deleted = b'0'",
            "        ORDER BY prev_rf.fact_time DESC, prev_rf.id DESC",
            "        LIMIT 1",
            "      ), ''), CHAR(9), COALESCE((",
            "        SELECT curr_rf.rank_status",
            "        FROM operations_competitor_rank_fact curr_rf",
            "        WHERE curr_rf.watch_product_id = kw.watch_product_id",
            "          AND curr_rf.keyword_id = kw.id",
            "          AND curr_rf.tracked_product_type = 'SELF'",
            "          AND curr_rf.is_sponsored = b'0'",
            "          AND curr_rf.fact_date = CURRENT_DATE",
            "          AND curr_rf.is_deleted = b'0'",
            "        ORDER BY curr_rf.fact_time DESC, curr_rf.id DESC",
            "        LIMIT 1",
            "      ), ''), CHAR(9), COALESCE((",
            "        SELECT curr_rf.rank_no",
            "        FROM operations_competitor_rank_fact curr_rf",
            "        WHERE curr_rf.watch_product_id = kw.watch_product_id",
            "          AND curr_rf.keyword_id = kw.id",
            "          AND curr_rf.tracked_product_type = 'SELF'",
            "          AND curr_rf.is_sponsored = b'0'",
            "          AND curr_rf.fact_date = CURRENT_DATE",
            "          AND curr_rf.is_deleted = b'0'",
            "        ORDER BY curr_rf.fact_time DESC, curr_rf.id DESC",
            "        LIMIT 1",
            "      ), ''), CHAR(9), COALESCE((",
            "        SELECT curr_rf.fact_date",
            "        FROM operations_competitor_rank_fact curr_rf",
            "        WHERE curr_rf.watch_product_id = kw.watch_product_id",
            "          AND curr_rf.keyword_id = kw.id",
            "          AND curr_rf.tracked_product_type = 'SELF'",
            "          AND curr_rf.is_sponsored = b'0'",
            "          AND curr_rf.fact_date = CURRENT_DATE",
            "          AND curr_rf.is_deleted = b'0'",
            "        ORDER BY curr_rf.fact_time DESC, curr_rf.id DESC",
            "        LIMIT 1",
            "      ), '')",
            "    ) ORDER BY kw.display_order ASC, kw.id ASC SEPARATOR '||')",
            "    FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id AND kw.status = 'ACTIVE' AND kw.is_deleted = b'0'",
            "  ) AS activeKeywordSummary,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_product cp",
            "    WHERE cp.watch_product_id = wp.id AND cp.review_status = 'PENDING' AND cp.is_deleted = b'0'",
            "  ) AS pendingCandidateCount,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_product cp",
            "    WHERE cp.watch_product_id = wp.id AND cp.review_status = 'CONFIRMED' AND cp.is_deleted = b'0'",
            "  ) AS confirmedCompetitorCount,",
            "  (",
            "    SELECT COUNT(DISTINCT ce.noon_product_code) FROM operations_competitor_product_change_event ce",
            "    WHERE ce.owner_user_id = ls.owner_user_id",
            "      AND ce.watch_product_id = wp.id",
            "      AND ce.subject_type = 'COMPETITOR'",
            "      AND ce.fact_date >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY)",
            "      AND ce.is_deleted = b'0'",
            "  ) AS recent7dChangedCompetitorCount,",
            "  (",
            "    SELECT COUNT(1) FROM operations_competitor_product_change_event ce",
            "    WHERE ce.owner_user_id = ls.owner_user_id",
            "      AND ce.watch_product_id = wp.id",
            "      AND ce.subject_type = 'COMPETITOR'",
            "      AND ce.fact_date >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY)",
            "      AND ce.is_deleted = b'0'",
            "  ) AS recent7dCompetitorChangeCount",
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
            "LEFT JOIN product_master_draft pmd",
            "  ON pmd.product_master_id = pm.id",
            " AND pmd.is_deleted = b'0'",
            "LEFT JOIN product_master_snapshot pms",
            "  ON pms.id = (",
            "    SELECT pms_latest.id",
            "    FROM product_master_snapshot pms_latest",
            "    WHERE pms_latest.product_master_id = pm.id",
            "      AND pms_latest.snapshot_type = 'baseline'",
            "      AND pms_latest.is_deleted = b'0'",
            "    ORDER BY pms_latest.fetched_at DESC, pms_latest.id DESC",
            "    LIMIT 1",
            "  )",
            "LEFT JOIN operations_competitor_watch_product wp",
            "  ON wp.id = (",
            "    SELECT wp_match.id",
            "    FROM operations_competitor_watch_product wp_match",
            "    WHERE wp_match.owner_user_id = ls.owner_user_id",
            "      AND wp_match.store_code = #{storeCode}",
            "      AND UPPER(wp_match.site_code) = UPPER(#{siteCode})",
            "      AND wp_match.is_deleted = b'0'",
            "      AND (",
            "        (",
            "          wp_match.product_site_offer_id = pso.id",
            "          AND wp_match.partner_sku = pv.partner_sku COLLATE utf8mb4_unicode_ci",
            "        )",
            "        OR (",
            "          NULLIF(TRIM(wp_match.partner_sku), '') IS NOT NULL",
            "          AND wp_match.partner_sku = pv.partner_sku COLLATE utf8mb4_unicode_ci",
            "          AND (wp_match.logical_store_id = ls.id OR wp_match.logical_store_id IS NULL)",
            "        )",
            "      )",
            "    ORDER BY",
            "      CASE WHEN wp_match.product_site_offer_id = pso.id AND wp_match.partner_sku = pv.partner_sku COLLATE utf8mb4_unicode_ci THEN 0 ELSE 1 END,",
            "      wp_match.latest_run_at DESC, wp_match.gmt_updated DESC, wp_match.id DESC",
            "    LIMIT 1",
            "  )",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  <if test='query.status != null and query.status != \"\"'>",
            "    AND COALESCE(wp.status, 'ACTIVE') = #{query.status}",
            "  </if>",
            "  <if test='query.productSearch != null and query.productSearch != \"\"'>",
            "    AND (",
            "      LOWER(COALESCE(pm.sku_parent COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pv.partner_sku COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pv.child_sku COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pso.psku_code COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pso.offer_code COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pm.title_cache COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(pm.title_cn_cache COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleEn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleEn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleCn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleZh')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleCn')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "      OR LOWER(COALESCE(CONVERT(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleZh')) USING utf8mb4) COLLATE utf8mb4_unicode_ci, '')) LIKE CONCAT('%', LOWER(CAST(#{query.productSearch} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci), '%')",
            "    )",
            "  </if>",
            "  <if test='query.keywordSearch != null and query.keywordSearch != \"\"'>",
            "    AND wp.id IS NOT NULL",
            "    AND EXISTS (",
            "      SELECT 1 FROM operations_competitor_keyword kw",
            "      WHERE kw.watch_product_id = wp.id",
            "        AND kw.is_deleted = b'0'",
            "        AND LOWER(kw.keyword) LIKE CONCAT('%', LOWER(#{query.keywordSearch}), '%')",
            "    )",
            "  </if>",
            "  <if test='query.competitorSearch != null and query.competitorSearch != \"\"'>",
            "    AND wp.id IS NOT NULL",
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
            "  <if test='query.pendingCandidateCountZero'>",
            "    AND NOT EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp_zero",
            "      WHERE cp_zero.watch_product_id = wp.id",
            "        AND cp_zero.review_status = 'PENDING'",
            "        AND cp_zero.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test='query.confirmedCompetitorCountZero'>",
            "    AND NOT EXISTS (",
            "      SELECT 1 FROM operations_competitor_product cp_zero",
            "      WHERE cp_zero.watch_product_id = wp.id",
            "        AND cp_zero.review_status = 'CONFIRMED'",
            "        AND cp_zero.is_deleted = b'0'",
            "    )",
            "  </if>",
            "<choose>",
            "  <when test='query.sortBy == \"CANDIDATE_COUNT_ASC\"'>",
            "    ORDER BY pendingCandidateCount ASC, confirmedCompetitorCount ASC, pm.sku_parent ASC, pv.partner_sku ASC, pso.id ASC",
            "  </when>",
            "  <when test='query.sortBy == \"MONITORED_COUNT_DESC\"'>",
            "    ORDER BY confirmedCompetitorCount DESC, pendingCandidateCount DESC, pm.sku_parent ASC, pv.partner_sku ASC, pso.id ASC",
            "  </when>",
            "  <when test='query.sortBy == \"MONITORED_COUNT_ASC\"'>",
            "    ORDER BY confirmedCompetitorCount ASC, pendingCandidateCount ASC, pm.sku_parent ASC, pv.partner_sku ASC, pso.id ASC",
            "  </when>",
            "  <when test='query.sortBy == \"RECENT_7D_CHANGE_COUNT_DESC\"'>",
            "    ORDER BY recent7dCompetitorChangeCount DESC, recent7dChangedCompetitorCount DESC, confirmedCompetitorCount DESC, pm.sku_parent ASC, pv.partner_sku ASC, pso.id ASC",
            "  </when>",
            "  <when test='query.sortBy == \"RECENT_7D_CHANGE_COUNT_ASC\"'>",
            "    ORDER BY recent7dCompetitorChangeCount ASC, recent7dChangedCompetitorCount ASC, confirmedCompetitorCount ASC, pm.sku_parent ASC, pv.partner_sku ASC, pso.id ASC",
            "  </when>",
            "  <otherwise>",
            "    ORDER BY pendingCandidateCount DESC, confirmedCompetitorCount DESC, pm.sku_parent ASC, pv.partner_sku ASC, pso.id ASC",
            "  </otherwise>",
            "</choose>",
            "LIMIT #{query.pageSize} OFFSET #{query.offset}",
            "</script>"
    })
    List<CompetitorWatchProductListRow> listProductBaselines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
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
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
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
            "  AND (",
            "    (NULLIF(TRIM(pso.psku_code), '') IS NOT NULL AND (UPPER(pso.psku_code) LIKE 'Z%' OR UPPER(pso.psku_code) LIKE 'N%'))",
            "    OR (NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL AND (UPPER(pm.sku_parent) LIKE 'Z%' OR UPPER(pm.sku_parent) LIKE 'N%'))",
            "  )",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(pm.sku_parent) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pv.partner_sku, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pv.child_sku, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pso.psku_code, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pm.title_cache, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pm.title_cn_cache, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
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

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, keyword, keyword_norm AS keywordNorm, locale,",
            "  status, display_order AS displayOrder, last_provider_status AS lastProviderStatus,",
            "  last_succeeded_at AS lastSucceededAt, last_error_code AS lastErrorCode,",
            "  last_error_message AS lastErrorMessage",
            "FROM operations_competitor_keyword",
            "WHERE id = #{keywordId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorKeywordRow selectKeywordById(@Param("keywordId") Long keywordId);

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
            "  wp.partner_sku AS partnerSku,",
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
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "  product_variant_id AS productVariantId, product_site_offer_id AS productSiteOfferId,",
            "  sku_parent AS skuParent, partner_sku AS partnerSku, child_sku AS childSku,",
            "  self_noon_product_code AS selfNoonProductCode, self_code_type AS selfCodeType,",
            "  title_snapshot AS titleSnapshot, brand_snapshot AS brandSnapshot,",
            "  image_url_snapshot AS imageUrlSnapshot, product_fulltype_snapshot AS productFulltypeSnapshot,",
            "  status, latest_run_id AS latestRunId, latest_run_status AS latestRunStatus,",
            "  latest_run_at AS latestRunAt, gmt_updated AS gmtUpdated",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.owner_user_id = #{ownerUserId}",
            "  AND wp.store_code = #{storeCode}",
            "  AND UPPER(wp.site_code) = UPPER(#{siteCode})",
            "  AND wp.status = 'ACTIVE'",
            "  AND wp.is_deleted = b'0'",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE'",
            "      AND kw.is_deleted = b'0'",
            "  )",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    JOIN operations_competitor_keyword_product kp",
            "      ON kp.keyword_id = kw.id",
            "     AND kp.relation_status = 'CONFIRMED'",
            "     AND kp.is_deleted = b'0'",
            "    JOIN operations_competitor_product cp",
            "      ON cp.id = kp.competitor_product_id",
            "     AND cp.review_status = 'CONFIRMED'",
            "     AND cp.is_deleted = b'0'",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE'",
            "      AND kw.is_deleted = b'0'",
            "  )",
            "ORDER BY wp.id ASC",
            "LIMIT #{limit}"
    })
    List<CompetitorWatchProductRow> listRefreshableWatchProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("limit") int limit
    );

    @Select({
            "SELECT",
            "  MIN(wp.id) AS id, wp.owner_user_id AS ownerUserId, wp.store_code AS storeCode, wp.site_code AS siteCode",
            "FROM operations_competitor_watch_product wp",
            "WHERE wp.status = 'ACTIVE'",
            "  AND wp.is_deleted = b'0'",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE'",
            "      AND kw.is_deleted = b'0'",
            "  )",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    JOIN operations_competitor_keyword_product kp",
            "      ON kp.keyword_id = kw.id",
            "     AND kp.relation_status = 'CONFIRMED'",
            "     AND kp.is_deleted = b'0'",
            "    JOIN operations_competitor_product cp",
            "      ON cp.id = kp.competitor_product_id",
            "     AND cp.review_status = 'CONFIRMED'",
            "     AND cp.is_deleted = b'0'",
            "    WHERE kw.watch_product_id = wp.id",
            "      AND kw.status = 'ACTIVE'",
            "      AND kw.is_deleted = b'0'",
            "  )",
            "GROUP BY wp.owner_user_id, wp.store_code, wp.site_code",
            "ORDER BY wp.owner_user_id ASC, wp.store_code ASC, wp.site_code ASC",
            "LIMIT #{limit}"
    })
    List<CompetitorWatchProductScopeRow> listRefreshableWatchProductScopes(@Param("limit") int limit);

    @Select({
            "SELECT",
            "  id, watch_product_id AS watchProductId, noon_product_code AS noonProductCode, code_type AS codeType,",
            "  canonical_url AS canonicalUrl, title_snapshot AS titleSnapshot,",
            "  title_en_snapshot AS titleEnSnapshot, title_ar_snapshot AS titleArSnapshot,",
            "  brand_snapshot AS brandSnapshot, image_url_snapshot AS imageUrlSnapshot,",
            "  price_amount_snapshot AS priceAmountSnapshot,",
            "  currency_code_snapshot AS currencyCodeSnapshot, rating_snapshot AS ratingSnapshot,",
            "  review_count_snapshot AS reviewCountSnapshot, tags_snapshot_json AS tagsSnapshotJson,",
            "  source_type AS sourceType, review_status AS reviewStatus,",
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
            "  title_en_snapshot, title_ar_snapshot, brand_snapshot, image_url_snapshot,",
            "  price_amount_snapshot, currency_code_snapshot, rating_snapshot, review_count_snapshot,",
            "  tags_snapshot_json, source_type, review_status, confirmed_by, confirmed_at,",
            "  first_seen_at, last_seen_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{watchProductId}, #{noonProductCode}, #{codeType}, #{canonicalUrl}, #{titleSnapshot},",
            "  #{titleEnSnapshot}, #{titleArSnapshot}, #{brandSnapshot}, #{imageUrlSnapshot},",
            "  #{priceAmountSnapshot}, #{currencyCodeSnapshot}, #{ratingSnapshot}, #{reviewCountSnapshot},",
            "  #{tagsSnapshotJson}, #{sourceType}, #{reviewStatus},",
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
            "    title_en_snapshot = COALESCE(#{titleEnSnapshot}, title_en_snapshot),",
            "    title_ar_snapshot = COALESCE(#{titleArSnapshot}, title_ar_snapshot),",
            "    brand_snapshot = COALESCE(#{brandSnapshot}, brand_snapshot),",
            "    image_url_snapshot = COALESCE(#{imageUrlSnapshot}, image_url_snapshot),",
            "    price_amount_snapshot = COALESCE(#{priceAmountSnapshot}, price_amount_snapshot),",
            "    currency_code_snapshot = COALESCE(#{currencyCodeSnapshot}, currency_code_snapshot),",
            "    rating_snapshot = COALESCE(#{ratingSnapshot}, rating_snapshot),",
            "    review_count_snapshot = COALESCE(#{reviewCountSnapshot}, review_count_snapshot),",
            "    tags_snapshot_json = COALESCE(#{tagsSnapshotJson}, tags_snapshot_json),",
            "    last_seen_at = NOW(),",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    int updateCompetitorProductFromSearch(CompetitorProductInsertCommand command);

    @Update({
            "UPDATE operations_competitor_product",
            "SET canonical_url = COALESCE(#{canonicalUrl}, canonical_url),",
            "    title_snapshot = COALESCE(#{titleSnapshot}, title_snapshot),",
            "    title_en_snapshot = COALESCE(#{titleEnSnapshot}, title_en_snapshot),",
            "    title_ar_snapshot = COALESCE(#{titleArSnapshot}, title_ar_snapshot),",
            "    brand_snapshot = COALESCE(#{brandSnapshot}, brand_snapshot),",
            "    image_url_snapshot = COALESCE(#{imageUrlSnapshot}, image_url_snapshot),",
            "    price_amount_snapshot = COALESCE(#{priceAmountSnapshot}, price_amount_snapshot),",
            "    currency_code_snapshot = COALESCE(#{currencyCodeSnapshot}, currency_code_snapshot),",
            "    rating_snapshot = COALESCE(#{ratingSnapshot}, rating_snapshot),",
            "    review_count_snapshot = COALESCE(#{reviewCountSnapshot}, review_count_snapshot),",
            "    tags_snapshot_json = COALESCE(#{tagsSnapshotJson}, tags_snapshot_json),",
            "    source_type = COALESCE(#{sourceType}, source_type),",
            "    last_seen_at = NOW(),",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    int updateCompetitorProductFromDetail(CompetitorProductInsertCommand command);

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
            "SET is_deleted = b'1',",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE keyword_id = #{keywordId}",
            "  AND competitor_product_id = #{competitorProductId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteKeywordProductRelation(
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
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
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
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
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
            "  AND pv.partner_sku = CAST(#{partnerSku} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci",
            "  AND (",
            "    (NULLIF(TRIM(pso.psku_code), '') IS NOT NULL AND (UPPER(pso.psku_code) LIKE 'Z%' OR UPPER(pso.psku_code) LIKE 'N%'))",
            "    OR (NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL AND (UPPER(pm.sku_parent) LIKE 'Z%' OR UPPER(pm.sku_parent) LIKE 'N%'))",
            "  )",
            "ORDER BY pso.id DESC",
            "LIMIT 1"
    })
    CompetitorProductOptionRow selectProductOptionByPartnerSku(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku
    );

    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "  product_variant_id AS productVariantId, product_site_offer_id AS productSiteOfferId,",
            "  sku_parent AS skuParent, partner_sku AS partnerSku, child_sku AS childSku,",
            "  psku_code AS pskuCode,",
            "  self_noon_product_code AS selfNoonProductCode, self_code_type AS selfCodeType,",
            "  title_snapshot AS titleSnapshot, brand_snapshot AS brandSnapshot,",
            "  image_url_snapshot AS imageUrlSnapshot, product_fulltype_snapshot AS productFulltypeSnapshot,",
            "  status, latest_run_id AS latestRunId, latest_run_status AS latestRunStatus,",
            "  latest_run_at AS latestRunAt, gmt_updated AS gmtUpdated",
            "FROM operations_competitor_watch_product",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND is_deleted = b'0'",
            "  AND (",
            "    (",
            "      product_site_offer_id = #{productSiteOfferId}",
            "      AND partner_sku = CAST(#{partnerSku} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci",
            "    )",
            "    OR (",
            "      NULLIF(TRIM(partner_sku), '') IS NOT NULL",
            "      AND partner_sku = CAST(#{partnerSku} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci",
            "      AND (logical_store_id = #{logicalStoreId} OR logical_store_id IS NULL)",
            "    )",
            "  )",
            "ORDER BY",
            "  CASE WHEN product_site_offer_id = #{productSiteOfferId} AND partner_sku = CAST(#{partnerSku} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci THEN 0 ELSE 1 END,",
            "  latest_run_at DESC, gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    CompetitorWatchProductRow selectReusableWatchProductByProductIdentity(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("productSiteOfferId") Long productSiteOfferId,
            @Param("partnerSku") String partnerSku
    );

    @Insert({
            "INSERT INTO operations_competitor_watch_product (",
            "  id, owner_user_id, store_code, site_code, logical_store_id, product_master_id,",
            "  product_variant_id, product_site_offer_id, sku_parent, partner_sku, child_sku, psku_code,",
            "  self_noon_product_code, self_code_type, title_snapshot, brand_snapshot,",
            "  image_url_snapshot, product_fulltype_snapshot, status, is_deleted, created_by, updated_by,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{logicalStoreId}, #{productMasterId},",
            "  #{productVariantId}, #{productSiteOfferId}, #{skuParent}, #{partnerSku}, #{childSku}, #{pskuCode},",
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
            "  psku_code AS pskuCode,",
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
            "  AND store_code = #{storeCode}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND product_site_offer_id = #{productSiteOfferId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorWatchProductRow selectWatchProductByProductSiteOfferId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("productSiteOfferId") Long productSiteOfferId
    );

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
            "  AND store_code = #{storeCode}",
            "  AND UPPER(site_code) = UPPER(#{siteCode})",
            "  AND partner_sku = #{partnerSku}",
            "  AND self_noon_product_code = #{selfNoonProductCode}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    CompetitorWatchProductRow selectWatchProductByBusinessKey(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("selfNoonProductCode") String selfNoonProductCode
    );

    @Update({
            "UPDATE operations_competitor_watch_product",
            "SET logical_store_id = #{option.logicalStoreId},",
            "    product_master_id = #{option.productMasterId},",
            "    product_variant_id = #{option.productVariantId},",
            "    product_site_offer_id = #{option.productSiteOfferId},",
            "    sku_parent = #{option.skuParent},",
            "    child_sku = #{option.childSku},",
            "    psku_code = #{option.pskuCode},",
            "    self_noon_product_code = #{selfNoonProductCode},",
            "    self_code_type = #{selfCodeType},",
            "    title_snapshot = #{option.title},",
            "    brand_snapshot = #{option.brand},",
            "    image_url_snapshot = #{option.imageUrl},",
            "    product_fulltype_snapshot = #{option.productFulltype},",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{watchProductId}",
            "  AND is_deleted = b'0'"
    })
    int updateWatchProductCurrentBinding(
            @Param("watchProductId") Long watchProductId,
            @Param("option") CompetitorProductOptionRow option,
            @Param("selfNoonProductCode") String selfNoonProductCode,
            @Param("selfCodeType") String selfCodeType,
            @Param("actorUserId") Long actorUserId
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
            "  cp.id, cp.watch_product_id AS watchProductId, cp.noon_product_code AS noonProductCode, cp.code_type AS codeType,",
            "  cp.canonical_url AS canonicalUrl, cp.title_snapshot AS titleSnapshot,",
            "  cp.title_en_snapshot AS titleEnSnapshot, cp.title_ar_snapshot AS titleArSnapshot,",
            "  cp.brand_snapshot AS brandSnapshot, cp.image_url_snapshot AS imageUrlSnapshot,",
            "  cp.price_amount_snapshot AS priceAmountSnapshot,",
            "  cp.currency_code_snapshot AS currencyCodeSnapshot, cp.rating_snapshot AS ratingSnapshot,",
            "  cp.review_count_snapshot AS reviewCountSnapshot, cp.tags_snapshot_json AS tagsSnapshotJson,",
            "  cp.source_type AS sourceType, cp.review_status AS reviewStatus,",
            "  CASE WHEN EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_watch_product wp",
            "    JOIN product_site_offer base_pso",
            "      ON base_pso.id = wp.product_site_offer_id",
            "     AND base_pso.is_deleted = b'0'",
            "    JOIN product_site_offer pso",
            "      ON pso.site_id = base_pso.site_id",
            "     AND pso.is_deleted = b'0'",
            "    JOIN product_variant pv",
            "      ON pv.id = pso.variant_id",
            "     AND pv.is_deleted = b'0'",
            "    JOIN product_master pm",
            "      ON pm.id = pv.product_master_id",
            "     AND pm.logical_store_id = wp.logical_store_id",
            "     AND pm.is_deleted = b'0'",
            "    WHERE wp.id = cp.watch_product_id",
            "      AND wp.is_deleted = b'0'",
            "      AND BINARY UPPER(TRIM(pso.psku_code)) = BINARY UPPER(TRIM(cp.noon_product_code))",
            "  ) THEN TRUE ELSE FALSE END AS ownedByCurrentStore,",
            "  cp.confirmed_by AS confirmedBy, cp.confirmed_at AS confirmedAt, cp.first_seen_at AS firstSeenAt, cp.last_seen_at AS lastSeenAt",
            "FROM operations_competitor_product cp",
            "WHERE cp.watch_product_id = #{watchProductId}",
            "  AND cp.is_deleted = b'0'",
            "ORDER BY FIELD(cp.review_status, 'PENDING', 'CONFIRMED', 'IGNORED'), cp.id ASC"
    })
    List<CompetitorProductRow> listProductsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  id, keyword_id AS keywordId, competitor_product_id AS competitorProductId,",
            "  relation_status AS relationStatus, first_seen_rank_no AS firstSeenRankNo,",
            "  first_seen_run_id AS firstSeenRunId, last_seen_run_id AS lastSeenRunId,",
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
            "  rf.rank_channel AS rankChannel, rf.scan_depth AS scanDepth,",
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
            "      AND latest.rank_channel = rf.rank_channel",
            "      AND latest.is_deleted = b'0'",
            "  )",
            "ORDER BY kw.display_order ASC, rf.tracked_product_type ASC, rf.rank_channel ASC, rf.rank_no ASC"
    })
    List<CompetitorLatestRankPointRow> listLatestRankPointsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  rf.keyword_id AS keywordId, kw.keyword AS keyword, rf.tracked_product_type AS trackedProductType,",
            "  rf.noon_product_code AS noonProductCode, rf.rank_status AS rankStatus, rf.rank_no AS rankNo,",
            "  rf.rank_channel AS rankChannel, rf.scan_depth AS scanDepth,",
            "  rf.is_sponsored AS sponsored, rf.price_amount AS priceAmount, rf.currency_code AS currencyCode,",
            "  rf.fact_time AS factTime",
            "FROM operations_competitor_rank_fact rf",
            "JOIN operations_competitor_keyword kw",
            "  ON kw.id = rf.keyword_id",
            "WHERE rf.watch_product_id = #{watchProductId}",
            "  AND rf.keyword_id = #{keywordId}",
            "  AND rf.fact_time >= #{fromTime}",
            "  AND rf.is_deleted = b'0'",
            "  AND kw.is_deleted = b'0'",
            "ORDER BY rf.fact_time DESC, rf.tracked_product_type ASC, rf.rank_channel ASC, COALESCE(rf.rank_no, 999999) ASC, rf.noon_product_code ASC",
            "LIMIT #{limit}"
    })
    List<CompetitorLatestRankPointRow> listRankHistoryByWatchProductIdAndKeywordId(
            @Param("watchProductId") Long watchProductId,
            @Param("keywordId") Long keywordId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("limit") Integer limit
    );

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
            "  result_count, requested_result_limit, source_url, parser_version, provider_http_status, response_hash,",
            "  captured_at, error_code, error_message, started_at, finished_at, is_deleted, created_by, updated_by,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{searchRunId}, #{keywordId}, #{keywordSnapshot}, #{localeSnapshot}, #{providerStatus},",
            "  #{resultCount}, #{requestedResultLimit}, #{sourceUrl}, #{parserVersion}, #{providerHttpStatus}, #{responseHash},",
            "  #{capturedAt}, #{errorCode}, #{errorMessage}, NOW(), NOW(), b'0', #{actorUserId}, #{actorUserId},",
            "  NOW(), NOW()",
            ")"
    })
    int insertKeywordRun(CompetitorKeywordRunInsertCommand command);

    @Select({
            "SELECT",
            "  id, search_run_id AS searchRunId, keyword_id AS keywordId,",
            "  keyword_snapshot AS keywordSnapshot, captured_at AS capturedAt",
            "FROM operations_competitor_keyword_run",
            "WHERE keyword_id = #{keywordId}",
            "  AND provider_status = 'SUCCESS'",
            "  AND is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    CompetitorKeywordRunRow selectLatestSucceededKeywordRunByKeywordId(@Param("keywordId") Long keywordId);

    @Select({
            "SELECT",
            "  kr.search_run_id AS searchRunId,",
            "  sr.watch_product_id AS watchProductId,",
            "  kr.keyword_id AS keywordId",
            "FROM operations_competitor_keyword_run kr",
            "JOIN operations_competitor_search_run sr",
            "  ON sr.id = kr.search_run_id",
            " AND sr.is_deleted = b'0'",
            "JOIN operations_competitor_keyword kw",
            "  ON kw.id = kr.keyword_id",
            " AND kw.status = 'ACTIVE'",
            " AND kw.is_deleted = b'0'",
            "JOIN operations_competitor_watch_product wp",
            "  ON wp.id = sr.watch_product_id",
            " AND wp.status = 'ACTIVE'",
            " AND wp.is_deleted = b'0'",
            "WHERE sr.trigger_mode = 'SCHEDULED_RANK_MONITOR'",
            "  AND sr.status = 'PARTIAL_FAILED'",
            "  AND sr.started_at >= #{sinceTime}",
            "  AND kr.provider_status = 'FAILED'",
            "  AND kr.error_code = 'PROVIDER_UNAVAILABLE'",
            "  AND kr.is_deleted = b'0'",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword_run newer",
            "    WHERE newer.search_run_id = kr.search_run_id",
            "      AND newer.keyword_id = kr.keyword_id",
            "      AND newer.id > kr.id",
            "      AND newer.is_deleted = b'0'",
            "  )",
            "  AND (",
            "    SELECT COUNT(*)",
            "    FROM operations_competitor_keyword_run attempts",
            "    WHERE attempts.search_run_id = kr.search_run_id",
            "      AND attempts.keyword_id = kr.keyword_id",
            "      AND attempts.provider_status = 'FAILED'",
            "      AND attempts.error_code = 'PROVIDER_UNAVAILABLE'",
            "      AND attempts.is_deleted = b'0'",
            "  ) = 1",
            "ORDER BY kr.id ASC",
            "LIMIT #{limit}"
    })
    List<CompetitorTransientKeywordFailureRow> listRetryableTransientRankKeywordFailures(
            @Param("sinceTime") LocalDateTime sinceTime,
            @Param("limit") int limit
    );

    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "  product_variant_id AS productVariantId, product_site_offer_id AS productSiteOfferId,",
            "  sku_parent AS skuParent, partner_sku AS partnerSku, child_sku AS childSku,",
            "  psku_code AS pskuCode,",
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
            "  canonical_url AS canonicalUrl, title_snapshot AS titleSnapshot,",
            "  title_en_snapshot AS titleEnSnapshot, title_ar_snapshot AS titleArSnapshot,",
            "  brand_snapshot AS brandSnapshot, image_url_snapshot AS imageUrlSnapshot,",
            "  price_amount_snapshot AS priceAmountSnapshot,",
            "  currency_code_snapshot AS currencyCodeSnapshot, rating_snapshot AS ratingSnapshot,",
            "  review_count_snapshot AS reviewCountSnapshot, tags_snapshot_json AS tagsSnapshotJson,",
            "  source_type AS sourceType, review_status AS reviewStatus,",
            "  confirmed_by AS confirmedBy, confirmed_at AS confirmedAt, first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt",
            "FROM operations_competitor_product",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND review_status = 'CONFIRMED'",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<CompetitorProductRow> listConfirmedCompetitorProductsByWatchProductId(@Param("watchProductId") Long watchProductId);

    @Select({
            "SELECT",
            "  cp.id, cp.watch_product_id AS watchProductId, cp.noon_product_code AS noonProductCode, cp.code_type AS codeType,",
            "  cp.canonical_url AS canonicalUrl, cp.title_snapshot AS titleSnapshot,",
            "  cp.title_en_snapshot AS titleEnSnapshot, cp.title_ar_snapshot AS titleArSnapshot,",
            "  cp.brand_snapshot AS brandSnapshot, cp.image_url_snapshot AS imageUrlSnapshot,",
            "  cp.price_amount_snapshot AS priceAmountSnapshot,",
            "  cp.currency_code_snapshot AS currencyCodeSnapshot, cp.rating_snapshot AS ratingSnapshot,",
            "  cp.review_count_snapshot AS reviewCountSnapshot, cp.tags_snapshot_json AS tagsSnapshotJson,",
            "  cp.source_type AS sourceType, cp.review_status AS reviewStatus,",
            "  cp.confirmed_by AS confirmedBy, cp.confirmed_at AS confirmedAt, cp.first_seen_at AS firstSeenAt, cp.last_seen_at AS lastSeenAt",
            "FROM operations_competitor_product cp",
            "JOIN operations_competitor_keyword_product kp",
            "  ON kp.competitor_product_id = cp.id",
            " AND kp.keyword_id = #{keywordId}",
            " AND kp.relation_status <> 'IGNORED'",
            " AND kp.is_deleted = b'0'",
            "WHERE cp.review_status = 'CONFIRMED'",
            "  AND cp.is_deleted = b'0'",
            "ORDER BY cp.id ASC"
    })
    List<CompetitorProductRow> listConfirmedCompetitorProductsByKeywordId(@Param("keywordId") Long keywordId);

    @Insert({
            "INSERT INTO operations_competitor_keyword_product (",
            "  id, keyword_id, competitor_product_id, relation_status,",
            "  first_seen_run_id, last_seen_run_id, first_seen_rank_no, last_seen_rank_no,",
            "  last_seen_sponsored, last_seen_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{keywordId}, #{competitorProductId}, #{relationStatus},",
            "  #{searchRunId}, #{searchRunId}, #{rankNo}, #{rankNo}, #{sponsored}, NOW(),",
            "  b'0', #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  relation_status = VALUES(relation_status),",
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

    @Update({
            "<script>",
            "UPDATE operations_competitor_keyword_product",
            "SET is_deleted = b'1',",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE keyword_id = #{keywordId}",
            "  AND relation_status = 'DISCOVERED'",
            "  AND is_deleted = b'0'",
            "  <if test='competitorProductIds != null and competitorProductIds.size() > 0'>",
            "    AND competitor_product_id NOT IN",
            "    <foreach collection='competitorProductIds' item='competitorProductId' open='(' separator=',' close=')'>",
            "      #{competitorProductId}",
            "    </foreach>",
            "  </if>",
            "</script>"
    })
    int softDeleteDiscoveredKeywordProductRelationsOutsideSet(
            @Param("keywordId") Long keywordId,
            @Param("competitorProductIds") List<Long> competitorProductIds,
            @Param("actorUserId") Long actorUserId
    );

    @Insert({
            "INSERT INTO operations_competitor_search_result (",
            "  id, keyword_run_id, result_position, noon_product_code, code_type, canonical_url,",
            "  title_snapshot, title_en_snapshot, title_ar_snapshot, brand_snapshot, image_url_snapshot,",
            "  price_amount, currency_code, rating, review_count, is_sponsored, tags_json,",
            "  raw_result_json, captured_at, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{keywordRunId}, #{resultPosition}, #{noonProductCode}, #{codeType}, #{canonicalUrl},",
            "  #{titleSnapshot}, #{titleEnSnapshot}, #{titleArSnapshot}, #{brandSnapshot}, #{imageUrlSnapshot},",
            "  #{priceAmount}, #{currencyCode}, #{rating}, #{reviewCount}, #{sponsored}, #{tagsJson},",
            "  #{rawResultJson}, #{capturedAt}, b'0',",
            "  #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertSearchResult(CompetitorSearchResultInsertCommand command);

    @Update({
            "UPDATE operations_competitor_search_result",
            "SET is_sponsored = b'1',",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE keyword_run_id = #{keywordRunId}",
            "  AND noon_product_code = #{noonProductCode}",
            "  AND is_deleted = b'0'"
    })
    int markSearchResultSponsored(
            @Param("keywordRunId") Long keywordRunId,
            @Param("noonProductCode") String noonProductCode,
            @Param("actorUserId") Long actorUserId
    );

    @Insert({
            "INSERT INTO operations_competitor_rank_fact (",
            "  id, watch_product_id, keyword_id, keyword_run_id, search_run_id, fact_time, fact_date,",
            "  tracked_product_type, rank_channel, noon_product_code, rank_status, rank_no, scan_depth, is_sponsored,",
            "  price_amount, currency_code, rating, review_count, source_result_id, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{watchProductId}, #{keywordId}, #{keywordRunId}, #{searchRunId}, #{factTime}, #{factDate},",
            "  #{trackedProductType}, #{rankChannel}, #{noonProductCode}, #{rankStatus}, #{rankNo}, #{scanDepth}, #{sponsored},",
            "  #{priceAmount}, #{currencyCode}, #{rating}, #{reviewCount}, #{sourceResultId}, b'0',",
            "  #{actorUserId}, #{actorUserId}, NOW(), NOW()",
            ")"
    })
    int insertRankFact(CompetitorRankFactInsertCommand command);

    @Update({
            "UPDATE operations_competitor_rank_fact",
            "SET is_sponsored = b'1',",
            "    updated_by = #{actorUserId},",
            "    gmt_updated = NOW()",
            "WHERE keyword_run_id = #{keywordRunId}",
            "  AND noon_product_code = #{noonProductCode}",
            "  AND is_deleted = b'0'"
    })
    int markRankFactSponsored(
            @Param("keywordRunId") Long keywordRunId,
            @Param("noonProductCode") String noonProductCode,
            @Param("actorUserId") Long actorUserId
    );

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
