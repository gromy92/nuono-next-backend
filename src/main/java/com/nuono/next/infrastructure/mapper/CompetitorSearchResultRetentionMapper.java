package com.nuono.next.infrastructure.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * Deletes only expired evidence that belongs to a terminal competitor refresh run.
 *
 * <p>The nested derived table is intentional: MySQL must materialize the bounded
 * candidate list before deleting from the same result table.</p>
 */
public interface CompetitorSearchResultRetentionMapper {
    @Delete({
            "DELETE sr",
            "FROM operations_competitor_search_result sr",
            "JOIN (",
            "  SELECT eligible.id",
            "  FROM (",
            "    SELECT candidate.id",
            "    FROM operations_competitor_search_result candidate",
            "    JOIN operations_competitor_keyword_run keyword_run",
            "      ON keyword_run.id = candidate.keyword_run_id",
            "     AND keyword_run.is_deleted = b'0'",
            "    JOIN operations_competitor_search_run search_run",
            "      ON search_run.id = keyword_run.search_run_id",
            "     AND search_run.is_deleted = b'0'",
            "    WHERE candidate.is_deleted = b'0'",
            "      AND search_run.status IN ('SUCCEEDED', 'PARTIAL_FAILED', 'FAILED')",
            "      AND search_run.finished_at IS NOT NULL",
            "      AND search_run.finished_at < #{cutoff}",
            "    ORDER BY search_run.finished_at ASC, candidate.id ASC",
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
