package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonpull.NoonPullSmokeEvidenceRecord;
import com.nuono.next.noonpull.NoonPullSmokeRunRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonPullSmokeRunMapper {

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
            "CREATE TABLE IF NOT EXISTS `noon_pull_smoke_run` (",
            "  `id` BIGINT NOT NULL,",
            "  `target_environment` VARCHAR(64) NOT NULL,",
            "  `owner_user_id` BIGINT NOT NULL,",
            "  `project_code` VARCHAR(100) DEFAULT NULL,",
            "  `project_name` VARCHAR(255) DEFAULT NULL,",
            "  `store_code` VARCHAR(100) DEFAULT NULL,",
            "  `site_code` VARCHAR(32) DEFAULT NULL,",
            "  `rollback_global_pause_strategy` VARCHAR(1000) DEFAULT NULL,",
            "  `requested_domains` VARCHAR(255) DEFAULT NULL,",
            "  `missing_requirements` VARCHAR(1000) DEFAULT NULL,",
            "  `evidence_gate_satisfied` BIT(1) NOT NULL DEFAULT b'0',",
            "  `production_scheduling_allowed` BIT(1) NOT NULL DEFAULT b'0',",
            "  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',",
            "  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,",
            "  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,",
            "  PRIMARY KEY (`id`),",
            "  KEY `idx_noon_pull_smoke_run_scope` (`target_environment`, `owner_user_id`, `store_code`, `site_code`),",
            "  KEY `idx_noon_pull_smoke_run_created` (`gmt_create`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    })
    void ensureSmokeRunTable();

    @Update({
            "CREATE TABLE IF NOT EXISTS `noon_pull_smoke_evidence` (",
            "  `id` BIGINT NOT NULL,",
            "  `run_id` BIGINT NOT NULL,",
            "  `sequence_no` INT NOT NULL,",
            "  `data_domain` VARCHAR(32) NOT NULL,",
            "  `target_identity` VARCHAR(255) DEFAULT NULL,",
            "  `date_from` DATE DEFAULT NULL,",
            "  `date_to` DATE DEFAULT NULL,",
            "  `row_or_item_count` INT DEFAULT NULL,",
            "  `task_id` BIGINT DEFAULT NULL,",
            "  `source_batch_id` VARCHAR(160) DEFAULT NULL,",
            "  `file_digest_sha256` VARCHAR(128) DEFAULT NULL,",
            "  `request_count` INT DEFAULT NULL,",
            "  `elapsed_millis` BIGINT DEFAULT NULL,",
            "  `latest_fact_date` DATE DEFAULT NULL,",
            "  `status` VARCHAR(32) DEFAULT NULL,",
            "  `quality_state` VARCHAR(64) DEFAULT NULL,",
            "  `failure_classification` VARCHAR(80) DEFAULT NULL,",
            "  `is_deleted` BIT(1) NOT NULL DEFAULT b'0',",
            "  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,",
            "  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,",
            "  PRIMARY KEY (`id`),",
            "  KEY `idx_noon_pull_smoke_evidence_run` (`run_id`, `sequence_no`),",
            "  KEY `idx_noon_pull_smoke_evidence_task` (`task_id`),",
            "  KEY `idx_noon_pull_smoke_evidence_batch` (`source_batch_id`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    })
    void ensureSmokeEvidenceTable();

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
            "INSERT INTO noon_pull_smoke_run (",
            "  id, target_environment, owner_user_id, project_code, project_name, store_code, site_code,",
            "  rollback_global_pause_strategy, requested_domains, missing_requirements,",
            "  evidence_gate_satisfied, production_scheduling_allowed, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{targetEnvironment}, #{ownerUserId}, #{projectCode}, #{projectName}, #{storeCode}, #{siteCode},",
            "  #{rollbackOrGlobalPauseStrategy}, #{requestedDataDomainsText}, #{missingRequirementsText},",
            "  #{evidenceGateSatisfied}, #{productionSchedulingAllowed}, #{createdAt}, #{updatedAt}",
            ")"
    })
    void insertRun(NoonPullSmokeRunRecord run);

    @Insert({
            "INSERT INTO noon_pull_smoke_evidence (",
            "  id, run_id, sequence_no, data_domain, target_identity, date_from, date_to, row_or_item_count,",
            "  task_id, source_batch_id, file_digest_sha256, request_count, elapsed_millis, latest_fact_date,",
            "  status, quality_state, failure_classification, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{runId}, #{sequenceNo}, #{dataDomain}, #{targetIdentity}, #{dateFrom}, #{dateTo},",
            "  #{rowOrItemCount}, #{taskId}, #{sourceBatchId}, #{fileDigestSha256}, #{requestCount},",
            "  #{elapsedMillis}, #{latestFactDate}, #{status}, #{qualityState}, #{failureClassification},",
            "  #{createdAt}, #{updatedAt}",
            ")"
    })
    void insertEvidence(NoonPullSmokeEvidenceRecord evidence);

    @Select({
            "SELECT",
            "  id, target_environment, owner_user_id, project_code, project_name, store_code, site_code,",
            "  rollback_global_pause_strategy AS rollback_or_global_pause_strategy,",
            "  requested_domains AS requested_data_domains_text,",
            "  missing_requirements AS missing_requirements_text,",
            "  evidence_gate_satisfied, production_scheduling_allowed,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_smoke_run",
            "WHERE is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT #{limit}"
    })
    List<NoonPullSmokeRunRecord> selectRecentRuns(@Param("limit") int limit);

    @Select({
            "SELECT",
            "  id, run_id, sequence_no, data_domain, target_identity, date_from, date_to, row_or_item_count,",
            "  task_id, source_batch_id, file_digest_sha256, request_count, elapsed_millis, latest_fact_date,",
            "  status, quality_state, failure_classification,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_smoke_evidence",
            "WHERE run_id = #{runId}",
            "  AND is_deleted = b'0'",
            "ORDER BY sequence_no ASC, id ASC"
    })
    List<NoonPullSmokeEvidenceRecord> selectEvidenceByRunId(@Param("runId") Long runId);
}
