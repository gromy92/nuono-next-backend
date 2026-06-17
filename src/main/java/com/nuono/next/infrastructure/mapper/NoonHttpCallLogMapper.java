package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonlog.NoonHttpCallLogRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.IdSequenceCommand;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

public interface NoonHttpCallLogMapper {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    @Select("SELECT COALESCE(MAX(id), 0) FROM noon_http_call_log")
    Long selectMaxLogId();

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, #{minAllocatedId}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = GREATEST(next_id, VALUES(next_id)),",
            "                        gmt_updated = NOW()"
    })
    int ensureSequenceAtLeast(
            @Param("sequenceName") String sequenceName,
            @Param("minAllocatedId") Long minAllocatedId
    );

    default Long nextLogId() {
        Long maxId = selectMaxLogId();
        if (maxId != null && maxId > 520000L) {
            ensureSequenceAtLeast("noon_http_call_log", maxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("noon_http_call_log", 520000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("Noon 调用日志 ID 序列分配失败。");
        }
        return command.getAllocatedId();
    }

    @Insert({
            "INSERT INTO noon_http_call_log (",
            "id, occurred_at, source_module, operation, owner_user_id, store_code, site_code, project_code, partner_id,",
            "business_type, business_id, business_ref, http_method, host, path, query_hash,",
            "request_summary_json, request_hash, response_status_code, response_summary_json, response_hash, elapsed_ms,",
            "status, failure_type, error_message, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.occurredAt}, #{row.sourceModule}, #{row.operation}, #{row.ownerUserId}, #{row.storeCode},",
            "#{row.siteCode}, #{row.projectCode}, #{row.partnerId}, #{row.businessType}, #{row.businessId}, #{row.businessRef},",
            "#{row.httpMethod}, #{row.host}, #{row.path}, #{row.queryHash}, #{row.requestSummaryJson}, #{row.requestHash},",
            "#{row.responseStatusCode}, #{row.responseSummaryJson}, #{row.responseHash}, #{row.elapsedMs}, #{row.status},",
            "#{row.failureType}, #{row.errorMessage}, b'0', #{row.ownerUserId}, #{row.ownerUserId}, NOW(), NOW())"
    })
    int insert(@Param("row") NoonHttpCallLogRecord row);

    @Select({
            "<script>",
            "SELECT id, occurred_at AS occurredAt, source_module AS sourceModule, operation, owner_user_id AS ownerUserId,",
            "       store_code AS storeCode, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       business_type AS businessType, business_id AS businessId, business_ref AS businessRef,",
            "       http_method AS httpMethod, host, path, response_status_code AS responseStatusCode,",
            "       elapsed_ms AS elapsedMs, status, failure_type AS failureType, error_message AS errorMessage,",
            "       request_summary_json AS requestSummaryJson, response_summary_json AS responseSummaryJson",
            "FROM noon_http_call_log",
            "WHERE is_deleted = b'0'",
            "<if test='businessType != null and businessType != \"\"'>",
            "  AND business_type = #{businessType}",
            "</if>",
            "<if test='businessId != null and businessId != \"\"'>",
            "  AND business_id = #{businessId}",
            "</if>",
            "<if test='businessRef != null and businessRef != \"\"'>",
            "  AND business_ref = #{businessRef}",
            "</if>",
            "ORDER BY occurred_at DESC, id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<NoonHttpCallLogRecord> listRecent(
            @Param("businessType") String businessType,
            @Param("businessId") String businessId,
            @Param("businessRef") String businessRef,
            @Param("limit") int limit
    );
}
