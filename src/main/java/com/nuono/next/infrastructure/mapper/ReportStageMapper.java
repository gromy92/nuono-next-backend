package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.report.ExportReportIntent;
import com.nuono.next.datapull.report.ReportStageApplySlice;
import com.nuono.next.datapull.report.ReportStageRowRecord;
import com.nuono.next.datapull.report.ReportStageState;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Lifecycle mapper for one immutable, bounded report stage. */
public interface ReportStageMapper {
    String STAGE_COLUMNS = "task_id AS taskId,operation_code AS operationCode,"
            + "artifact_key AS artifactKey,artifact_sha256 AS artifactSha256,"
            + "active_fence_epoch AS activeFenceEpoch,state,header_json AS headerJson,"
            + "next_byte_offset AS nextByteOffset,declared_row_count AS declaredRowCount,"
            + "source_row_count AS sourceRowCount,accepted_row_count AS acceptedRowCount,"
            + "business_skipped_row_count AS businessSkippedRowCount,"
            + "identity_skipped_row_count AS identitySkippedRowCount,"
            + "apply_row_cursor AS applyRowCursor,applied_row_count AS appliedRowCount,"
            + "applied_warning_count AS appliedWarningCount,fact_container_id AS factContainerId,"
            + "poison_code AS poisonCode,version_no AS versionNo";

    @Select("SELECT " + STAGE_COLUMNS + " FROM dp_pull_report_stage WHERE task_id=#{taskId}")
    ReportStageState selectStage(@Param("taskId") long taskId);

    @Select("SELECT " + STAGE_COLUMNS
            + " FROM dp_pull_report_stage WHERE task_id=#{taskId} FOR UPDATE")
    ReportStageState selectStageForUpdate(@Param("taskId") long taskId);

    @Insert({
            "INSERT INTO dp_pull_report_stage(task_id,operation_code,artifact_key,artifact_sha256,",
            "active_fence_epoch,state,header_json,next_byte_offset,declared_row_count,source_row_count,",
            "accepted_row_count,business_skipped_row_count,identity_skipped_row_count,apply_row_cursor,",
            "applied_row_count,applied_warning_count,fact_container_id,poison_code,version_no,gmt_create,gmt_updated)",
            "VALUES(#{intent.taskId},#{intent.operationCode},#{artifactKey},#{artifactSha256},",
            "#{intent.fenceEpoch},'VALIDATING',#{headerJson},#{initialByteOffset},#{declaredRowCount},",
            "0,0,0,0,0,0,0,NULL,NULL,0,#{nowUtc},#{nowUtc})",
            "ON DUPLICATE KEY UPDATE task_id=task_id"
    })
    int insertStageIfAbsent(
            @Param("intent") ExportReportIntent intent,
            @Param("artifactKey") String artifactKey,
            @Param("artifactSha256") String artifactSha256,
            @Param("headerJson") String headerJson,
            @Param("initialByteOffset") long initialByteOffset,
            @Param("declaredRowCount") long declaredRowCount,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Update({
            "UPDATE dp_pull_report_stage SET artifact_key=#{artifactKey},",
            "artifact_sha256=#{artifactSha256},active_fence_epoch=#{intent.fenceEpoch},",
            "state='VALIDATING',header_json=#{headerJson},next_byte_offset=#{initialByteOffset},",
            "declared_row_count=#{declaredRowCount},version_no=version_no+1,gmt_updated=#{nowUtc}",
            "WHERE task_id=#{intent.taskId} AND operation_code=#{intent.operationCode}",
            "AND BINARY artifact_key=BINARY #{oldArtifactKey}",
            "AND artifact_sha256=#{oldArtifactSha256} AND state='EMPTY_UNPROVEN'",
            "AND source_row_count=0 AND accepted_row_count=0",
            "AND business_skipped_row_count=0 AND identity_skipped_row_count=0",
            "AND apply_row_cursor=0 AND applied_row_count=0 AND applied_warning_count=0"
    })
    int rebindUnprovenEmpty(
            @Param("intent") ExportReportIntent intent,
            @Param("oldArtifactKey") String oldArtifactKey,
            @Param("oldArtifactSha256") String oldArtifactSha256,
            @Param("artifactKey") String artifactKey,
            @Param("artifactSha256") String artifactSha256,
            @Param("headerJson") String headerJson,
            @Param("initialByteOffset") long initialByteOffset,
            @Param("declaredRowCount") long declaredRowCount,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "<script>SELECT accepted_identity_sha256 FROM dp_pull_report_stage_row",
            "WHERE task_id=#{taskId} AND accepted_identity_sha256 IN",
            "<foreach item='digest' collection='digests' open='(' separator=',' close=')'>",
            "#{digest}</foreach> FOR UPDATE</script>"
    })
    List<String> selectExistingAcceptedIdentities(
            @Param("taskId") long taskId,
            @Param("digests") List<String> digests
    );

