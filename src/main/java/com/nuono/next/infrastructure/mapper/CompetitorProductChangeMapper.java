package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.CompetitorProductChangeEventRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CompetitorProductChangeMapper {

    @Select({
            "SELECT",
            "  ce.id, ce.fact_date AS factDate, ce.noon_product_code AS noonProductCode,",
            "  COALESCE(NULLIF(sn.title_en, ''), NULLIF(cp.title_snapshot, ''), NULLIF(wp.title_snapshot, ''), ce.noon_product_code) AS productName,",
            "  ce.subject_type AS subjectType, ce.field_key AS fieldKey, ce.field_label AS fieldLabel,",
            "  ce.change_type AS changeType, ce.old_value_json AS oldValueJson, ce.new_value_json AS newValueJson,",
            "  ce.severity",
            "FROM operations_competitor_product_change_event ce",
            "JOIN operations_competitor_watch_product wp",
            "  ON wp.id = ce.watch_product_id",
            " AND wp.owner_user_id = #{ownerUserId}",
            " AND wp.is_deleted = b'0'",
            "LEFT JOIN operations_competitor_product_snapshot sn",
            "  ON sn.id = ce.snapshot_id",
            " AND sn.is_deleted = b'0'",
            "LEFT JOIN operations_competitor_product cp",
            "  ON cp.id = ce.competitor_product_id",
            " AND cp.is_deleted = b'0'",
            "WHERE ce.owner_user_id = #{ownerUserId}",
            "  AND ce.watch_product_id = #{watchProductId}",
            "  AND ce.is_deleted = b'0'",
            "ORDER BY ce.fact_date DESC, ce.snapshot_id DESC, ce.id ASC",
            "LIMIT #{limit}"
    })
    List<CompetitorProductChangeEventRow> listProductChangeEvents(
            @Param("ownerUserId") Long ownerUserId,
            @Param("watchProductId") Long watchProductId,
            @Param("limit") int limit
    );
}
