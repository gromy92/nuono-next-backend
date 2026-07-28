package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonpull.NoonProductionSchedulerEnablementRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

public interface NoonProductionSchedulerEnablementMapper {

    @Insert({
            "INSERT INTO noon_pull_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue}), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    void nextId(IdSequenceCommand command);

    @Insert({
            "INSERT INTO noon_production_scheduler_enablement (",
            "  id, target_environment, owner_user_id, project_code, project_name, store_code, site_code,",
            "  enabled_domains, schedule_boundaries, rollback_global_pause_strategy, operator_user_id,",
            "  smoke_run_id, decision, rejection_reasons, plan_ids, hitl_approved, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{targetEnvironment}, #{ownerUserId}, #{projectCode}, #{projectName}, #{storeCode}, #{siteCode},",
            "  #{enabledDomainsText}, #{scheduleBoundaries}, #{rollbackOrGlobalPauseStrategy}, #{operatorUserId},",
            "  #{smokeRunId}, #{decision}, #{rejectionReasonsText}, #{planIdsText}, #{hitlApproved},",
            "  #{createdAt}, #{updatedAt}",
            ")"
    })
    void insertRecord(NoonProductionSchedulerEnablementRecord record);

    @Select({
            "SELECT",
            "  id, target_environment, owner_user_id, project_code, project_name, store_code, site_code,",
            "  enabled_domains AS enabled_domains_text,",
            "  schedule_boundaries,",
            "  rollback_global_pause_strategy AS rollback_or_global_pause_strategy,",
            "  operator_user_id, smoke_run_id, decision,",
            "  rejection_reasons AS rejection_reasons_text,",
            "  plan_ids AS plan_ids_text,",
            "  hitl_approved,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_production_scheduler_enablement",
            "WHERE is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT #{limit}"
    })
    List<NoonProductionSchedulerEnablementRecord> selectRecent(@Param("limit") int limit);
}
