package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.report.ReportArtifactRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Persistence Adapter for restart-safe report bytes. */
public interface DataPullReportArtifactMapper {

    @Insert({
            "INSERT INTO dp_pull_report_artifact (",
            "  artifact_key, task_id, stable_request_key, remote_handle, content_sha256,",
            "  content_length, content_bytes, created_at",
            ") VALUES (",
            "  #{row.artifactKey}, #{row.taskId}, #{row.stableRequestKey}, #{row.remoteHandle},",
            "  #{row.contentSha256}, #{row.contentLength}, #{row.contentBytes}, #{row.createdAt}",
            ") ON DUPLICATE KEY UPDATE artifact_key = artifact_key"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insertIfAbsent(@Param("row") ReportArtifactRecord row);

    @Select({
            "SELECT artifact_key AS artifactKey, task_id AS taskId,",
            "       stable_request_key AS stableRequestKey,",
            "       remote_handle AS remoteHandle, content_sha256 AS contentSha256,",
            "       content_length AS contentLength, content_bytes AS contentBytes,",
            "       created_at AS createdAt",
            "FROM dp_pull_report_artifact",
            "WHERE BINARY artifact_key = BINARY #{artifactKey}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    ReportArtifactRecord selectByKey(@Param("artifactKey") String artifactKey);

    @Select({
            "SELECT artifact_key AS artifactKey, task_id AS taskId,",
            "       stable_request_key AS stableRequestKey, remote_handle AS remoteHandle,",
            "       content_sha256 AS contentSha256, content_length AS contentLength,",
            "       created_at AS createdAt",
            "FROM dp_pull_report_artifact",
            "WHERE BINARY artifact_key = BINARY #{artifactKey}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    ReportArtifactRecord selectMetadataByKey(@Param("artifactKey") String artifactKey);

    @Select({
            "SELECT artifact_key AS artifactKey, task_id AS taskId,",
            "       stable_request_key AS stableRequestKey, remote_handle AS remoteHandle,",
            "       content_sha256 AS contentSha256, content_length AS contentLength,",
            "       LOWER(SHA2(content_bytes, 256)) AS databaseContentSha256,",
            "       created_at AS createdAt",
            "FROM dp_pull_report_artifact",
            "WHERE BINARY artifact_key = BINARY #{artifactKey}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    ReportArtifactRecord selectVerifiedMetadataByKey(@Param("artifactKey") String artifactKey);

    @Select({
            "SELECT SUBSTRING(content_bytes, #{byteOffset} + 1, #{maxBytes})",
            "FROM dp_pull_report_artifact",
            "WHERE BINARY artifact_key = BINARY #{artifactKey}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    byte[] selectContentSlice(
            @Param("artifactKey") String artifactKey,
            @Param("byteOffset") long byteOffset,
            @Param("maxBytes") int maxBytes
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_artifact",
            "WHERE artifact_key IN (",
            "  SELECT artifact_key FROM (",
            "    SELECT artifact.artifact_key",
            "    FROM dp_pull_report_artifact artifact",
            "    INNER JOIN dp_pull_task task ON task.id = artifact.task_id",
            "    WHERE task.state IN ('SUCCEEDED', 'SUPERSEDED')",
            "      AND task.finished_at IS NOT NULL",
            "      AND task.finished_at &lt; #{cutoffUtc}",
            "      AND artifact.created_at &lt; #{cutoffUtc}",
            "      AND task.lease_owner IS NULL",
            "      AND task.lease_until IS NULL",
            "      AND (task.state = 'SUPERSEDED' OR EXISTS (",
            "        SELECT 1 FROM dp_pull_report_apply applied",
            "        WHERE applied.task_id = task.id",
            "      ))",
            "      AND NOT EXISTS (",
            "        SELECT 1 FROM dp_pull_report_stage stage",
            "        WHERE BINARY stage.artifact_key=BINARY artifact.artifact_key",
            "      )",
            "      AND NOT EXISTS (",
            "        SELECT 1 FROM dp_pull_report_artifact_chunk chunk",
            "        WHERE BINARY chunk.artifact_key=BINARY artifact.artifact_key",
            "      )",
            "    ORDER BY task.finished_at ASC, artifact.created_at ASC,",
            "             artifact.artifact_key ASC",
            "    LIMIT #{batchSize}",
            "  ) eligible_artifact",
            ")",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteTerminalBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );

    @Delete({
            "<script>",
            "DELETE FROM dp_pull_report_artifact",
            "WHERE artifact_key IN (SELECT artifact_key FROM (",
            " SELECT artifact.artifact_key FROM dp_pull_report_artifact artifact",
            " JOIN dp_pull_task task ON task.id=artifact.task_id",
            " WHERE task.state='FAILED' AND task.finished_at IS NOT NULL",
            "  AND task.finished_at &lt; #{cutoffUtc}",
            "  AND artifact.created_at &lt; #{cutoffUtc}",
            "  AND task.lease_owner IS NULL AND task.lease_until IS NULL",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_report_stage stage",
            "   WHERE BINARY stage.artifact_key=BINARY artifact.artifact_key)",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_report_artifact_chunk chunk",
            "   WHERE BINARY chunk.artifact_key=BINARY artifact.artifact_key)",
            " ORDER BY task.finished_at,artifact.created_at,artifact.artifact_key",
            " LIMIT #{batchSize}",
            ") abandoned_artifact)",
            "</script>"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("batchSize") int batchSize
    );
}