    @Insert({
            "<script>INSERT INTO dp_pull_report_stage_row(task_id,`row_number`,decision,identity_sha256,",
            "accepted_identity_sha256,payload_json,gmt_create) VALUES",
            "<foreach item='row' collection='rows' separator=','>",
            "(#{row.taskId},#{row.rowNumber},#{row.decision},#{row.identitySha256},",
            "#{row.acceptedIdentitySha256},#{row.payloadJson},#{nowUtc})</foreach></script>"
    })
    int insertStageRows(
            @Param("rows") List<ReportStageRowRecord> rows,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Update({
            "UPDATE dp_pull_report_stage SET active_fence_epoch=#{intent.fenceEpoch},state=#{state},",
            "next_byte_offset=#{nextByteOffset},source_row_count=#{sourceRows},",
            "declared_row_count=IF(#{finalizeLocalRowCount},#{sourceRows},declared_row_count),",
            "accepted_row_count=#{acceptedRows},business_skipped_row_count=#{businessSkippedRows},",
            "identity_skipped_row_count=#{identitySkippedRows},version_no=version_no+1,gmt_updated=#{nowUtc}",
            "WHERE task_id=#{intent.taskId} AND operation_code=#{intent.operationCode}",
            "AND BINARY artifact_key=BINARY #{artifactKey} AND artifact_sha256=#{artifactSha256}",
            "AND next_byte_offset=#{expectedByteOffset} AND state='VALIDATING'"
    })
    int advanceStage(
            @Param("intent") ExportReportIntent intent,
            @Param("artifactKey") String artifactKey,
            @Param("artifactSha256") String artifactSha256,
            @Param("expectedByteOffset") long expectedByteOffset,
            @Param("nextByteOffset") long nextByteOffset,
            @Param("sourceRows") long sourceRows,
            @Param("acceptedRows") long acceptedRows,
            @Param("businessSkippedRows") long businessSkippedRows,
            @Param("identitySkippedRows") long identitySkippedRows,
            @Param("state") String state,
            @Param("nowUtc") LocalDateTime nowUtc,
            @Param("finalizeLocalRowCount") boolean finalizeLocalRowCount
    );

    @Update({
            "UPDATE dp_pull_report_stage SET active_fence_epoch=#{intent.fenceEpoch},state='POISONED',",
            "poison_code=#{poisonCode},version_no=version_no+1,gmt_updated=#{nowUtc}",
            "WHERE task_id=#{intent.taskId} AND operation_code=#{intent.operationCode}",
            "AND state IN ('VALIDATING','POISONED')"
    })
    int poisonStage(
            @Param("intent") ExportReportIntent intent,
            @Param("poisonCode") String poisonCode,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT COUNT(*) AS rowCount,MAX(next_rows.`row_number`) AS lastRowNumber FROM (",
            "SELECT `row_number` FROM dp_pull_report_stage_row",
            "WHERE task_id=#{taskId} AND decision='ACCEPTED' AND `row_number`>#{afterRowNumber}",
            "ORDER BY `row_number` LIMIT #{limitRows}) next_rows"
    })
    ReportStageApplySlice selectNextApplySlice(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("limitRows") int limitRows
    );

    @Update({
            "UPDATE dp_pull_report_stage SET apply_row_cursor=#{nextRowCursor},",
            "applied_row_count=#{nextAppliedCount},applied_warning_count=#{nextWarningCount},",
            "active_fence_epoch=#{intent.fenceEpoch},version_no=version_no+1,gmt_updated=#{nowUtc}",
            "WHERE task_id=#{intent.taskId} AND operation_code=#{intent.operationCode} AND state='SEALED'",
            "AND apply_row_cursor=#{expectedRowCursor} AND applied_row_count=#{expectedAppliedCount}",
            "AND applied_warning_count=#{expectedWarningCount}"
    })
    int advanceApplyCursor(
            @Param("intent") ExportReportIntent intent,
            @Param("expectedRowCursor") long expectedRowCursor,
            @Param("nextRowCursor") long nextRowCursor,
            @Param("expectedAppliedCount") long expectedAppliedCount,
            @Param("nextAppliedCount") long nextAppliedCount,
            @Param("expectedWarningCount") long expectedWarningCount,
            @Param("nextWarningCount") long nextWarningCount,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Update({
            "UPDATE dp_pull_report_stage SET state='APPLIED',active_fence_epoch=#{intent.fenceEpoch},",
            "version_no=version_no+1,gmt_updated=#{nowUtc}",
            "WHERE task_id=#{intent.taskId} AND operation_code=#{intent.operationCode}",
            "AND state='SEALED' AND applied_row_count=accepted_row_count"
    })
    int markApplied(
            @Param("intent") ExportReportIntent intent,
            @Param("nowUtc") LocalDateTime nowUtc
    );
}
