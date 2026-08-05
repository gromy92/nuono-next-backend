package com.nuono.next.infrastructure.mapper;

import com.nuono.next.competitoranalysis.Dp08SearchResultIdentityRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** DP08-only bounded search-result identity read for member-batched rank writes. */
public interface Dp08RankMemberMapper {
    @Select({"SELECT id,noon_product_code AS noonProductCode,is_sponsored AS sponsored",
            "FROM operations_competitor_search_result WHERE keyword_run_id=#{keywordRunId}",
            "AND is_deleted=b'0' ORDER BY result_position,id LIMIT 201"})
    List<Dp08SearchResultIdentityRow> listSearchResultIdentities(
            @Param("keywordRunId") Long keywordRunId);
}
