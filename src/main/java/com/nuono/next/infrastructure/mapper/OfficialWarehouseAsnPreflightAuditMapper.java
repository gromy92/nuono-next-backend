package com.nuono.next.infrastructure.mapper;

import com.nuono.next.officialwarehouse.OfficialWarehouseAsnPreflightAuditRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.IdSequenceCommand;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

interface OfficialWarehouseAsnPreflightAuditMapper {
    @Select("SELECT COALESCE(MAX(id), 0) FROM official_warehouse_asn_preflight_audit")
    Long selectMaxAsnPreflightAuditId();

    int allocateId(IdSequenceCommand command);

    int ensureSequenceAtLeast(String sequenceName, Long minAllocatedId);

    default Long nextAsnPreflightAuditId() {
        Long tableMaxId = selectMaxAsnPreflightAuditId();
        if (tableMaxId != null && tableMaxId > 630000L) {
            ensureSequenceAtLeast("official_warehouse_asn_preflight_audit", tableMaxId);
        }
        IdSequenceCommand command = new IdSequenceCommand("official_warehouse_asn_preflight_audit", 630000L);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("官方仓 ID 序列分配失败：official_warehouse_asn_preflight_audit");
        }
        return command.getAllocatedId();
    }

    @Insert({
            "INSERT INTO official_warehouse_asn_preflight_audit (",
            "id, owner_user_id, operator_user_id, logical_store_id, project_code, store_code, site_code, partner_id,",
            "attempt_asn_id, attempt_ref, operation, request_line_count, invalid_line_count, failure_code,",
            "failure_message, reason_summary, invalid_lines_json, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.operatorUserId}, #{row.logicalStoreId}, #{row.projectCode},",
            "#{row.storeCode}, #{row.siteCode}, #{row.partnerId}, #{row.attemptAsnId}, #{row.attemptRef},",
            "#{row.operation}, #{row.requestLineCount}, #{row.invalidLineCount}, #{row.failureCode},",
            "#{row.failureMessage}, #{row.reasonSummary}, #{row.invalidLinesJson}, #{row.operatorUserId},",
            "#{row.operatorUserId}, NOW(), NOW())"
    })
    int insertAsnPreflightAudit(@Param("row") OfficialWarehouseAsnPreflightAuditRecord row);
}
