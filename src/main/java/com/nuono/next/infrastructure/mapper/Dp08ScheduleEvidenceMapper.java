package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.dp08.Dp08EvidenceRequestRow;
import com.nuono.next.competitoranalysis.dp08.Dp08EvidenceResultRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** One set-based evidence query for the current bounded DP08B task batch. */
@Mapper
public interface Dp08ScheduleEvidenceMapper {
    @Select({
            "<script>",
            "SELECT request.scopeKey, request.factDate, request.watchProductId,",
            " request.competitorProductId,",
            "CASE WHEN EXISTS (SELECT 1 FROM operations_competitor_rank_fact rank_fact",
            " WHERE rank_fact.watch_product_id = request.watchProductId",
            "  AND UPPER(rank_fact.noon_product_code) = request.noonProductCode",
            "  AND rank_fact.fact_date = request.factDate",
            "  AND rank_fact.rank_status = 'RANKED' AND rank_fact.scan_depth = 200",
            "  AND rank_fact.is_deleted = b'0') THEN TRUE ELSE FALSE END AS ranked,",
            "CASE WHEN EXISTS (SELECT 1 FROM operations_competitor_product_snapshot snapshot",
            " WHERE snapshot.watch_product_id = request.watchProductId",
            "  AND UPPER(snapshot.noon_product_code) = request.noonProductCode",
            "  AND snapshot.fact_date = request.factDate",
            "  AND NULLIF(TRIM(snapshot.title_en), '') IS NOT NULL",
            "  AND NULLIF(TRIM(snapshot.title_ar), '') IS NOT NULL",
            "  AND snapshot.is_deleted = b'0') THEN TRUE ELSE FALSE END AS completeTitles",
            "FROM (",
            "<foreach collection='requests' item='item' separator=' UNION ALL '>",
            " SELECT #{item.scopeKey} scopeKey, #{item.factDate} factDate,",
            "  #{item.watchProductId} watchProductId,",
            "  #{item.competitorProductId} competitorProductId,",
            "  #{item.noonProductCode} noonProductCode",
            "</foreach>",
            ") request",
            "ORDER BY BINARY request.scopeKey, request.factDate, request.watchProductId,",
            " request.competitorProductId",
            "</script>"
    })
    List<Dp08EvidenceResultRow> listEvidence(
            @Param("requests") List<Dp08EvidenceRequestRow> requests
    );
}
