package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitBatchRecords.ImportBatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.OperationAuditRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InTransitImportAuditMapper extends InTransitGoodsSequenceMapper {

    @Insert({
            "INSERT INTO in_transit_import_batch (",
            "id, owner_user_id, file_name, source_type, status, total_row_count, valid_row_count, error_count, warning_count,",
            "summary_json, raw_preview_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.fileName}, #{row.sourceType}, #{row.status}, #{row.totalRowCount},",
            "#{row.validRowCount}, #{row.errorCount}, #{row.warningCount}, #{row.summaryJson}, #{row.rawPreviewJson},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertImportBatch(@Param("row") ImportBatchRow row);

    @Select({
            "SELECT id, owner_user_id, file_name, source_type, status, total_row_count, valid_row_count, error_count, warning_count,",
            "summary_json, raw_preview_json, created_by, updated_by",
            "FROM in_transit_import_batch",
            "WHERE owner_user_id = #{ownerUserId} AND id = #{importBatchId} AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ImportBatchRow selectImportBatchById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("importBatchId") Long importBatchId
    );

    @Update({
            "UPDATE in_transit_import_batch",
            "SET status = 'imported', summary_json = #{summaryJson}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND id = #{importBatchId} AND is_deleted = b'0'"
    })
    int markImportBatchImported(
            @Param("ownerUserId") Long ownerUserId,
            @Param("importBatchId") Long importBatchId,
            @Param("operatorUserId") Long operatorUserId,
            @Param("summaryJson") String summaryJson
    );

    @Insert({
            "INSERT INTO in_transit_operation_audit (",
            "id, owner_user_id, operator_user_id, operation_type, target_type, target_id, batch_id,",
            "store_code, site_code, summary, detail_json, created_by, gmt_create",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.operatorUserId}, #{row.operationType}, #{row.targetType}, #{row.targetId}, #{row.batchId},",
            "#{row.storeCode}, #{row.siteCode}, #{row.summary}, #{row.detailJson}, #{row.createdBy}, NOW())"
    })
    int insertOperationAudit(@Param("row") OperationAuditRow row);
}
