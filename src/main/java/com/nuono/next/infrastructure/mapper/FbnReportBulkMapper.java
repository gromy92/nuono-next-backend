package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.report.ExportReportIntent;
import com.nuono.next.datapull.report.FbnReportChunkProof;
import com.nuono.next.datapull.report.ReportFactIdBlock;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

/** DP-07-B bounded multi-table Fact Writer mapper. */
public interface FbnReportBulkMapper {
    @Insert({
            "INSERT INTO product_management_id_sequence(sequence_name,next_id,gmt_create,gmt_updated)",
            "VALUES(#{sequenceName},LAST_INSERT_ID(#{initialValue}+#{blockSize}),NOW(),NOW())",
            "ON DUPLICATE KEY UPDATE next_id=LAST_INSERT_ID(next_id+#{blockSize}),gmt_updated=NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "lastId",
            before = false, resultType = Long.class)
    void reserveIds(ReportFactIdBlock block);

    @Update({
            "UPDATE dp_pull_report_stage SET fact_container_id=#{containerId},",
            " active_fence_epoch=#{intent.fenceEpoch},version_no=version_no+1,gmt_updated=#{nowUtc}",
            "WHERE task_id=#{intent.taskId} AND operation_code='DP07B' AND state='SEALED'",
            " AND fact_container_id IS NULL"
    })
    int initializeContainerId(
            @Param("intent") ExportReportIntent intent,
            @Param("containerId") long containerId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @InsertProvider(type = FbnReportApplySql.class, method = "insertReportRows")
    @Options(timeout = 10)
    int insertReportRows(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber,
            @Param("importId") long importId,
            @Param("firstRowId") long firstRowId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @InsertProvider(type = FbnReportApplySql.class, method = "insertReceiptLines")
    @Options(timeout = 10)
    int insertReceiptLines(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber,
            @Param("importId") long importId,
            @Param("firstRowId") long firstRowId,
            @Param("firstReceiptId") long firstReceiptId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT COUNT(DISTINCT report_row.id) AS reportRows,",
            " COUNT(DISTINCT receipt.id) AS receiptRows,",
            " COALESCE(SUM(receipt.receipt_status<>'NORMAL'),0) AS warningRows",
            "FROM official_warehouse_report_row report_row",
            "LEFT JOIN official_warehouse_inbound_receipt_line receipt",
            " ON receipt.report_row_id=report_row.id AND receipt.import_id=report_row.import_id",
            "WHERE report_row.import_id=#{importId} AND report_row.row_no>#{afterRowNumber}",
            " AND report_row.row_no<=#{throughRowNumber} AND report_row.is_deleted=b'0'"
    })
    FbnReportChunkProof selectChunkProof(
            @Param("importId") long importId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber
    );

    @Update({
            "UPDATE official_warehouse_report_import old_import",
            "JOIN dp_pull_task task ON task.id=#{taskId}",
            "JOIN dp_pull_report_stage stage ON stage.task_id=task.id",
            "JOIN dp_pull_report_artifact artifact ON BINARY artifact.artifact_key=BINARY stage.artifact_key",
            "SET old_import.is_deleted=b'1',old_import.updated_by=task.owner_user_id,old_import.gmt_updated=#{nowUtc}",
            "WHERE old_import.id<>#{importId} AND old_import.is_deleted=b'0'",
            " AND old_import.owner_user_id=task.owner_user_id",
            " AND BINARY old_import.store_code=BINARY task.store_code",
            " AND UPPER(old_import.site_code)=UPPER(task.site_code)",
            " AND old_import.report_type='FBN_INBOUND_FBNRECEIVEDREPORT'",
            " AND BINARY old_import.source_export_code=BINARY LEFT(artifact.remote_handle,120)"
    })
    int deactivatePreviousImportHeaders(
            @Param("taskId") long taskId,
            @Param("importId") long importId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Insert({
            "INSERT INTO official_warehouse_report_import(id,owner_user_id,logical_store_id,store_code,",
            "site_code,project_code,partner_id,report_type,source_type,source_export_code,file_name,file_sha256,",
            "snapshot_at,business_date_start,business_date_end,total_rows,valid_rows,warning_rows,error_rows,status,",
            "summary_json,raw_preview_json,is_deleted,created_by,updated_by,gmt_create,gmt_updated)",
            "SELECT #{importId},task.owner_user_id,task.logical_store_id,task.store_code,task.site_code,task.project_code,",
            "NULL,'FBN_INBOUND_FBNRECEIVEDREPORT','FBN_REPORT_EXPORT_API',",
            "LEFT(artifact.remote_handle,120),CONCAT('dp07b-',LEFT(stage.artifact_key,80),'.csv'),",
            "stage.artifact_sha256,#{nowUtc},",
            "(SELECT MIN(receipt.asn_schedule_date) FROM official_warehouse_inbound_receipt_line receipt",
            " WHERE receipt.import_id=#{importId} AND receipt.is_deleted=b'0'),",
            "(SELECT MAX(receipt.asn_schedule_date) FROM official_warehouse_inbound_receipt_line receipt",
            " WHERE receipt.import_id=#{importId} AND receipt.is_deleted=b'0'),",
            "stage.accepted_row_count+stage.identity_skipped_row_count,",
            "stage.applied_row_count,stage.applied_warning_count,0,'IMPORTED',",
            "JSON_OBJECT('sourceType','FBN_REPORT_EXPORT_API','providerRows',stage.declared_row_count,",
            " 'parsedRows',stage.accepted_row_count+stage.identity_skipped_row_count,",
            " 'insertedReceiptLines',stage.applied_row_count,'warningRows',stage.applied_warning_count,",
            " 'businessSkippedRows',stage.business_skipped_row_count,'identitySkippedRows',stage.identity_skipped_row_count),",
            "JSON_ARRAY(),b'0',task.owner_user_id,task.owner_user_id,#{nowUtc},#{nowUtc}",
            "FROM dp_pull_task task JOIN dp_pull_report_stage stage ON stage.task_id=task.id",
            "JOIN dp_pull_report_artifact artifact ON BINARY artifact.artifact_key=BINARY stage.artifact_key",
            "WHERE task.id=#{taskId} AND stage.fact_container_id=#{importId}",
            " AND stage.applied_row_count=stage.accepted_row_count"
    })
    @Options(timeout = 10)
    int insertImportHeader(
            @Param("taskId") long taskId,
            @Param("importId") long importId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT COUNT(*) FROM official_warehouse_report_import",
            "WHERE id=#{importId} AND report_type='FBN_INBOUND_FBNRECEIVEDREPORT' AND is_deleted=b'0'"
    })
    long countActiveImportHeader(@Param("importId") long importId);
}
