package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonpull.NoonProductionSchedulerEnablementRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonProductionSchedulerEnablementMapper {

    @Update({
            "CREATE TABLE IF NOT EXISTS `noon_pull_id_sequence` (",
            "  `sequence_name` VARCHAR(100) NOT NULL,",
            "  `next_id` BIGINT NOT NULL,",
            "  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,",
            "  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,",
            "  PRIMARY KEY (`sequence_name`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    })
    void ensureNoonPullIdSequence();

    @Update({
            "CREATE TABLE IF NOT EXISTS `noon_production_scheduler_enablement` (",
            "  `id` BIGINT NOT NULL,",
            "  `target_environment` VARCHAR(64) NOT NULL,",
            "  `owner_user_id` BIGINT DEFAULT NULL,",
            "  `project_code` VARCHAR(100) DEFAULT NULL,",
            "  `project_name` VARCHAR(255) DEFAULT NULL,",
            "  `store_code` VARCHAR(100) DEFAULT NULL,",
            "  `site_code` VARCHAR(32) DEFAULT NULL,",
            "  `enabled_domains` VARCHAR(255) DEFAULT NULL,",
            "  `schedule_boundaries` VARCHAR(1000) DEFAULT NULL,",
            "  `rollback_global_pause_strategy` VARCHAR(1000) DEFAULT NULL,",
            "  `operator_user_id` BIGINT DEFAULT NULL,",
            "  `smoke_run_id` BIGINT DEFAULT NULL,",
            "  `decision` VARCHAR(32) NOT NULL,",
            "  `rejection_reasons` VARCHAR(1000) DEFAULT NULL,",
            "  `plan_ids` VARCHAR(500) DEFAULT NULL,",
            "  `hitl_approved` BIT(1) NOT NULL DEFAULT b'0',",
            "  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',",
            "  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,",
            "  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,",
            "  PRIMARY KEY (`id`),",
            "  KEY `idx_noon_scheduler_enablement_scope` (`target_environment`, `owner_user_id`, `store_code`, `site_code`),",
            "  KEY `idx_noon_scheduler_enablement_smoke` (`smoke_run_id`),",
            "  KEY `idx_noon_scheduler_enablement_decision` (`decision`, `gmt_create`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    })
    void ensureEnablementTable();

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
