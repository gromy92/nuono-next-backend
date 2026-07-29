package com.nuono.next.infrastructure.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CompetitorListCoverageMapper {

    @Select({
            "SELECT CASE WHEN",
            "  NOT EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword kw",
            "    WHERE kw.watch_product_id = #{watchProductId}",
            "      AND kw.status = 'ACTIVE'",
            "      AND kw.is_deleted = b'0'",
            "      AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM operations_competitor_keyword_run kr",
            "    JOIN operations_competitor_search_run sr",
            "      ON sr.id = kr.search_run_id",
            "     AND sr.watch_product_id = kw.watch_product_id",
            "     AND sr.is_deleted = b'0'",
            "    WHERE kr.keyword_id = kw.id",
            "      AND kr.provider_status = 'SUCCESS'",
            "      AND kr.requested_result_limit = 200",
            "      AND DATE(kr.captured_at) = #{factDate}",
            "      AND kr.is_deleted = b'0'",
            "      )",
            "  )",
            "THEN TRUE ELSE FALSE END"
    })
    boolean hasCompleteRankScanCoverage(
            @Param("watchProductId") Long watchProductId,
            @Param("factDate") LocalDate factDate
    );

    @Select({
            "SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END",
            "FROM operations_competitor_rank_fact",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND UPPER(noon_product_code) = UPPER(#{noonProductCode})",
            "  AND fact_date = #{factDate}",
            "  AND rank_status = 'RANKED'",
            "  AND scan_depth = 200",
            "  AND is_deleted = b'0'"
    })
    boolean hasRankedFactInTop200(
            @Param("watchProductId") Long watchProductId,
            @Param("noonProductCode") String noonProductCode,
            @Param("factDate") LocalDate factDate
    );

    @Select({
            "SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END",
            "FROM operations_competitor_product_snapshot",
            "WHERE watch_product_id = #{watchProductId}",
            "  AND UPPER(noon_product_code) = UPPER(#{noonProductCode})",
            "  AND fact_date = #{factDate}",
            "  AND NULLIF(TRIM(title_en), '') IS NOT NULL",
            "  AND NULLIF(TRIM(title_ar), '') IS NOT NULL",
            "  AND is_deleted = b'0'"
    })
    boolean hasCompleteListTitlesToday(
            @Param("watchProductId") Long watchProductId,
            @Param("noonProductCode") String noonProductCode,
            @Param("factDate") LocalDate factDate
    );
}
