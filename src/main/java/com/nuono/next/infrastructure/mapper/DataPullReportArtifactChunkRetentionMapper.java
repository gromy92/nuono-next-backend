package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

/** Deletes report bytes in small child-first batches before the manifest can be removed. */
public interface DataPullReportArtifactChunkRetentionMapper {

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_artifact_chunk",
            "WHERE (artifact_key,chunk_no) IN (SELECT artifact_key,chunk_no FROM (",
            " SELECT chunk.artifact_key,chunk.chunk_no",
            " FROM dp_pull_report_artifact_chunk chunk",
            " INNER JOIN dp_pull_report_artifact artifact",
            "  ON BINARY artifact.artifact_key=BINARY chunk.artifact_key",
            " INNER JOIN dp_pull_task task ON task.id=artifact.task_id",
            " WHERE task.state IN ('SUCCEEDED','SUPERSEDED')",
            "  AND task.finished_at IS NOT NULL AND task.finished_at &lt; #{cutoffUtc}",
            "  AND artifact.created_at &lt; #{cutoffUtc}",
            "  AND task.lease_owner IS NULL AND task.lease_until IS NULL",
            "  AND (task.state='SUPERSEDED' OR EXISTS (",
            "   SELECT 1 FROM dp_pull_report_apply applied WHERE applied.task_id=task.id",
            "  ))",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_report_stage stage",
            "   WHERE BINARY stage.artifact_key=BINARY artifact.artifact_key)",
            " ORDER BY task.finished_at ASC,artifact.created_at ASC,",
            "  artifact.artifact_key ASC,chunk.chunk_no ASC",
            " LIMIT #{batchSize}",
            ") eligible_chunk)",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_artifact_chunk",
            "WHERE (artifact_key,chunk_no) IN (SELECT artifact_key,chunk_no FROM (",
            " SELECT chunk.artifact_key,chunk.chunk_no",
            " FROM dp_pull_report_artifact_chunk chunk",
            " INNER JOIN dp_pull_report_artifact artifact",
            "  ON BINARY artifact.artifact_key=BINARY chunk.artifact_key",
            " INNER JOIN dp_pull_task task ON task.id=artifact.task_id",
            " WHERE task.state='FAILED' AND task.finished_at IS NOT NULL",
            "  AND task.finished_at &lt; #{cutoffUtc}",
            "  AND artifact.created_at &lt; #{cutoffUtc}",
            "  AND task.lease_owner IS NULL AND task.lease_until IS NULL",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_report_stage stage",
            "   WHERE BINARY stage.artifact_key=BINARY artifact.artifact_key)",
            " ORDER BY task.finished_at ASC,artifact.created_at ASC,",
            "  artifact.artifact_key ASC,chunk.chunk_no ASC",
            " LIMIT #{batchSize}",
            ") eligible_chunk)",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );
}
