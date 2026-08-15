package com.nuono.next.infrastructure.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * Deletes only expired evidence that belongs to a terminal competitor refresh run.
 *
 * <p>The nested derived table is intentional: MySQL must materialize the bounded
 * candidate list before deleting from the same result table. The terminal run is
 * the forced outer driver so each batch starts from the retention lookup index.</p>
 */
public interface CompetitorSearchResultRetentionMapper {
    @Delete({
            "DELETE sr",
            "FROM operations_competitor_search_result sr",
            "JOIN (",
            "  SELECT eligible.id",
            "  FROM (",
            "    SELECT candidate.id",
            "    FROM operations_competitor_search_run search_run",
            "    FORCE INDEX (idx_ops_comp_search_run_retention)",
            "    STRAIGHT_JOIN operations_competitor_keyword_run keyword_run",
            "    FORCE INDEX (idx_ops_comp_keyword_run_search)",
            "      ON keyword_run.search_run_id = search_run.id",
            "     AND keyword_run.is_deleted = b'0'",
            "    STRAIGHT_JOIN operations_competitor_search_result candidate",
            "    FORCE INDEX (uk_ops_comp_search_result_position)",
            "      ON candidate.keyword_run_id = keyword_run.id",
            "    WHERE search_run.is_deleted = b'0'",
            "      AND search_run.status IN ('SUCCEEDED', 'PARTIAL_FAILED', 'FAILED')",
            "      AND search_run.finished_at IS NOT NULL",
            "      AND search_run.finished_at < #{cutoff}",
            "      AND candidate.is_deleted = b'0'",
            "    LIMIT #{limit}",
            "  ) eligible",
            ") candidates ON candidates.id = sr.id",
            "WHERE sr.is_deleted = b'0'"
    })
    int deleteExpiredTerminalSearchResults(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );
}
