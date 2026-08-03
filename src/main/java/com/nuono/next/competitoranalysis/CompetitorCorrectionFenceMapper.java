package com.nuono.next.competitoranalysis;

import org.apache.ibatis.annotations.Select;

public interface CompetitorCorrectionFenceMapper {
    @Select({
            "SELECT CASE",
            "WHEN fence_status = 'OPEN' AND (",
            "  (generation = 0 AND operation_run_id IS NULL",
            "    AND activated_by IS NULL AND activated_at IS NULL",
            "    AND reopened_by IS NULL AND reopened_at IS NULL)",
            "  OR (generation > 0 AND operation_run_id IS NOT NULL",
            "    AND activated_by IS NOT NULL AND activated_at IS NOT NULL",
            "    AND reopened_by IS NOT NULL AND reopened_at IS NOT NULL)",
            ") THEN 'OPEN'",
            "WHEN fence_status = 'ACTIVE' AND generation > 0",
            "  AND operation_run_id IS NOT NULL",
            "  AND activated_by IS NOT NULL AND activated_at IS NOT NULL",
            "  AND reopened_by IS NULL AND reopened_at IS NULL",
            "THEN 'ACTIVE'",
            "ELSE 'INVALID' END",
            "FROM operations_competitor_correction_writer_fence",
            "WHERE fence_name = 'HISTORICAL_BUSINESS_DATE_CORRECTION'",
            "LIMIT 1 FOR SHARE"
    })
    String lockCompetitorCorrectionWriterFence();
}
