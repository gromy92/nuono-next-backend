package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonpull.NoonPullSmokeEvidenceRecord;
import com.nuono.next.noonpull.NoonPullSmokeRunRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

public interface NoonPullSmokeRunMapper {

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
