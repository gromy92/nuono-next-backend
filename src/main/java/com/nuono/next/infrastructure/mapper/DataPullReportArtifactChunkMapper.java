package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.report.ReportArtifactChunkAggregate;
import com.nuono.next.datapull.report.ReportArtifactChunkRecord;
import com.nuono.next.datapull.report.ReportArtifactRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Chunked report artifact persistence; no query materializes a complete report. */
public interface DataPullReportArtifactChunkMapper {

    @Insert({
            "INSERT INTO dp_pull_report_artifact (",
            " artifact_key,task_id,stable_request_key,remote_handle,content_sha256,",
            " content_length,content_bytes,created_at,download_state,persisted_chunk_count,updated_at",
            ") VALUES (",
            " #{row.artifactKey},#{row.taskId},#{row.stableRequestKey},#{row.remoteHandle},NULL,",
            " 0,NULL,#{row.createdAt},'DOWNLOADING',0,#{row.updatedAt}",
            ") ON DUPLICATE KEY UPDATE artifact_key=artifact_key"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insertDownloadingIfAbsent(@Param("row") ReportArtifactRecord row);

    @Select({
            "SELECT artifact_key AS artifactKey,task_id AS taskId,",
            " stable_request_key AS stableRequestKey,remote_handle AS remoteHandle,",
            " content_sha256 AS contentSha256,content_length AS contentLength,",
            " download_state AS downloadState,persisted_chunk_count AS persistedChunkCount,",
            " download_fence_epoch AS downloadFenceEpoch,",
            " downloaded_byte_count AS downloadedByteCount,",
            " downloaded_chunk_count AS downloadedChunkCount,",
            " resumable_sha256_state AS resumableSha256State,",
            " expected_content_length AS expectedContentLength,",
            " source_validator AS sourceValidator,",
            " created_at AS createdAt,updated_at AS updatedAt",
            "FROM dp_pull_report_artifact WHERE BINARY artifact_key=BINARY #{artifactKey}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    ReportArtifactRecord selectMetadata(@Param("artifactKey") String artifactKey);

    @Update({
            "UPDATE dp_pull_report_artifact",
            "SET download_fence_epoch=download_fence_epoch+1,updated_at=#{updatedAt}",
            "WHERE BINARY artifact_key=BINARY #{artifactKey}",
            " AND download_state='DOWNLOADING'",
            " AND download_fence_epoch=#{expectedFenceEpoch}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int claimDownloadFence(
            @Param("artifactKey") String artifactKey,
            @Param("expectedFenceEpoch") long expectedFenceEpoch,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Update({
            "UPDATE dp_pull_report_artifact",
            "SET expected_content_length=COALESCE(expected_content_length,#{totalLength}),",
            " source_validator=COALESCE(source_validator,#{validator,jdbcType=VARCHAR}),",
            " updated_at=#{updatedAt}",
            "WHERE BINARY artifact_key=BINARY #{artifactKey}",
            " AND download_state='DOWNLOADING'",
            " AND download_fence_epoch=#{fenceEpoch}",
            " AND downloaded_byte_count=#{responseStart}",
            " AND (expected_content_length IS NULL OR expected_content_length=#{totalLength})",
            " AND (source_validator IS NULL",
            "      OR BINARY source_validator=BINARY #{validator,jdbcType=VARCHAR})"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int bindDownloadResponse(
            @Param("artifactKey") String artifactKey,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("responseStart") long responseStart,
            @Param("totalLength") long totalLength,
            @Param("validator") String validator,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Insert({
            "INSERT INTO dp_pull_report_artifact_chunk (",
            " artifact_key,chunk_no,byte_offset,content_length,content_sha256,content_bytes,created_at",
            ") SELECT",
            " #{row.artifactKey},#{row.chunkNo},#{row.byteOffset},#{row.contentLength},",
            " #{row.contentSha256},#{row.contentBytes},#{row.createdAt}",
            "FROM dp_pull_report_artifact manifest",
            "WHERE BINARY manifest.artifact_key=BINARY #{row.artifactKey}",
            " AND manifest.download_state='DOWNLOADING'",
            " AND manifest.download_fence_epoch=#{fenceEpoch}",
            " AND manifest.downloaded_chunk_count=#{row.chunkNo}",
            " AND manifest.downloaded_byte_count=#{row.byteOffset}",
            "ON DUPLICATE KEY UPDATE",
            " artifact_key=dp_pull_report_artifact_chunk.artifact_key"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insertChunkIfCurrentWriter(
            @Param("row") ReportArtifactChunkRecord row,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Select({
            "SELECT chunk.artifact_key AS artifactKey,chunk.chunk_no AS chunkNo,",
            " chunk.byte_offset AS byteOffset,chunk.content_length AS contentLength,",
            " chunk.content_sha256 AS contentSha256,",
            " chunk.content_bytes AS contentBytes,chunk.created_at AS createdAt",
            "FROM dp_pull_report_artifact_chunk chunk",
            "INNER JOIN dp_pull_report_artifact manifest",
            " ON BINARY manifest.artifact_key=BINARY chunk.artifact_key",
            "WHERE BINARY chunk.artifact_key=BINARY #{artifactKey}",
            " AND chunk.chunk_no=#{chunkNo}",
            " AND manifest.download_state='DOWNLOADING'",
            " AND manifest.download_fence_epoch=#{fenceEpoch}",
            " AND manifest.downloaded_chunk_count=#{chunkNo}",
            " AND manifest.downloaded_byte_count=#{byteOffset}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    ReportArtifactChunkRecord selectCurrentWriterChunk(
            @Param("artifactKey") String artifactKey,
            @Param("chunkNo") int chunkNo,
            @Param("byteOffset") long byteOffset,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Select({
            "SELECT artifact_key AS artifactKey,chunk_no AS chunkNo,byte_offset AS byteOffset,",
            " content_length AS contentLength,content_sha256 AS contentSha256,",
            " content_bytes AS contentBytes,created_at AS createdAt",
            "FROM dp_pull_report_artifact_chunk",
            "WHERE BINARY artifact_key=BINARY #{artifactKey} AND chunk_no=#{chunkNo}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    ReportArtifactChunkRecord selectChunk(
            @Param("artifactKey") String artifactKey,
            @Param("chunkNo") int chunkNo
    );

    @Update({
            "UPDATE dp_pull_report_artifact",
            "SET downloaded_byte_count=#{nextByteOffset},",
            " downloaded_chunk_count=#{nextChunkNo},",
            " resumable_sha256_state=#{resumableSha256State},updated_at=#{updatedAt}",
            "WHERE BINARY artifact_key=BINARY #{artifactKey}",
            " AND download_state='DOWNLOADING'",
            " AND download_fence_epoch=#{fenceEpoch}",
            " AND downloaded_byte_count=#{expectedByteOffset}",
            " AND downloaded_chunk_count=#{expectedChunkNo}",
            " AND expected_content_length IS NOT NULL",
            " AND #{nextByteOffset} &lt;= expected_content_length"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int advanceDownloadProgress(
            @Param("artifactKey") String artifactKey,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("expectedByteOffset") long expectedByteOffset,
            @Param("expectedChunkNo") int expectedChunkNo,
            @Param("nextByteOffset") long nextByteOffset,
            @Param("nextChunkNo") int nextChunkNo,
            @Param("resumableSha256State") String resumableSha256State,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Select({
            "SELECT artifact_key AS artifactKey,chunk_no AS chunkNo,byte_offset AS byteOffset,",
            " content_length AS contentLength,content_sha256 AS contentSha256,",
            " content_bytes AS contentBytes,created_at AS createdAt",
            "FROM dp_pull_report_artifact_chunk",
            "WHERE BINARY artifact_key=BINARY #{artifactKey}",
            " AND byte_offset < #{rangeEnd}",
            " AND byte_offset + content_length &gt; #{rangeStart}",
            "ORDER BY chunk_no LIMIT #{maximumChunks}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<ReportArtifactChunkRecord> selectOverlappingChunks(
            @Param("artifactKey") String artifactKey,
            @Param("rangeStart") long rangeStart,
            @Param("rangeEnd") long rangeEnd,
            @Param("maximumChunks") int maximumChunks
    );

    @Select({
            "SELECT COUNT(*) AS chunkCount,COALESCE(SUM(content_length),0) AS contentLength,",
            " MAX(chunk_no) AS maximumChunkNo",
            "FROM dp_pull_report_artifact_chunk",
            "WHERE BINARY artifact_key=BINARY #{artifactKey}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    ReportArtifactChunkAggregate selectChunkAggregate(@Param("artifactKey") String artifactKey);

    @Update({
            "UPDATE dp_pull_report_artifact manifest",
            "SET content_sha256=#{contentSha256},content_length=#{contentLength},",
            " persisted_chunk_count=#{chunkCount},download_state='COMPLETE',updated_at=#{updatedAt}",
            "WHERE BINARY manifest.artifact_key=BINARY #{artifactKey}",
            " AND manifest.download_state='DOWNLOADING'",
            " AND manifest.download_fence_epoch=#{fenceEpoch}",
            " AND manifest.downloaded_byte_count=#{contentLength}",
            " AND manifest.downloaded_chunk_count=#{chunkCount}",
            " AND manifest.expected_content_length=#{contentLength}",
            " AND BINARY manifest.resumable_sha256_state=BINARY #{resumableSha256State}",
            " AND (SELECT COUNT(*) FROM dp_pull_report_artifact_chunk chunk",
            "      WHERE BINARY chunk.artifact_key=BINARY manifest.artifact_key)=#{chunkCount}",
            " AND (SELECT COALESCE(SUM(chunk.content_length),0)",
            "      FROM dp_pull_report_artifact_chunk chunk",
            "      WHERE BINARY chunk.artifact_key=BINARY manifest.artifact_key)=#{contentLength}",
            " AND (SELECT COALESCE(MAX(chunk.chunk_no),-1)",
            "      FROM dp_pull_report_artifact_chunk chunk",
            "      WHERE BINARY chunk.artifact_key=BINARY manifest.artifact_key)=#{chunkCount}-1"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int completeDownload(
            @Param("artifactKey") String artifactKey,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("contentSha256") String contentSha256,
            @Param("contentLength") long contentLength,
            @Param("chunkCount") int chunkCount,
            @Param("resumableSha256State") String resumableSha256State,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
