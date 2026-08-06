package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

/** Repair-safe bounded retention for report-stage rows and their now-empty headers. */
public interface ReportStageRetentionMapper {

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_stage_row",
            "WHERE (task_id,`row_number`) IN (",
            "  SELECT task_id,`row_number` FROM (",
            "    SELECT stage_row.task_id,stage_row.`row_number`",
            "    FROM dp_pull_report_stage_row stage_row",
            "    INNER JOIN dp_pull_report_stage stage ON stage.task_id = stage_row.task_id",
            "    INNER JOIN dp_pull_task task ON task.id = stage.task_id",
            "    WHERE task.state IN ('SUCCEEDED', 'SUPERSEDED')",
            "      AND task.finished_at IS NOT NULL",
            "      AND task.finished_at &lt; #{cutoffUtc}",
            "      AND stage.gmt_updated &lt; #{cutoffUtc}",
            "      AND task.lease_owner IS NULL",
            "      AND task.lease_until IS NULL",
            "      AND (task.state = 'SUPERSEDED' OR (",
            "        stage.state = 'APPLIED'",
            "        AND EXISTS (SELECT 1 FROM dp_pull_report_apply applied",
            "                    WHERE applied.task_id = task.id)",
            "        AND (task.operation_code &lt;&gt; 'DP07B' OR stage.accepted_row_count = 0",
            "             OR EXISTS (SELECT 1 FROM official_warehouse_report_import imported",
            "                        WHERE imported.id = stage.fact_container_id",
            "                          AND imported.is_deleted = b'0'))",
            "      ))",
            "    ORDER BY task.finished_at ASC, stage_row.task_id ASC, stage_row.`row_number` ASC",
            "    LIMIT #{batchSize}",
            "  ) eligible_stage_row",
            ")",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalRowsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_stage_row",
            "WHERE (task_id,`row_number`) IN (SELECT task_id,`row_number` FROM (",
            " SELECT row_item.task_id,row_item.`row_number`",
            " FROM dp_pull_report_stage_row row_item",
            " JOIN dp_pull_report_stage stage ON stage.task_id=row_item.task_id",
            " JOIN dp_pull_task task ON task.id=stage.task_id",
            " WHERE task.state='FAILED' AND task.finished_at IS NOT NULL",
            "  AND task.finished_at &lt; #{cutoffUtc}",
            "  AND stage.gmt_updated &lt; #{cutoffUtc}",
            "  AND task.lease_owner IS NULL AND task.lease_until IS NULL",
            " ORDER BY task.finished_at,row_item.task_id,row_item.`row_number` LIMIT #{batchSize}",
            ") abandoned_stage_row)",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedRowsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_stage",
            "WHERE task_id IN (",
            "  SELECT task_id FROM (",
            "    SELECT stage.task_id",
            "    FROM dp_pull_report_stage stage",
            "    INNER JOIN dp_pull_task task ON task.id = stage.task_id",
            "    WHERE task.state IN ('SUCCEEDED', 'SUPERSEDED')",
            "      AND task.finished_at IS NOT NULL",
            "      AND task.finished_at &lt; #{cutoffUtc}",
            "      AND stage.gmt_updated &lt; #{cutoffUtc}",
            "      AND task.lease_owner IS NULL",
            "      AND task.lease_until IS NULL",
            "      AND (task.state = 'SUPERSEDED' OR (",
            "        stage.state = 'APPLIED'",
            "        AND EXISTS (SELECT 1 FROM dp_pull_report_apply applied",
            "                    WHERE applied.task_id = task.id)",
            "        AND (task.operation_code &lt;&gt; 'DP07B' OR stage.accepted_row_count = 0",
            "             OR EXISTS (SELECT 1 FROM official_warehouse_report_import imported",
            "                        WHERE imported.id = stage.fact_container_id",
            "                          AND imported.is_deleted = b'0'))",
            "      ))",
            "      AND NOT EXISTS (",
            "        SELECT 1 FROM dp_pull_report_stage_row stage_row",
            "        WHERE stage_row.task_id = stage.task_id",
            "      )",
            "    ORDER BY task.finished_at ASC, stage.task_id ASC",
            "    LIMIT #{batchSize}",
            "  ) eligible_stage",
            ")",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalStagesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_stage",
            "WHERE task_id IN (SELECT task_id FROM (",
            " SELECT stage.task_id FROM dp_pull_report_stage stage",
            " JOIN dp_pull_task task ON task.id=stage.task_id",
            " WHERE task.state='FAILED' AND task.finished_at IS NOT NULL",
            "  AND task.finished_at &lt; #{cutoffUtc}",
            "  AND stage.gmt_updated &lt; #{cutoffUtc}",
            "  AND task.lease_owner IS NULL AND task.lease_until IS NULL",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_report_stage_row row_item",
            "   WHERE row_item.task_id=stage.task_id)",
            " ORDER BY task.finished_at,stage.task_id LIMIT #{batchSize}",
            ") abandoned_stage)",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedStagesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );
}
